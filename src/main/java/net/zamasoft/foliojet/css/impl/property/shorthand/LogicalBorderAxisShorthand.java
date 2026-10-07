package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.border.LogicalBorder;
import net.zamasoft.foliojet.css.impl.property.border.LogicalBorder.Aspect;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Logical border shorthands for <b>both sides together</b> (css-logical-1 §4.5, 2026-08-30).
 *
 * <ul>
 * <li>{@code border-block} / {@code border-inline}:
 * read the same grammar as {@code border-block-start} and distribute to <b>both sides</b> of the axis</li>
 * <li>{@code border-block-width} / {@code -style} / {@code -color} and
 * {@code border-inline-*}: take {@code {1,2}} values; one applies to both sides,
 * two apply in start / end order</li>
 * </ul>
 *
 * <p>
 * Single-side {@link LogicalBorderShorthand} ({@code border-block-start}, etc.) and longhands
 * ({@code border-inline-start-width}, etc.) already exist; this only distributes values to them.
 * Only the both-sides shorthands were missing (found in the MDN Baseline inventory on 2026-08-30).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LogicalBorderAxisShorthand extends AbstractShorthandPropertyInfo {
	/** Axis (block/inline) and its start / end. */
	private enum Axis {
		BLOCK("block", LogicalSide.BLOCK_START, LogicalSide.BLOCK_END),
		INLINE("inline", LogicalSide.INLINE_START, LogicalSide.INLINE_END);

		final String name;
		final LogicalSide start, end;

		Axis(final String name, final LogicalSide start, final LogicalSide end) {
			this.name = name;
			this.start = start;
			this.end = end;
		}
	}

	/** Null means the {@code border-block} form (reads width/style/color together). */
	private final Aspect aspect;

	private final PrimitivePropertyInfo startWidth, startStyle, startColor;

	private final PrimitivePropertyInfo endWidth, endStyle, endColor;

	private LogicalBorderAxisShorthand(final Axis axis, final Aspect aspect) {
		super(aspect == null ? "border-" + axis.name
				: "border-" + axis.name + "-" + aspect.name().toLowerCase(java.util.Locale.ROOT));
		this.aspect = aspect;
		this.startWidth = LogicalBorder.of(Aspect.WIDTH, axis.start);
		this.startStyle = LogicalBorder.of(Aspect.STYLE, axis.start);
		this.startColor = LogicalBorder.of(Aspect.COLOR, axis.start);
		this.endWidth = LogicalBorder.of(Aspect.WIDTH, axis.end);
		this.endStyle = LogicalBorder.of(Aspect.STYLE, axis.end);
		this.endColor = LogicalBorder.of(Aspect.COLOR, axis.end);
	}

	/** Creates eight shorthands ({@code border-block}/{@code border-inline} × four forms). */
	public static ShorthandPropertyInfo[] all() {
		final Aspect[] aspects = { null, Aspect.WIDTH, Aspect.STYLE, Aspect.COLOR };
		final ShorthandPropertyInfo[] infos = new ShorthandPropertyInfo[Axis.values().length * aspects.length];
		int i = 0;
		for (final Axis axis : Axis.values()) {
			for (final Aspect aspect : aspects) {
				infos[i++] = new LogicalBorderAxisShorthand(axis, aspect);
			}
		}
		return infos;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		if (this.aspect == null) {
			return new PrimitivePropertyInfo[] { this.startWidth, this.startStyle, this.startColor, //
					this.endWidth, this.endStyle, this.endColor };
		}
		return switch (this.aspect) {
		case WIDTH -> new PrimitivePropertyInfo[] { this.startWidth, this.endWidth };
		case STYLE -> new PrimitivePropertyInfo[] { this.startStyle, this.endStyle };
		case COLOR -> new PrimitivePropertyInfo[] { this.startColor, this.endColor };
		};
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		if (this.aspect == null) {
			this.parseBorder(tokens, ua, primitives);
			return;
		}
		// {1,2}: one value for both sides, two for start / end.
		final Value first = this.parseAspect(tokens, ua);
		if (first == null) {
			throw new PropertyException();
		}
		Value second = first;
		if (tokens.hasNext()) {
			second = this.parseAspect(tokens, ua);
			if (second == null || tokens.hasNext()) {
				throw new PropertyException();
			}
		}
		switch (this.aspect) {
		case WIDTH -> {
			primitives.set(this.startWidth, first);
			primitives.set(this.endWidth, second);
		}
		case STYLE -> {
			primitives.set(this.startStyle, first);
			primitives.set(this.endStyle, second);
		}
		case COLOR -> {
			primitives.set(this.startColor, first);
			primitives.set(this.endColor, second);
		}
		}
	}

	/** {@code border-block: 1px solid red} form. Distributes the same values to both sides. */
	private void parseBorder(final TokenStream tokens, final UserAgent ua, final Primitives primitives)
			throws PropertyException {
		Value width = null, style = null, color = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (width == null && (width = BorderValueUtils.toBorderWidth(ua, lu)) != null) {
				continue;
			}
			if (style == null && (style = BorderValueUtils.toBorderStyle(lu)) != null) {
				continue;
			}
			if (color == null) {
				color = ColorValueUtils.isTransparent(lu) ? KeywordValue.TRANSPARENT
						: ColorValueUtils.toColorOrCurrent(ua, lu);
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		primitives.set(this.startWidth, width);
		primitives.set(this.startStyle, style);
		primitives.set(this.startColor, color);
		primitives.set(this.endWidth, width);
		primitives.set(this.endStyle, style);
		primitives.set(this.endColor, color);
	}

	/** Reads one value of an individual form ({@code -width}/{@code -style}/{@code -color}). */
	private Value parseAspect(final TokenStream tokens, final UserAgent ua) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu == null) {
			return null;
		}
		return switch (this.aspect) {
		case WIDTH -> BorderValueUtils.toBorderWidth(ua, lu);
		case STYLE -> BorderValueUtils.toBorderStyle(lu);
		case COLOR -> ColorValueUtils.isTransparent(lu) ? KeywordValue.TRANSPARENT
				: ColorValueUtils.toColorOrCurrent(ua, lu);
		};
	}
}
