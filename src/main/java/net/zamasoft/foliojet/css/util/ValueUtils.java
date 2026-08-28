package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.TypedAttrValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * @author MIYABE Tatsuhiko
 */
public final class ValueUtils {
	private ValueUtils() {
		// unused
	}

	/**
	 * 識別子キーワード(大文字小文字無視)であれば true を返します。
	 */
	public static boolean isKeyword(CssToken token, String keyword) {
		return token instanceof CssToken.Ident ident && ident.is(keyword);
	}

	/**
	 * auto であればtrueを返します。
	 */
	public static boolean isAuto(CssToken token) {
		return isKeyword(token, "auto");
	}

	/**
	 * none であればtrueを返します。
	 */
	public static boolean isNone(CssToken token) {
		return isKeyword(token, "none");
	}

	/**
	 * normal であればtrueを返します。
	 */
	public static boolean isNormal(CssToken token) {
		return isKeyword(token, "normal");
	}

	/**
	 * &lt;length&gt; を値に変換します。
	 */
	public static LengthValue toLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case EM:
				return RelativeLengthValue.em(dim.value());
			case EX:
				return RelativeLengthValue.ex(dim.value());
			case REM:
				return RelativeLengthValue.rem(dim.value());
			case CH:
				return RelativeLengthValue.ch(dim.value());
			case LH:
				return RelativeLengthValue.lh(dim.value());
			case CQW:
			case CQI:
				// コンテナクエリ単位(段6、2026-08-15)。RelativeLengthValueと
				// 同じく解析時には解決せず、emExToAbsoluteLengthで使用値
				// 計算時に解決する
				return net.zamasoft.foliojet.css.value.ContainerRelativeLengthValue.of(dim.unit(), dim.value());
			default:
				break;
			}
		}
		return toAbsoluteLength(ua, token);
	}

	/**
	 * 文字列表現を長さに変換します。
	 */
	public static LengthValue toLength(UserAgent ua, boolean legacy, String s) {
		try {
			s = s.toLowerCase().trim();
			if (s.endsWith("em")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.em(len);
			} else if (s.endsWith("ex")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.ex(len);
			} else if (s.endsWith("ch")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.ch(len);
			} else if (s.endsWith("rem")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 3));
				return RelativeLengthValue.rem(len);
			} else if (s.endsWith("lh")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return RelativeLengthValue.lh(len);
			} else {
				return toAbsoluteLength(ua, legacy, s);
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 文字列表現を長さに変換します。
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, boolean legacy, String s) {
		if (s == null) {
			return null;
		}
		s = s.toLowerCase().trim();
		try {
			if (s.endsWith("mm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.MM);
			} else if (s.endsWith("cm")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.CM);
			} else if (s.endsWith("pt")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PT);
			} else if (s.endsWith("px")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PX);
			} else if (s.endsWith("pc")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.PC);
			} else if (s.endsWith("in")) {
				double len = NumberUtils.parseDouble(s.substring(0, s.length() - 2));
				return AbsoluteLengthValue.create(ua, len, Unit.IN);
			} else {
				double len = NumberUtils.parseDouble(s);
				if (len == 0) {
					return AbsoluteLengthValue.ZERO;
				}
				if (legacy) {
					return AbsoluteLengthValue.create(ua, len, Unit.PX);
				}
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * valueがEM_LENGTHかEX_LENGTHならstyleのフォント情報を基準に絶対長さに変換します。
	 */
	public static Value emExToAbsoluteLength(Value value, CSSStyle style) {
		if (value instanceof RelativeLengthValue relative) {
			return relative.toAbsoluteLength(style);
		}
		// コンテナクエリ単位(段6、2026-08-15)。RelativeLengthValueと同じ経路
		if (value instanceof net.zamasoft.foliojet.css.value.ContainerRelativeLengthValue containerRelative) {
			return containerRelative.toAbsoluteLength(style);
		}
		// **calc()の中のフォント相対単位もここで解く**(2026-08-03)。
		// 解析時には要素のfont-sizeが無いので、絶対成分・割合成分と分けたまま
		// ここまで持ち回っている({@link CalcFontRelativeValue})
		if (value instanceof CalcFontRelativeValue calc) {
			return calc.resolve(style);
		}
		// 型付き attr()(2026-08-03)。属性を読んで値にする。解決できず
		// フォールバックも無い場合は null を返し、呼び出し側(DeferredProperty)が
		// 「使用値計算時に無効」として unset 相当に落とす
		if (value instanceof TypedAttrValue attr) {
			final Value resolved = attr.resolve(style);
			return resolved == null ? KeywordValue.NONE : emExToAbsoluteLength(resolved, style);
		}
		// fit-content(<length-percentage>)の引数(2026-08-29)。calc()の
		// フォント相対成分と同じく、要素のfont-sizeが分かるここで解く
		if (value instanceof net.zamasoft.foliojet.css.value.FitContentValue fit) {
			final Value argument = emExToAbsoluteLength(fit.argument(), style);
			return argument == fit.argument() ? fit
					: new net.zamasoft.foliojet.css.value.FitContentValue(argument);
		}
		return value;
	}

	/**
	 * フォント相対長さ以外の &lt;length&gt; を値に変換します。
	 */
	public static AbsoluteLengthValue toAbsoluteLength(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case IN:
			case CM:
			case MM:
			case PT:
			case PC:
			case PX:
				return AbsoluteLengthValue.create(ua, dim.value(), dim.unit());
			case VW:
			case VH:
			case VMIN:
			case VMAX:
				// ビューポート単位(2026-08-29)。ページ媒体では版面寸法が
				// 解析時に確定している(UAプロパティ由来)ので、rem等と
				// 違って絶対長さへ即時に解決できる——calc()/min()/max()の
				// 葉(CalcValueUtils.evaluateLeaf)もこの経路を通るため、
				// 数式の中でも同じ値になる
				return ViewportUnits.resolve(ua, dim.unit(), dim.value());
			default:
				return null;
			}
		}
		if (token instanceof CssToken.Num num && num.value() == 0) {
			return AbsoluteLengthValue.ZERO;
		}
		return null;
	}

	/**
	 * &lt;percentage&gt; を値に変換します。
	 */
	public static PercentageValue toPercentage(CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return PercentageValue.create(percent.value());
		}
		return null;
	}

	/**
	 * &lt;number&gt; を値に変換します。
	 */
	public static RealValue toReal(CssToken token) {
		if (token instanceof CssToken.Num num) {
			return RealValue.create(num.value());
		}
		return null;
	}

	/**
	 * &lt;uri&gt; を値に変換します。
	 */
	public static URIValue toURI(UserAgent ua, URI baseURI, CssToken token) throws URISyntaxException {
		if (token instanceof CssToken.Uri uri) {
			return createURIValue(ua.getDocumentContext().getEncoding(), baseURI, uri.uri());
		}
		return null;
	}

	/** {@code url()}または{@code image-set()}のトークンか(2026-08-29)。 */
	public static boolean isImage(CssToken token) {
		return token instanceof CssToken.Uri || token instanceof CssToken.Func func
				&& (func.is("image-set") || func.is("-webkit-image-set"));
	}

	/** 警告メッセージ用にトークンのURI文字列(image-set()は関数全体)を返します。 */
	public static String uriText(CssToken token) {
		return token instanceof CssToken.Uri uri ? uri.uri() : String.valueOf(token);
	}

	/**
	 * &lt;image&gt;({@code url()}または{@code image-set()})を画像URIに変換します
	 * (css-images-4 §4.1、2026-08-29)。
	 *
	 * <p>
	 * {@code image-set(<image> <resolution>? type(<string>)?, ...)}
	 * (接頭辞{@code -webkit-image-set(url() 1x, url() 2x)}も同じ)からは
	 * 出力解像度({@code UAProps.OUTPUT_RESOLUTION}=
	 * {@link UserAgent#getPixelsPerInch()}。1x=96dpi、2x=192dpi)に最も近い
	 * 候補を選ぶ: 出力解像度を超えない最大の解像度、無ければ超える中で最小。
	 * 解像度省略は1x。{@code image()}関数・グラデーション・未対応MIMEの
	 * {@code type()}付き候補は飛ばす(候補が一つも無ければnull=宣言無効)。
	 * </p>
	 */
	public static URIValue toImage(UserAgent ua, URI baseURI, CssToken token) throws URISyntaxException {
		if (token instanceof CssToken.Uri) {
			return toURI(ua, baseURI, token);
		}
		if (!isImage(token)) {
			return null;
		}
		final double target = ua.getPixelsPerInch();
		String bestBelow = null, bestAbove = null;
		double belowDpi = -1, aboveDpi = Double.MAX_VALUE;
		for (final TokenStream candidate : ((CssToken.Func) token).argStream().splitComma()) {
			final CssToken image = candidate.next();
			final String href;
			if (image instanceof CssToken.Uri uri) {
				href = uri.uri();
			} else if (image instanceof CssToken.Str str) {
				href = str.value();
			} else {
				continue; // image()・グラデーション等
			}
			double dpi = 96;
			boolean supported = true;
			while (candidate.hasNext()) {
				final CssToken option = candidate.next();
				if (option instanceof CssToken.Dim dim) {
					final String unit = dim.unitText().toLowerCase(java.util.Locale.ROOT);
					switch (unit) {
					case "x", "dppx" -> dpi = dim.value() * 96;
					case "dpi" -> dpi = dim.value();
					case "dpcm" -> dpi = dim.value() * 2.54;
					default -> supported = false;
					}
				} else if (option instanceof CssToken.Func func && func.is("type")) {
					final String mime = func.argStream().string();
					supported &= mime != null && isSupportedImageType(mime);
				} else {
					supported = false;
				}
			}
			if (!supported || !(dpi > 0)) {
				continue;
			}
			if (dpi <= target + 1e-6) {
				if (dpi > belowDpi) {
					belowDpi = dpi;
					bestBelow = href;
				}
			} else if (dpi < aboveDpi) {
				aboveDpi = dpi;
				bestAbove = href;
			}
		}
		final String chosen = bestBelow != null ? bestBelow : bestAbove;
		if (chosen == null) {
			return null;
		}
		return createURIValue(ua.getDocumentContext().getEncoding(), baseURI, chosen);
	}

	/** {@code type()}のMIMEが描画できる画像形式か(不明な形式の候補は飛ばす)。 */
	private static boolean isSupportedImageType(final String mime) {
		switch (mime.trim().toLowerCase(java.util.Locale.ROOT)) {
		case "image/png", "image/jpeg", "image/jpg", "image/gif", "image/bmp", "image/svg+xml", "image/webp",
				"image/tiff":
			return true;
		default:
			return false;
		}
	}

	/**
	 * 参照文字列を基底URIで解決してURI値にします。
	 */
	public static URIValue createURIValue(String encoding, URI baseURI, String href) throws URISyntaxException {
		URI uri;
		try {
			uri = URIHelper.resolve(encoding, baseURI, href);
		} catch (URISyntaxException e) {
			// **URIHelper.resolve()の不正文字サニタイズはbaseURIがhttp/https
			// の時にしか効かない**(2026-08-06、実物のyahoo.co.jpで発覚)。
			// アイコン用SVGを`url("data:image/svg+xml;charset=utf-8,
			// %3Csvg width='80' height='80'...")`のようにリテラル空白混じり
			// で埋め込むのはブラウザ向けCSSでは普通に見る書き方——ブラウザは
			// 常に許容するが、こちらはbaseURIがfile://(ローカルHTML変換や
			// インライン&lt;style&gt;)だとURI構文エラーで例外になり、
			// 背景画像がまるごと消える。base URIのスキームに関係なくブラウザ
			// と同じ寛容さにするため、例外時だけ最小限の不正文字を
			// %エンコードして一度だけ再試行する
			String sanitized = sanitizeForURI(href);
			if (sanitized.equals(href)) {
				throw e;
			}
			uri = URIHelper.resolve(encoding, baseURI, sanitized);
		}
		return URIValue.create(uri);
	}

	/**
	 * URIとして不正な(未エスケープの)文字を%エンコードします。
	 *
	 * <p>
	 * {@code '}のようなRFC3986のsub-delimsは正当な文字なので触らない
	 * ——実際に例外を起こすのは空白等、常にエスケープが必要な文字。
	 * 既存の{@code %XX}エスケープは対象にせず素通りさせる(二重エンコード
	 * を避ける)。
	 * </p>
	 */
	private static String sanitizeForURI(String href) {
		StringBuilder sb = null;
		for (int i = 0; i < href.length(); ++i) {
			char c = href.charAt(i);
			boolean illegal;
			switch (c) {
			case ' ':
			case '"':
			case '<':
			case '>':
			case '`':
			case '{':
			case '}':
			case '|':
			case '\\':
			case '^':
				illegal = true;
				break;
			default:
				illegal = c <= 0x20 || c == 0x7f;
			}
			if (illegal) {
				if (sb == null) {
					sb = new StringBuilder(href.length() + 16);
					sb.append(href, 0, i);
				}
				sb.append('%');
				sb.append(Character.forDigit((c >> 4) & 0xf, 16));
				sb.append(Character.forDigit(c & 0xf, 16));
			} else if (sb != null) {
				sb.append(c);
			}
		}
		return sb == null ? href : sb.toString();
	}
}
