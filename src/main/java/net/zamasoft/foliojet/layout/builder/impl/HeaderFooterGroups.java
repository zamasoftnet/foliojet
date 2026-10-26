package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;

/**
 * Only the first header group and the first footer group of a table are its header and footer; later ones are laid
 * out as row groups (CSS 2.1 §17.2), as browsers do (2026-10-26, found by the small-scope exhaustive check of the
 * anonymous table boxes: both table builders kept only the last one, and the content of the earlier ones was lost —
 * two {@code <thead>} in HTML too).
 */
final class HeaderFooterGroups {
	static final int HEADER_SEEN = 1, FOOTER_SEEN = 2;

	private HeaderFooterGroups() {
		// utility
	}

	/**
	 * Turns a repeated header or footer group into a body group, before anything reads its type.
	 *
	 * @param seen the {@code *_SEEN} bits of the groups the table already has
	 * @return the bits including this group
	 */
	static int demoteRepeated(final TableRowGroupBox group, final int seen) {
		final TableRowGroupPos pos = group.getTableRowGroupPos();
		final int bit = pos.rowGroupType == RowGroupType.HEADER ? HEADER_SEEN
				: pos.rowGroupType == RowGroupType.FOOTER ? FOOTER_SEEN : 0;
		if (bit == 0) {
			return seen;
		}
		if ((seen & bit) != 0) {
			pos.rowGroupType = RowGroupType.BODY;
			return seen;
		}
		return seen | bit;
	}
}
