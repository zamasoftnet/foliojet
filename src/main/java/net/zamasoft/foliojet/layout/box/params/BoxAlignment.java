package net.zamasoft.foliojet.layout.box.params;

/**
 * CSS Box Alignment values (Grid G5a, 2026-07-31;
 * consult-codex-2026-07-31-grid-g5.txt Q2). Subset for Grid item/content alignment:
 * {@code AUTO} is only for self properties (refers to the container value); {@code NORMAL} is the
 * layout-mode default (resolves to {@code STRETCH} for Grid in G5). baseline, space-*, and safe/unsafe
 * are outside this subset (invalid declarations). Separate from the existing {@link Align}
 * (block/table auto-margin resolution).
 *
 * @author MIYABE Tatsuhiko
 */
public enum BoxAlignment {
	AUTO, NORMAL, START, CENTER, END, STRETCH;

	/**
	 * Resolves to the used value (recommendation Q2: do not rewrite during capture; always obtain
	 * the same resolution from frozen values at bind time, ensuring replay determinism).
	 *
	 * @param self      item's self value (auto allowed)
	 * @param container container's items value (never auto)
	 * @return one of START/CENTER/END/STRETCH
	 */
	public static BoxAlignment resolve(final BoxAlignment self, final BoxAlignment container) {
		final BoxAlignment effective = self == AUTO ? container : self;
		return effective == NORMAL || effective == AUTO ? STRETCH : effective;
	}
}
