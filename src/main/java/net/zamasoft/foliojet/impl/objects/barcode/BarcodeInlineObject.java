package net.zamasoft.foliojet.impl.objects.barcode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
import uk.org.okapibarcode.backend.QrCode;
import uk.org.okapibarcode.backend.Symbol;

/**
 * Barcode4J互換のXML記述からOkapiBarcodeの画像を生成します。
 */
public class BarcodeInlineObject extends DefaultHandler implements InlineObject {
	private String message;
	private String type;
	private final Map<String, String> params = new HashMap<String, String>();
	private String currentParam;
	private StringBuilder text;
	private int depth = 0;

	public Image getImage(UserAgent ua) throws IOException {
		try {
			Symbol symbol = this.createSymbol();
			symbol.setContent(this.message == null ? "" : this.message);
			return new BarcodeImage(ua, symbol, this.message);
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
		} else if (this.depth == 2) {
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
		if (this.depth == 2 && this.currentParam != null) {
			this.params.put(this.currentParam, this.text.toString().trim());
			this.currentParam = null;
			this.text = null;
		}
		--this.depth;
	}

	private Symbol createSymbol() throws Exception {
		Symbol symbol = this.createSymbol(this.type);
		this.applyCommon(symbol);
		return symbol;
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
		case "ean13":
		case "ean8":
		case "ean":
		case "isbn":
			return new Ean();
		case "japanpost":
		case "jp4scc":
			return new JapanPost();
		case "code39":
		case "code3of9":
			return new Code3Of9();
		case "codabar":
			return new Codabar();
		case "interleaved2of5":
		case "itf":
			return new Code2Of5();
		case "code128":
		default:
			return new Code128();
		}
	}

	private void applyCommon(Symbol symbol) throws Exception {
		setDouble(symbol, "setModuleWidth", getDouble("module-width", "moduleWidth"));
		setDouble(symbol, "setBarHeight", getDouble("height", "bar-height", "barHeight"));
		setDouble(symbol, "setQuietZoneHorizontal", getDouble("quiet-zone", "quiet-zone-horizontal", "quietZone"));
		setDouble(symbol, "setQuietZoneVertical", getDouble("quiet-zone-vertical"));
		setDouble(symbol, "setFontSize", getDouble("font-size", "fontSize"));
		setString(symbol, "setFontName", get("font-name", "fontName"));
		String placement = get("human-readable", "human-readable-placement", "humanReadablePlacement", "msg-position");
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

	private Double getDouble(String... names) {
		String value = get(names);
		if (value == null) {
			return null;
		}
		try {
			return Double.valueOf(value.replaceAll("[a-zA-Z]+$", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void setDouble(Symbol symbol, String methodName, Double value) throws Exception {
		if (value == null) {
			return;
		}
		try {
			Method method = symbol.getClass().getMethod(methodName, double.class);
			method.invoke(symbol, value.doubleValue());
		} catch (NoSuchMethodException e) {
			try {
				Method method = symbol.getClass().getMethod(methodName, int.class);
				method.invoke(symbol, Integer.valueOf((int) Math.round(value.doubleValue())));
			} catch (NoSuchMethodException e2) {
				// optional
			}
		}
	}

	private void setString(Symbol symbol, String methodName, String value) throws Exception {
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

	private void setEnum(Symbol symbol, String methodName, Enum<?> value) throws Exception {
		try {
			Method method = symbol.getClass().getMethod(methodName, value.getDeclaringClass());
			method.invoke(symbol, value);
		} catch (NoSuchMethodException e) {
			// optional
		}
	}
}
