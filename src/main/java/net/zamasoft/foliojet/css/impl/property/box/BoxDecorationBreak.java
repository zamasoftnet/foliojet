package net.zamasoft.foliojet.css.impl.property.box;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.Value;

/**
 * {@code box-decoration-break: slice | clone} (css-break-3 §5.4, added 2026-11-15; publishing report
 * TECH-20261115-001).
 *
 * <p>
 * A simplified {@code clone}: where a page or column break (or a {@code column-span: all} element) cuts a block box,
 * each fragment paints the box's border on the cut side too, inside the fragment's own area. No padding is added and
 * nothing is laid out differently from {@code slice}, so the closing border sits right at the last line. Inline boxes
 * broken across lines and table cells stay sliced.
 * </p>
 */
public class BoxDecorationBreak extends KeywordPropertyInfo {
	private static final Value SLICE = keyword("slice");

	private static final Value CLONE = keyword("clone");

	public static final PrimitivePropertyInfo INFO = new BoxDecorationBreak();

	public static boolean isClone(final CSSStyle style) {
		return style.get(INFO) == CLONE;
	}

	protected BoxDecorationBreak() {
		super("box-decoration-break", false, SLICE, Map.of("slice", SLICE, "clone", CLONE));
	}

	private static Value keyword(final String name) {
		return new Value() {
			@Override
			public String toString() {
				return name;
			}
		};
	}
}
