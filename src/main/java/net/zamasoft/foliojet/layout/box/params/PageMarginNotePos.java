package net.zamasoft.foliojet.layout.box.params;

/**
 * Places JLREQ 4.2.7 parallel notes (sidenotes in horizontal writing, headnotes/footnotes in vertical
 * writing) in the margin on the logical line-start or line-end side of the type area.
 *
 * <p>CSS has no standard parallel-note setting, so these are created from the Copper extension
 * {@code float: -cssj-note-start | -cssj-note-end}.</p>
 */
public final class PageMarginNotePos extends FloatPos {
	/** Whether to place on the logical line-start side (false means logical line-end). */
	public final boolean start;

	public PageMarginNotePos(final boolean start) {
		this.start = start;
	}
}
