package net.zamasoft.foliojet.objects.barcode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import uk.org.okapibarcode.backend.AztecCode;
import uk.org.okapibarcode.backend.Code128;
import uk.org.okapibarcode.backend.Code2Of5;
import uk.org.okapibarcode.backend.Code3Of9;
import uk.org.okapibarcode.backend.Codabar;
import uk.org.okapibarcode.backend.DataMatrix;
import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.backend.JapanPost;
import uk.org.okapibarcode.backend.Pdf417;
import uk.org.okapibarcode.backend.Postnet;
import uk.org.okapibarcode.backend.QrCode;
import uk.org.okapibarcode.backend.RoyalMail4State;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.backend.Upc;
import uk.org.okapibarcode.backend.UspsOneCode;

/**
 * Generates OkapiBarcode images from Barcode4J-compatible XML descriptions.
 *
 * <p>
 * <b>Unit system</b> (corrected 2026-08-07): Okapi geometry uses integer "module" units;
 * {@code setModuleWidth(int)} and similar methods do not accept physical dimensions.
 * Previously, rounding {@code module-width: 0.21mm} before passing it yielded zero,
 * so <b>all one-dimensional bars had zero width and disappeared</b>
 * (digits appeared, but bars did not). Now uses module width as a "mm per unit" drawing scale
 * in {@link BarcodeImage}, and converts heights, quiet zones, and font sizes
 * to module units before passing them to Okapi.
 * </p>
 */
public class BarcodeInlineObject extends DefaultHandler implements InlineObject {
	private String message;
	private String type;
	private final Map<String, String> params = new HashMap<String, String>();
	private String currentParam;
	private StringBuilder text;
	private int depth = 0;

	private static final double MM_PER_PT = 25.4 / 72.0;

	/** Physical size of one module (mm) when module-width is omitted. */
	private static final double DEFAULT_MODULE_MM = 0.33;

