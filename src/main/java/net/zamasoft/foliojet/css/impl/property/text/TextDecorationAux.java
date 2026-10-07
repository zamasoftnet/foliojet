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
 * Additional text-decoration longhands: {@code text-decoration-style},
 * {@code text-decoration-thickness}, and {@code text-underline-offset}
 * (css-text-decoration-3/4, added 2026-08-29).
 *
 * <p>
 * Also connected to rendering the same day: line style via {@link #getStyle},
 * thickness via {@link #getThickness} (absolute length, 0 for auto),
 * and underline offset via {@link #getUnderlineOffset} (absolute length, NaN for auto)
 * are carried to {@code AbstractTextParams} and read by decoration rendering in {@code AbstractTextBox}.
 * All percentages resolve against 1em (the element's font size) (css-text-decoration-4).
 * {@code from-font} is equivalent to {@code auto} because pdfg2d's {@code FontSource}
 * does not expose underline thickness/position. Thickness and position accept
 * {@code auto}/{@code from-font}/length/percentage; line style accepts
 * {@code solid|double|dotted|dashed|wavy}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextDecorationAux extends AbstractPrimitivePropertyInfo {
	public static final TextDecorationAux STYLE = new TextDecorationAux("text-decoration-style", true);

	public static final TextDecorationAux THICKNESS = new TextDecorationAux("text-decoration-thickness", false);

	public static final TextDecorationAux UNDERLINE_OFFSET = new TextDecorationAux("text-underline-offset", false);

	/** Value of {@code text-decoration-style}. */
	public static final Set<String> STYLES = Set.of("solid", "double", "dotted", "dashed", "wavy");

	/** Value representing the {@code solid} default of {@link #STYLE}. */
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

	/** Line style of {@code text-decoration-style} ({@code AbstractTextParams.DECORATION_STYLE_*}). */
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

	/** Absolute length of {@code text-decoration-thickness}. 0 for {@code auto}/{@code from-font}. */
	public static double getThickness(final CSSStyle style) {
		return resolveLength(style.get(THICKNESS), style, 0);
	}

	/** Absolute length of {@code text-underline-offset}. NaN for {@code auto}. */
	public static double getUnderlineOffset(final CSSStyle style) {
		return resolveLength(style.get(UNDERLINE_OFFSET), style, Double.NaN);
	}

	private static double resolveLength(final Value value, final CSSStyle style, final double fallback) {
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		if (value instanceof PercentageValue percentage) {
			// Percentages are relative to 1em (css-text-decoration-4 §2.5/§2.7).
			return percentage.getRatio() * style.getFontStyle().getSize();
		}
		return fallback;
	}

	/**
	 * Reads one token as this longhand's value (also used by the shorthand).
	 * Returns null if it does not match.
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
			// Negative values are also allowed (move the line upward).
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
