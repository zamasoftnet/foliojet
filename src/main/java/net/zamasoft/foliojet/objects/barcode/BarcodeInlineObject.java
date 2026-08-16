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
 * Barcode4J互換のXML記述からOkapiBarcodeの画像を生成します。
 *
 * <p>
 * <b>単位系</b>(2026-08-07に是正): Okapiの幾何は整数の「モジュール」
 * 単位で、{@code setModuleWidth(int)}等は物理寸法を受けない。従来は
 * {@code module-width: 0.21mm} を丸めて渡していたため0になり、
 * <b>1次元系のバーが全て幅0で消えていた</b>(数字だけ出てバーが出ない)。
 * 現在はモジュール幅を「1単位=何mmか」という倍率として
 * {@link BarcodeImage}の描画スケールに使い、高さ・静止帯・文字サイズを
 * モジュール単位へ換算してOkapiへ渡す。
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

	/** module-width省略時の1モジュールの物理寸法(mm)。 */
	private static final double DEFAULT_MODULE_MM = 0.33;

	public Image getImage(UserAgent ua) throws IOException {
		try {
			Symbol symbol = this.createSymbol(this.type);
			// 1単位=unitMm。Okapiへ渡す長さは全てこの単位へ換算する
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
			// depth>=2は葉のパラメータとして名前→テキストで拾う。
			// human-readableのような入れ子(placement/font-size等)も
			// これで個別のパラメータになる(従来はhuman-readable直下の
			// テキストが連結され、placementの比較が常に外れていた)
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
	 * Barcode4Jが受理するmessage表記をOkapiの入力仕様へ合わせます。
	 * Okapiは検証が厳格で、そのまま渡すと例外でバーコードごと消える
	 * (2026-08-07、バーコード出力例の全数検証で判明)。
	 */
	private static String normalizeContent(Symbol symbol, String content) {
		if (symbol instanceof Ean ean) {
			// Barcode4Jはチェックデジット込み(EAN-13=13桁、ISBN含む)を
			// 受けるが、Okapiは本体のみを受けて自分で計算する
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
			// Barcode4Jはstart/stop(A-D)無しのmessageに自動付与する
			if (!content.matches("(?i)^[A-D].*[A-D]$")) {
				return "A" + content + "A";
			}
			return content;
		}
		if (symbol instanceof UspsOneCode) {
			// Okapiは「追跡20桁-ルーティング」のダッシュ区切りを要求する。
			// Barcode4Jは連結数字列(20/25/29/31桁)を受ける
			final String digits = content.replaceAll("[^0-9]", "");
			if (digits.length() > 20) {
				return digits.substring(0, 20) + "-" + digits.substring(20);
			}
			return digits;
		}
		if (symbol instanceof JapanPost) {
			// マニュアル(4900_barcode)に「message内に含まれる数字、アルファベット、
			// ハイフン以外の文字は無視されます」と明記されている。旧Barcode4Jは
			// これらを無視していたが、OkapiBarcodeのJapanPostはスペース等を
			// OkapiInputExceptionとして拒否するため、ここで明示的に除去して
			// ドキュメント記載の挙動を維持する(2026-07-19)。
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
			// 種類を明示しないbc:eanは桁数で13/8を選ぶ(旧Barcode4J互換)
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
			// Barcode4Jのintl2of5はインターリーブド(2桁/シンボル)
			return new Code2Of5(Code2Of5.ToFMode.INTERLEAVED);
		case "ean128":
		case "gs1128":
			// GS1のAI構文(FNC1)は未対応の近似——素のCode 128として描く。
			// Barcode4Jの照合用途(読み取り互換)には不足しうるが、
			// バーが出ないよりはよい(記録: 4900_barcode)
			return new Code128();
		case "code128":
		default:
			return new Code128();
		}
	}

	private void applyCommon(Symbol symbol, double unitMm) throws Exception {
		// 高さ系: 物理長→モジュール単位
		setUnits(symbol, "setBarHeight", this.getMm("height", "bar-height", "barHeight"), unitMm);
		// 静止帯: "10mw"のmw単位はそのまま、物理長は換算
		setUnits(symbol, "setQuietZoneHorizontal",
				this.getMmOrModules("quiet-zone", "quiet-zone-horizontal", "quietZone"), unitMm);
		setUnits(symbol, "setQuietZoneVertical", this.getMmOrModules("quiet-zone-vertical", "vertical-quiet-zone"),
				unitMm);
		// 文字サイズ: pt既定→モジュール単位
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

	/** 物理長をmmで返します(mw単位・解釈不能はnull)。 */
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

	/** ptで表した長さを返します(単位なしはpt扱い)。 */
	private Double getPt(String... names) {
		final Double mm = this.getMm(names);
		return mm == null ? null : Double.valueOf(mm.doubleValue() / MM_PER_PT);
	}

	/**
	 * 長さ(物理またはmw単位)を返します。mw単位は
	 * {@link Length#modules}、物理長は{@link Length#mm}に入る。
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