	public Image getImage(UserAgent ua) throws IOException {
		try {
			Symbol symbol = this.createSymbol(this.type);
			// 1 unit = unitMm. Convert every length passed to Okapi to this unit
			Double mw = this.getMm("module-width", "moduleWidth");
			final double unitMm = mw != null && mw.doubleValue() > 0 ? mw.doubleValue() : DEFAULT_MODULE_MM;
			this.applyCommon(symbol, unitMm);
			symbol.setContent(normalizeContent(symbol, this.message == null ? "" : this.message));
			return new BarcodeImage(ua, symbol, this.message, unitMm);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if ("barcode".equals(lName)) {
			this.message = atts.getValue("message");
			this.depth = 0;
			return;
		}
		++this.depth;
		if (this.depth == 1) {
			this.type = lName;
			for (int i = 0; i < atts.getLength(); ++i) {
				this.params.put(atts.getLocalName(i), atts.getValue(i));
			}
		} else {
			// At depth>=2, collect leaf parameters as name → text.
			// This also makes nested items such as human-readable (placement/font-size, etc.)
			// individual parameters (previously text directly under human-readable was concatenated,
			// so placement comparisons always failed)
			this.currentParam = lName;
			this.text = new StringBuilder();
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.text != null) {
			this.text.append(ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if ("barcode".equals(lName)) {
			return;
		}
		if (this.depth >= 2 && this.currentParam != null) {
			final String value = this.text.toString().trim();
			if (value.length() > 0) {
				this.params.put(this.currentParam, value);
			}
			this.currentParam = null;
			this.text = null;
		}
		--this.depth;
	}

	/**
	 * Adapts message formats accepted by Barcode4J to Okapi's input requirements.
	 * Okapi validates strictly; passing them unchanged throws an exception and loses the entire
	 * barcode (found 2026-08-07 during verification of all barcode output examples).
	 */
	private static String normalizeContent(Symbol symbol, String content) {
		if (symbol instanceof Ean ean) {
			// Barcode4J accepts check digits (EAN-13 = 13 digits, including ISBN),
			// but Okapi accepts only the body and calculates the check digit itself
			final String digits = content.replaceAll("[^0-9]", "");
			if (ean.getMode() == Ean.Mode.EAN13 && digits.length() == 13) {
				return digits.substring(0, 12);
			}
			if (ean.getMode() == Ean.Mode.EAN8 && digits.length() == 8) {
				return digits.substring(0, 7);
			}
			return digits;
		}
		if (symbol instanceof Upc upc) {
			final String digits = content.replaceAll("[^0-9]", "");
			if (upc.getMode() == Upc.Mode.UPCA && digits.length() == 12) {
				return digits.substring(0, 11);
			}
			if (upc.getMode() == Upc.Mode.UPCE && digits.length() == 8) {
				return digits.substring(0, 7);
			}
			return digits;
		}
		if (symbol instanceof Codabar) {
			// Barcode4J automatically adds start/stop (A-D) to messages without them
			if (!content.matches("(?i)^[A-D].*[A-D]$")) {
				return "A" + content + "A";
			}
			return content;
		}
		if (symbol instanceof UspsOneCode) {
			// Okapi requires a dash separator in "20 tracking digits-routing".
			// Barcode4J accepts concatenated digit strings (20/25/29/31 digits)
			final String digits = content.replaceAll("[^0-9]", "");
			if (digits.length() > 20) {
				return digits.substring(0, 20) + "-" + digits.substring(20);
			}
			return digits;
		}
		if (symbol instanceof JapanPost) {
			// The manual (4900_barcode) explicitly says "characters in message other than digits, letters,
			// and hyphens are ignored." The old Barcode4J ignored these,
			// but OkapiBarcode's JapanPost rejects spaces and similar characters with
			// OkapiInputException, so remove them explicitly here to
			// preserve the documented behavior (2026-07-19).
			return content.replaceAll("[^0-9A-Za-z-]", "");
		}
		return content;
	}

	private Symbol createSymbol(String type) {
		String name = type == null ? "code128" : type.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		switch (name) {
		case "qrcode":
		case "qr":
			return new QrCode();
		case "datamatrix":
			return new DataMatrix();
		case "pdf417":
			return new Pdf417();
		case "aztec":
		case "azteccode":
			return new AztecCode();
		case "ean8":
			return new Ean(Ean.Mode.EAN8);
		case "ean13":
			return new Ean(Ean.Mode.EAN13);
		case "isbn":
			return new BookJanSymbol();
		case "ean": {
			// bc:ean without an explicit type selects 13/8 by digit count (compatible with the old Barcode4J)
			final String digits = (this.message == null ? "" : this.message).replaceAll("[^0-9]", "");
			return new Ean(digits.length() <= 8 ? Ean.Mode.EAN8 : Ean.Mode.EAN13);
		}
		case "upca":
			return new Upc(Upc.Mode.UPCA);
		case "upce":
			return new Upc(Upc.Mode.UPCE);
		case "japanpost":
		case "jp4scc":
			return new JapanPost();
		case "postnet":
			return new Postnet(Postnet.Mode.POSTNET);
		case "planet":
			return new Postnet(Postnet.Mode.PLANET);
		case "royalmailcbc":
		case "royalmail":
		case "rm4scc":
			return new RoyalMail4State();
		case "usps4cbc":
		case "usps4cb":
		case "uspsonecode":
		case "uspsintelligentmail":
			return new UspsOneCode();
		case "code39":
		case "code3of9":
			return new Code3Of9();
		case "codabar":
			return new Codabar();
		case "interleaved2of5":
		case "intl2of5":
		case "int2of5":
		case "itf":
			// Barcode4J's intl2of5 is interleaved (2 digits/symbol)
			return new Code2Of5(Code2Of5.ToFMode.INTERLEAVED);
		case "ean128":
		case "gs1128":
			// GS1 AI syntax (FNC1) is an unsupported approximation: render as plain Code 128.
			// This may be insufficient for Barcode4J matching uses (reader compatibility),
			// but is better than missing bars (record: 4900_barcode)
			return new Code128();
		case "code128":
		default:
			return new Code128();
		}
	}

	/** Whether this is a two-dimensional symbol, for which quiet zones can be equal on all four sides. */
	private static boolean isTwoDimensional(final Symbol symbol) {
		return symbol instanceof QrCode || symbol instanceof DataMatrix || symbol instanceof AztecCode
				|| symbol instanceof Pdf417;
	}

	private void applyCommon(Symbol symbol, double unitMm) throws Exception {
		// Heights: physical lengths → module units
		setUnits(symbol, "setBarHeight", this.getMm("height", "bar-height", "barHeight"), unitMm);
		// Quiet zones: retain mw units such as "10mw"; convert physical lengths
		final Length horizontalQuietZone = this.getMmOrModules("quiet-zone", "quiet-zone-horizontal", "quietZone");
		final Length verticalQuietZone = this.getMmOrModules("quiet-zone-vertical", "vertical-quiet-zone");
		setUnits(symbol, "setQuietZoneHorizontal", horizontalQuietZone, unitMm);
		// Two-dimensional symbols have equal quiet zones on all four sides (ISO/IEC 18004 requires four cells
		// on each side for QR). Separate horizontal/vertical settings originate in one-dimensional barcodes,
		// so use the horizontal value when no vertical value is specified.
		// **Leaving them separate expands only symbol.getWidth() by the quiet zone,
		// making the natural dimensions rectangular.** A caller providing a square frame then
		// stretches it vertically to that frame, making the cells nonsquare
		setUnits(symbol, "setQuietZoneVertical",
				verticalQuietZone == null && isTwoDimensional(symbol) ? horizontalQuietZone : verticalQuietZone,
				unitMm);
		// Font size: default pt → module units
		final Double fontPt = this.getPt("font-size", "fontSize");
		if (fontPt != null) {
			setInt(symbol, "setFontSize", Math.max(1, (int) Math.round(fontPt.doubleValue() * MM_PER_PT / unitMm)));
		}
		setString(symbol, "setFontName", get("font-name", "fontName"));
		String placement = get("placement", "human-readable", "human-readable-placement", "humanReadablePlacement",
				"msg-position");
		if (placement != null) {
			String value = placement.toLowerCase(Locale.ROOT);
			if ("none".equals(value) || "hidden".equals(value)) {
				setEnum(symbol, "setHumanReadableLocation", HumanReadableLocation.NONE);
			} else if ("top".equals(value)) {
				setEnum(symbol, "setHumanReadableLocation", HumanReadableLocation.TOP);
			} else if ("bottom".equals(value)) {
				setEnum(symbol, "setHumanReadableLocation", HumanReadableLocation.BOTTOM);
			}
		}
	}

	private String get(String... names) {
		for (String name : names) {
			String value = this.params.get(name);
			if (value != null && value.length() > 0) {
				return value;
			}
		}
		return null;
	}

	private static final Pattern LENGTH = Pattern.compile("([-+]?[0-9.]+)\\s*([a-zA-Z]*)");

	/** Returns a physical length in mm (null for mw units or unparseable values). */
	private Double getMm(String... names) {
		final String value = get(names);
		if (value == null) {
			return null;
		}
		final Matcher m = LENGTH.matcher(value.trim());
		if (!m.matches()) {
			return null;
		}
		final double v;
		try {
			v = Double.parseDouble(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
		switch (m.group(2).toLowerCase(Locale.ROOT)) {
		case "":
		case "mm":
			return Double.valueOf(v);
		case "cm":
			return Double.valueOf(v * 10);
		case "in":
			return Double.valueOf(v * 25.4);
		case "pt":
			return Double.valueOf(v * MM_PER_PT);
		case "px":
			return Double.valueOf(v * 25.4 / 96);
		default:
			return null;
		}
	}

	/** Returns a length in pt (unitless values are treated as pt). */
	private Double getPt(String... names) {
		final Double mm = this.getMm(names);
		return mm == null ? null : Double.valueOf(mm.doubleValue() / MM_PER_PT);
	}

	/**
	 * Returns a length (physical or in mw units). Stores mw units in {@link Length#modules}
	 * and physical lengths in {@link Length#mm}.
	 */
	private Length getMmOrModules(String... names) {
		final String value = get(names);
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		if (trimmed.toLowerCase(Locale.ROOT).endsWith("mw")) {
			try {
				return Length.modules(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2).trim()));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		final Double mm = this.getMm(names);
		return mm == null ? null : Length.mm(mm.doubleValue());
	}

	private record Length(double value, boolean isModules) {
		static Length mm(double v) {
			return new Length(v, false);
		}

		static Length modules(double v) {
			return new Length(v, true);
		}
	}

	private static void setUnits(Symbol symbol, String methodName, Double mm, double unitMm) throws Exception {
		if (mm == null) {
			return;
		}
		setInt(symbol, methodName, Math.max(1, (int) Math.round(mm.doubleValue() / unitMm)));
	}

	private static void setUnits(Symbol symbol, String methodName, Length length, double unitMm) throws Exception {
		if (length == null) {
			return;
		}
		final double units = length.isModules() ? length.value() : length.value() / unitMm;
		setInt(symbol, methodName, Math.max(0, (int) Math.round(units)));
	}

	private static void setInt(Symbol symbol, String methodName, int value) throws Exception {
		try {
			Method method = symbol.getClass().getMethod(methodName, int.class);
			method.invoke(symbol, Integer.valueOf(value));
		} catch (NoSuchMethodException e) {
			// optional
		}
	}

	private static void setString(Symbol symbol, String methodName, String value) throws Exception {
		if (value == null) {
			return;
		}
		try {
			Method method = symbol.getClass().getMethod(methodName, String.class);
			method.invoke(symbol, value);
		} catch (NoSuchMethodException e) {
			// optional
		}
	}

	private static void setEnum(Symbol symbol, String methodName, Enum<?> value) throws Exception {
		try {
			Method method = symbol.getClass().getMethod(methodName, value.getDeclaringClass());
			method.invoke(symbol, value);
		} catch (NoSuchMethodException e) {
			// optional
		}
	}
}
