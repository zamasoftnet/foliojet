package net.zamasoft.foliojet.css.util;

import java.util.List;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.TypedAttrValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * <b>型付き {@code attr()}</b>(CSS Values 5)を解析します(2026-08-03新設)。
 *
 * <p>
 * 受け付ける形は<b>Chromeが出荷している構文</b>に合わせる(133以降)。
 *
 * <ul>
 * <li>{@code attr(width px)} —— 単位の短縮形。単位の付いていない属性値に
 * その単位を補う({@code width="200"} → {@code 200px})</li>
 * <li>{@code attr(width type(<length>))} —— 型指定。単位は属性値が持つ</li>
 * <li>{@code attr(bgcolor type(<color>))}・{@code attr(cols type(<integer>))}</li>
 * <li>いずれも {@code , フォールバック} を後置できる
 * ({@code attr(width px, auto)})</li>
 * </ul>
 *
 * <p>
 * <b>{@code url()}の中では使えない</b>(仕様どおり。情報の持ち出し経路になる)。
 * ここは長さ・色・数値の文脈からのみ呼ばれる。
 */
public final class AttrValueUtils {
	private AttrValueUtils() {
		// utility
	}

	/**
	 * トークンが型付き{@code attr()}なら未解決値を返します。そうでなければnull。
	 *
	 * @param defaultKind 型指定が無いときに仮定する型(長さ文脈ならLENGTH)
	 */
	public static Value toTypedAttr(UserAgent ua, CssToken token, TypedAttrValue.Kind defaultKind) {
		if (!(token instanceof CssToken.Func func) || !func.is("attr")) {
			return null;
		}
		final List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)) {
			return null;
		}
		final String name = nameToken.name();
		int i = 1;
		TypedAttrValue.Kind kind = null;
		Unit unit = Unit.PX;
		// 型または単位(カンマの手前まで)
		if (i < args.size() && args.get(i) != CssToken.Op.COMMA) {
			final CssToken typeToken = args.get(i);
			if (typeToken instanceof CssToken.Ident ident) {
				unit = Unit.of(ident.name());
				if (unit == null) {
					return null;
				}
				kind = TypedAttrValue.Kind.LENGTH;
			} else if (typeToken instanceof CssToken.Func typeFunc && typeFunc.is("type")) {
				kind = kindOf(typeFunc.args());
				if (kind == null) {
					return null;
				}
			} else {
				return null;
			}
			++i;
		}
		if (kind == null) {
			kind = defaultKind;
		}
		// フォールバック
		Value fallback = null;
		if (i < args.size() && args.get(i) == CssToken.Op.COMMA) {
			++i;
			if (i < args.size()) {
				fallback = parseFallback(ua, args.get(i), kind);
				if (fallback == null) {
					// フォールバックが解釈できない指定は宣言ごと無効にする
					// (黙って無視すると、意図しない既定値で組まれる)
					return null;
				}
			}
		}
		return TypedAttrValue.create(name, kind, unit, fallback);
	}

	private static TypedAttrValue.Kind kindOf(List<CssToken> args) {
		// **山括弧は字句として別に来ることがある**(2026-08-03に実測)。
		// `type(<length>)` が Ident 1個で来るとは限らないので、引数の中から
		// 型名の識別子を拾う
		for (final CssToken token : args) {
			if (!(token instanceof CssToken.Ident ident)) {
				continue;
			}
			final String s = ident.name().replace("<", "").replace(">", "").toLowerCase();
			switch (s) {
			case "length":
				return TypedAttrValue.Kind.LENGTH;
			case "color":
				return TypedAttrValue.Kind.COLOR;
			case "number":
				return TypedAttrValue.Kind.NUMBER;
			case "integer":
				return TypedAttrValue.Kind.INTEGER;
			default:
				break;
			}
		}
		return null;
	}

	private static Value parseFallback(UserAgent ua, CssToken token, TypedAttrValue.Kind kind) {
		switch (kind) {
		case COLOR:
			return ColorValueUtils.toPaint(ua, token);
		case NUMBER:
		case INTEGER:
			return ValueUtils.toReal(token);
		case LENGTH:
		default:
			if (token instanceof CssToken.Ident ident && ident.is("auto")) {
				return net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
			}
			if (token instanceof CssToken.Percent) {
				return ValueUtils.toPercentage(token);
			}
			return ValueUtils.toLength(ua, token);
		}
	}
}
