package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.InitialLetterValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code initial-letter} (css-inline-3, added 2026-08-20).
 *
 * <p>
 * The drop cap's line count and sink, specified on {@code ::first-letter}
 * (or the first inline box). The first-letter branch of {@code StyleEventMachine}
 * applies it via {@link #desugar}: calculates font size from the occupied line count
 * and desugars to the existing float mechanism (float:left + text wrapping).
 * A print differentiator supported by Prince/AH/WebKit, still unimplemented in Firefox
 * (non-Baseline).
 * </p>
 *
 * <p>
 * <b>Size approximation</b>: cap-height alignment uses the conventional approximation
 * "uppercase height = 0.7 × font size" (the actual font's OS/2 sCapHeight is unavailable
 * during style computation). Target cap height = (N-1)×line pitch + parent cap height.
 * </p>
 */
public class InitialLetter extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new InitialLetter();

	/** Conventional cap-height ratio (cap height/font size). */
	private static final double CAP_RATIO = 0.7;

	public static InitialLetterValue get(final CSSStyle style) {
		final Value value = style.get(InitialLetter.INFO);
		return value instanceof InitialLetterValue v ? v : null;
	}

	/**
	 * Desugars into the first-letter style. If {@code initial-letter} is specified,
	 * sets font size, line height, and float to use existing mechanisms.
	 *
	 * @param firstLetterStyle style of the first-letter pseudo-element (already applied)
	 * @param parentStyle parent (paragraph) style
	 */
	public static void desugar(final CSSStyle firstLetterStyle, final CSSStyle parentStyle) {
		final InitialLetterValue v = get(firstLetterStyle);
		if (v == null) {
			return;
		}
		final UserAgent ua = firstLetterStyle.getUserAgent();
		final double parentFontSize = FontSize.get(parentStyle);
		final double lineHeight = LineHeight.get(parentStyle);
		// Use the actual font cap-height ratio (2026-08-20, made more precise with user approval).
		// Derived from OS/2 sCapHeight (units/em=1000 convention). Invalid values (0 for missing data
		// or values above em) fall back to the conventional approximation 0.7.
		double capRatio = CAP_RATIO;
		try {
			// Resolve using the parent style's finalized FontStyle. firstLetterStyle will have
			// font-size, etc. set afterward, so calling getFontStyle() here would
			// freeze an unfinished state (measured: the drop cap collapsed to about the parent size).
			// The parent suffices because font-family overrides on first-letter are rare.
			final short cap = ua.getFontManager().getFontListMetrics(parentStyle.getFontStyle())
					.getFontMetrics(0).getFontSource().getCapHeight();
			if (cap > 200 && cap <= 1000) {
				capRatio = cap / 1000.0;
			}
		} catch (final RuntimeException e) {
			// Continue with the approximation even if font resolution fails.
		}
		// Target cap height: line pitch for (N-1) lines + parent cap height.
		final double targetCap = (v.lines() - 1) * lineHeight + parentFontSize * capRatio;
		final double size = targetCap / capRatio;
		firstLetterStyle.set(FontSize.INFO, AbsoluteLengthValue.create(ua, size));
		// Fix line height as an absolute value so the float box height is exactly sink lines
		// (using the font size, RealValue.ONE, makes the box exceed sink lines and extends
		// text wrapping by one extra line; confirmed against Chrome).
		firstLetterStyle.set(LineHeight.INFO, AbsoluteLengthValue.create(ua, v.sink() * lineHeight));
		if (v.sink() >= v.lines()) {
			// Normal drop cap: wrap text using a float.
			firstLetterStyle.set(net.zamasoft.foliojet.css.impl.property.box.CSSFloat.INFO,
					CSSFloatValue.LEFT_VALUE);
		}
		// sink < lines (raised cap) only enlarges the inline text (no float);
		// standing on the baseline approximates the specification.
	}

	protected InitialLetter() {
		super("initial-letter");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("normal")) {
				return KeywordValue.NORMAL;
			}
			throw new PropertyException();
		}
		final double lines;
		if (lu instanceof CssToken.Num num && num.value() >= 1) {
			lines = num.value();
		} else {
			throw new PropertyException();
		}
		int sink = (int) Math.floor(lines);
		if (tokens.hasNext()) {
			final CssToken second = tokens.next();
			if (second instanceof CssToken.Num num2 && num2.integer() && num2.value() >= 1) {
				sink = (int) num2.value();
			} else if (second instanceof CssToken.Ident id2 && id2.is("drop")) {
				sink = (int) Math.floor(lines);
			} else if (second instanceof CssToken.Ident id3 && id3.is("raise")) {
				sink = 1;
			} else {
				throw new PropertyException();
			}
		}
		return new InitialLetterValue(lines, sink);
	}
}
