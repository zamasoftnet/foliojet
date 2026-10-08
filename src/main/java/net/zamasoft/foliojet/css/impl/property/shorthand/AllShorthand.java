package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.PropertyInfo;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code all} (css-cascade-4 §3.1, 2026-08-29).
 *
 * <p>
 * Accepts only CSS-wide keywords ({@code inherit}/{@code initial}/{@code unset};
 * {@code revert} variants cause the entire declaration to be ignored) and distributes them
 * to all longhands except {@code direction} and {@code unicode-bidi}.
 * Real sites use it to reset buttons ({@code all: unset}). Collects the longhand list
 * from the registry on the first call (registration order means it is incomplete at construction time).
 * </p>
 */
public final class AllShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new AllShorthand();

	private volatile PrimitivePropertyInfo[] longhands;

	private AllShorthand() {
		super("all");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		PrimitivePropertyInfo[] result = this.longhands;
		if (result == null) {
			final List<PrimitivePropertyInfo> list = new ArrayList<>();
			for (final PropertyInfo info : ElementPropertySet.getInstance().registeredInfos()) {
				if (info instanceof PrimitivePropertyInfo primitive && !list.contains(primitive)
						&& !"direction".equals(primitive.getName()) && !"unicode-bidi".equals(primitive.getName())
						&& ElementPropertySet.getCode(primitive) >= 0) {
					list.add(primitive);
				}
			}
			// Logical longhands first, physical ones last (2026-10-08): the later of a physical and a logical
			// declaration wins (CSS Logical 1 §4), and in all the physical one does, as in Chrome. In registry order a
			// vertical child's all: inherit took its left margin from the parent's margin-block-end.
			list.sort(java.util.Comparator.comparing(
					(PrimitivePropertyInfo primitive) -> !net.zamasoft.foliojet.css.CSSStyle.isLogical(primitive)));
			result = list.toArray(new PrimitivePropertyInfo[0]);
			this.longhands = result;
		}
		return result;
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		// Anything other than a CSS-wide keyword is invalid (the base class already handled CSS-wide keywords).
		throw new PropertyException();
	}
}
