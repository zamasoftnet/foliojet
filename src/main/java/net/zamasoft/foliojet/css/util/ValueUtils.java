package net.zamasoft.foliojet.css.util;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
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
