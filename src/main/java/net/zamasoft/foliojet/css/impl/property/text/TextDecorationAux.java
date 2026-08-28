package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 文字装飾の付帯的な個別指定——{@code text-decoration-style}・
 * {@code text-decoration-thickness}・{@code text-underline-offset}
 * (css-text-decoration-3/4)です(2026-08-29新設)。
 *
 * <p>
 * <b>構文として受理して値を保持するだけで、描画には反映しません</b>
 * (装飾線は常に実線・既定の太さ・既定の位置)。受理する理由は、
 * {@code text-decoration: underline dotted}のような短縮形が
 * 構成要素の未対応で宣言ごと捨てられ、<b>下線そのものが消える</b>
 * のを防ぐためです。個別指定は実サイト50件中10〜16件で使われて
 * いました。太さ・位置は{@code auto}/{@code from-font}/長さ/割合を、
 * 線種は{@code solid|double|dotted|dashed|wavy}を受けます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextDecorationAux extends AbstractPrimitivePropertyInfo {
	public static final TextDecorationAux STYLE = new TextDecorationAux("text-decoration-style", true);

	public static final TextDecorationAux THICKNESS = new TextDecorationAux("text-decoration-thickness", false);

	public static final TextDecorationAux UNDERLINE_OFFSET = new TextDecorationAux("text-underline-offset", false);

	/** {@code text-decoration-style}の値。 */
	public static final Set<String> STYLES = Set.of("solid", "double", "dotted", "dashed", "wavy");

	/** {@link #STYLE}の既定{@code solid}を表す値。 */
	public static final Value SOLID = new StyleKeyword("solid");

	private record StyleKeyword(String name) implements Value {
		public String toString() {
			return this.name;
		}
	}

	private final boolean isStyle;

	private TextDecorationAux(final String name, final boolean isStyle) {
		super(name);
		this.isStyle = isStyle;
	}

	public Value getDefault(CSSStyle style) {
		return this.isStyle ? SOLID : KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	/**
	 * 1トークンをこの個別指定の値として読みます(短縮形からも使う)。
	 * 該当しなければ null。
	 */
	public Value toValue(final UserAgent ua, final CssToken token) {
		if (this.isStyle) {
			if (token instanceof CssToken.Ident ident && STYLES.contains(ident.lower())) {
				return ident.is("solid") ? SOLID : new StyleKeyword(ident.lower());
			}
			return null;
		}
		if (ValueUtils.isAuto(token)) {
			return KeywordValue.AUTO;
		}
		if (this == THICKNESS && ValueUtils.isKeyword(token, "from-font")) {
			return KeywordValue.NORMAL;
		}
		if (token instanceof CssToken.Ident) {
			return null;
		}
		if (this == UNDERLINE_OFFSET) {
			// 負の値も許される(線を上へ寄せる)
			Value value = net.zamasoft.foliojet.css.util.CalcValueUtils.toCalc(ua, token);
			if (value == null) {
				value = ValueUtils.toPercentage(token);
			}
			if (value == null) {
				value = ValueUtils.toLength(ua, token);
			}
			return value;
		}
		return BoxValueUtils.toPositiveLength(ua, token);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final Value value = this.toValue(ua, tokens.next());
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
