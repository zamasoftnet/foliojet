package net.zamasoft.foliojet.layout.box.params;

/**
 * Page float positioning ({@code float: top} / {@code float: bottom})
 * (2026-08-02; top priority in PLAN §2. Aligns figures and tables to page edges in book typesetting).
 *
 * <p>
 * Like {@link FootnotePos}, extends {@link FloatPos} and flows through as {@code PosType.FLOAT},
 * reusing the separate builder's lifecycle (container-builder push/pop and range sealing).
 * Differs from left/right floats only at completion, when it goes to the page registry
 * ({@code RootBuilder}) instead of {@code addBound} on the parent. After placement, top floats
 * serve as two-dimensional exclusion areas only for line scanning in Root coordinates.
 * </p>
 */
public final class PageFloatPos extends FloatPos {

	/** Whether to align to the page top (false means bottom). */
	public final boolean top;

	/**
	 * Whether top/bottom are physical directions ({@code top}/{@code bottom}; false means logical
	 * {@code block-start}/{@code block-end}). They coincide in horizontal writing. In vertical writing,
	 * a physical bottom float sits at the bottom of the sheet (the end of inline progression) and shortens
	 * lines, while a logical one sits at the block end (the left/right edge in vertical writing)
	 * (2026-10-05). Both top variants in vertical writing sit on the line-start side.
	 */
	public final boolean physical;

	public PageFloatPos(final boolean top, final boolean physical) {
		this.top = top;
		this.physical = physical;
	}
}
