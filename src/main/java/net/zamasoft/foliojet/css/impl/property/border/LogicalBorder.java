package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * <b>Logical border properties</b> (added 2026-08-03):
 * the 12 {@code border-block-start-*} / {@code border-block-end-*} /
 * {@code border-inline-start-*} / {@code border-inline-end-*} properties.
 *
 * <p>
 * The physical side depends on the writing direction ({@link LogicalSide}). When the physical property of the same
 * side is declared too, the later declaration wins (2026-10-08, CSS Logical 1 §4; {@link LogicalSide#resolve}).
 *
 * <p>
 * <b>Why these are needed</b>: HTML {@code <hr noshade>} requests a border only at the block-end
 * side. This remained in Java because CSS lacked the vocabulary to express it
 * (found during the 2026-08-03 HTMLStyle move). Logical properties themselves are standard
 * features in Baseline and directly useful to users.
 */
public final class LogicalBorder extends AbstractPrimitivePropertyInfo {
	/** What the property specifies. */
	public enum Aspect {
		WIDTH("width"), STYLE("style"), COLOR("color");

		final String text;

		Aspect(String text) {
			this.text = text;
		}
	}

	private static final LogicalBorder[][] BY_ASPECT_SIDE = new LogicalBorder[Aspect.values().length][LogicalSide
			.values().length];

	static {
		for (final Aspect aspect : Aspect.values()) {
			for (final LogicalSide side : LogicalSide.values()) {
				BY_ASPECT_SIDE[aspect.ordinal()][side.ordinal()] = new LogicalBorder(aspect, side);
			}
		}
	}

	public static LogicalBorder of(Aspect aspect, LogicalSide side) {
		return BY_ASPECT_SIDE[aspect.ordinal()][side.ordinal()];
	}

	/** Returns all 12 properties for registration. */
	public static LogicalBorder[] all() {
		final LogicalBorder[] all = new LogicalBorder[Aspect.values().length * LogicalSide.values().length];
		int i = 0;
		for (final Aspect aspect : Aspect.values()) {
			for (final LogicalSide side : LogicalSide.values()) {
				all[i++] = of(aspect, side);
			}
		}
		return all;
	}

	/**
	 * The four properties of an aspect indexed by {@link LogicalSide#ordinal()}, the logical half of the pairs
	 * {@link LogicalSide#resolve} resolves.
	 */
	public static PrimitivePropertyInfo[] bySide(Aspect aspect) {
		return BY_ASPECT_SIDE[aspect.ordinal()];
	}

	private final Aspect aspect;

	private LogicalBorder(Aspect aspect, LogicalSide side) {
		super("border-" + text(side) + "-" + aspect.text);
		this.aspect = aspect;
	}

	private static String text(LogicalSide side) {
		switch (side) {
		case BLOCK_START:
			return "block-start";
		case BLOCK_END:
			return "block-end";
		case INLINE_START:
			return "inline-start";
		default:
			return "inline-end";
		}
	}

	public Value getDefault(CSSStyle style) {
		switch (this.aspect) {
		case STYLE:
			return BorderStyleValue.NONE_VALUE;
		case COLOR:
			return KeywordValue.NONE;
		default:
			// medium, as border-left-width (2026-10-08): border-inline-start-width: initial, and the width a
			// border-inline-start: solid leaves out, gave 0 instead
			return style.getUserAgent().getBorderWidth(net.zamasoft.foliojet.ua.BorderWidthKeyword.MEDIUM);
		}
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (this.aspect == Aspect.STYLE) {
			return value;
		}
		// Resolve typed attr() for both widths and colors (2026-08-04). Fall back to
		// currentColor for unresolved colors; BorderColor.get() requires a ColorValue.
		value = ValueUtils.emExToAbsoluteLength(value, style);
		if (this.aspect == Aspect.COLOR && (value == KeywordValue.NONE || value == KeywordValue.DEFAULT)) {
			value = style.get(net.zamasoft.foliojet.css.impl.property.text.CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		// Typed attr() (2026-08-03). Get border width/color from attributes.
		final Value attrValue = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu, this.aspect == Aspect.COLOR ? net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR
						: net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.LENGTH);
		if (attrValue != null) {
			return attrValue;
		}
		final Value value;
		switch (this.aspect) {
		case STYLE:
			value = BorderValueUtils.toBorderStyle(lu);
			break;
		case COLOR:
			value = ColorValueUtils.toColorOrCurrent(ua, lu);
			break;
		default: {
			final LengthValue width = BorderValueUtils.toBorderWidth(ua, lu);
			value = width;
			break;
		}
		}
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
