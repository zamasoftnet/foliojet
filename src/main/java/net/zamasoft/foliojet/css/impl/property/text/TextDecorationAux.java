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
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 文字装飾の付帯的な個別指定——{@code text-decoration-style}・
 * {@code text-decoration-thickness}・{@code text-underline-offset}
 * (css-text-decoration-3/4)です(2026-08-29新設)。
 *
 * <p>
 * 同日中に描画へも配線した: 線種は{@link #getStyle}、太さは
 * {@link #getThickness}(絶対長、autoなら0)、下線のずらしは
 * {@link #getUnderlineOffset}(絶対長、autoならNaN)で
 * {@code AbstractTextParams}へ運び、{@code AbstractTextBox}の装飾線描画が
 * 読む。割合はいずれも1em(その要素のフォントサイズ)に対して解決する
 * (css-text-decoration-4)。{@code from-font}はpdfg2dの{@code FontSource}
 * が下線の太さ・位置を公開していないため{@code auto}と同じ。太さ・位置は
 * {@code auto}/{@code from-font}/長さ/割合を、線種は
 * {@code solid|double|dotted|dashed|wavy}を受けます。
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
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	/** {@code text-decoration-style}の線種({@code AbstractTextParams.DECORATION_STYLE_*})。 */
	public static byte getStyle(final CSSStyle style) {
		final Value value = style.get(STYLE);
		if (value instanceof StyleKeyword keyword) {
			switch (keyword.name()) {
			case "double":
				return AbstractTextParams.DECORATION_STYLE_DOUBLE;
			case "dotted":
				return AbstractTextParams.DECORATION_STYLE_DOTTED;
			case "dashed":
				return AbstractTextParams.DECORATION_STYLE_DASHED;
			case "wavy":
				return AbstractTextParams.DECORATION_STYLE_WAVY;
			default:
				break;
			}
		}
		return AbstractTextParams.DECORATION_STYLE_SOLID;
	}

	/** {@code text-decoration-thickness}の絶対長。{@code auto}/{@code from-font}なら0。 */
	public static double getThickness(final CSSStyle style) {
		return resolveLength(style.get(THICKNESS), style, 0);
	}

	/** {@code text-underline-offset}の絶対長。{@code auto}ならNaN。 */
	public static double getUnderlineOffset(final CSSStyle style) {
		return resolveLength(style.get(UNDERLINE_OFFSET), style, Double.NaN);
	}

	private static double resolveLength(final Value value, final CSSStyle style, final double fallback) {
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		if (value instanceof PercentageValue percentage) {
			// 割合は1em(css-text-decoration-4 §2.5/§2.7)
			return percentage.getRatio() * style.getFontStyle().getSize();
		}
		return fallback;
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
