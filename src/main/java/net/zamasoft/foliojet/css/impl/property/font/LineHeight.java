package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-line-height"> line-
 * height property </a>.
 *
 * @author MIYABE Tatsuhiko
 */
public class LineHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineHeight();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof RealValue real) {
			return real.getReal() * FontSize.get(style);
		}
		if (value == KeywordValue.NORMAL) {
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected LineHeight() {
		super("line-height");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// SPEC css-inline-3: line-height cannot be negative. Clamp negative results from folding lh into calc(), etc.
		// to 0 rather than invalidating the declaration (css-values-4
		// range checking, 2026-08-27)
		return clampNonNegative(this.computeValue(value, style));
	}

	private static Value clampNonNegative(Value value) {
		if (value instanceof AbsoluteLengthValue length && length.getLength() < 0) {
			return AbsoluteLengthValue.ZERO;
		}
		return value;
	}

	/**
	 * Returns whether this unit is self-referential as a value of line-height itself.
	 * {@code lh} always is; {@code rlh} is only when used in the root element's line-height
	 * (descendants can safely read the root's computed value).
	 */
	private static boolean isSelfReferentialLineHeightUnit(net.zamasoft.foliojet.css.token.Unit unit,
			CSSStyle style) {
		if (unit == net.zamasoft.foliojet.css.token.Unit.LH) {
			return true;
		}
		return unit == net.zamasoft.foliojet.css.token.Unit.RLH && style.getRootStyle() == style;
	}

	private Value computeValue(Value value, CSSStyle style) {
		if (value == KeywordValue.NORMAL || value instanceof RealValue) {
			return value;
		}
		// When lh appears in line-height itself, first fold it against the inherited value
		// (parent line-height, or UA normal at the root) to avoid self-reference
		// (SPEC css-values-4 §6.1.2). Folding here guarantees that resolving lh
		// in other properties (RelativeLengthValue.toAbsoluteLength→LineHeight.get)
		// does not recurse.
		if (value instanceof RelativeLengthValue rel && isSelfReferentialLineHeightUnit(rel.getUnit(), style)) {
			return AbsoluteLengthValue.create(style.getUserAgent(), inheritedLineHeight(style) * rel.getValue());
		}
		if (value instanceof CalcFontRelativeValue lhCalc && lhCalc.usesLh()) {
			value = lhCalc.resolveLh(style.getUserAgent(), inheritedLineHeight(style));
		}
		if (value instanceof CalcFontRelativeValue fontRelative) {
			// Resolve font-relative components using this element's font, then resolve remaining % components
			// in the branches below. Deferring to emExToAbsoluteLength at the end would
			// finalize a CalcLengthValue with a remaining % as the computed value,
			// causing the cast in LineHeight.get to fail (observed with calc(50% + 0.5em)).
			value = fontRelative.resolve(style);
		}
		if (value instanceof PercentageValue percentage) {
			return AbsoluteLengthValue.create(style.getUserAgent(), percentage.getRatio() * FontSize.get(style));
		}
		if (value instanceof CalcLengthValue calc) {
			// When calc() mixes absolute lengths and percentages (e.g. calc(50% + 10pt)).
			// Like font-size percentages, line-height percentages can be resolved here, but against
			// this element's font-size rather than its parent's. Treat them like PercentageValue
			// and reduce completely to AbsoluteLengthValue.
			return AbsoluteLengthValue.create(style.getUserAgent(),
					calc.getAbsolute() + calc.getRatio() * FontSize.get(style));
		}
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	/**
	 * Inherited line-height used as the lh reference (UA normal at the root element).
	 *
	 * <p>
	 * In deep inheritance chains (especially boxless {@code display:contents} chains),
	 * if each level has {@code line-height:1lh}, naive {@code get(parent)} recursion
	 * adds a stack frame for every ancestor. Finalize computed values from the root downward
	 * to populate the cache and limit recursion depth to one parent level
	 * (2026-08-27, noted in an independent review).
	 * </p>
	 */
	private static double inheritedLineHeight(CSSStyle style) {
		final CSSStyle parent = style.getParentStyle();
		if (parent == null) {
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		}
		final java.util.ArrayList<CSSStyle> chain = new java.util.ArrayList<>();
		for (CSSStyle s = parent; s != null; s = s.getParentStyle()) {
			chain.add(s);
		}
		for (int i = chain.size() - 1; i >= 1; --i) {
			get(chain.get(i));
		}
		return get(parent);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value lineHeight = BoxValueUtils.toLineHeight(ua, lu);
		if (lineHeight == null) {
			throw new PropertyException();
		}
		return lineHeight;
	}

}