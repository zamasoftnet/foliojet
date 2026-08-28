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
 * 論理境界ショートハンド {@code border-block-start} / {@code border-block-end} /
 * {@code border-inline-start} / {@code border-inline-end}(css-logical-1 §4.4、
 * 2026-08-29)。論理longhand({@code border-inline-start-width}等)は
 * 実装済みで、{@code border-top}と同じ文法で3つへ配るだけ。
 */
public final class LogicalBorderShorthand extends AbstractShorthandPropertyInfo {
	private final PrimitivePropertyInfo width, style, color;

	private LogicalBorderShorthand(final LogicalSide side) {
		super("border-" + side.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
		this.width = LogicalBorder.of(Aspect.WIDTH, side);
		this.style = LogicalBorder.of(Aspect.STYLE, side);
		this.color = LogicalBorder.of(Aspect.COLOR, side);
	}

	public static ShorthandPropertyInfo[] all() {
		final LogicalSide[] sides = LogicalSide.values();
		final ShorthandPropertyInfo[] infos = new ShorthandPropertyInfo[sides.length];
		for (int i = 0; i < sides.length; ++i) {
			infos[i] = new LogicalBorderShorthand(sides[i]);
		}
		return infos;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { this.width, this.style, this.color };
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		Value width = null;
		Value styleValue = null;
		Value color = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (width == null) {
				width = BorderValueUtils.toBorderWidth(ua, lu);
				if (width != null) {
					continue;
				}
			}
			if (styleValue == null) {
				styleValue = BorderValueUtils.toBorderStyle(lu);
				if (styleValue != null) {
					continue;
				}
			}
			if (color == null) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = KeywordValue.TRANSPARENT;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		primitives.set(this.width, width);
		primitives.set(this.style, styleValue);
		primitives.set(this.color, color);
	}
}
