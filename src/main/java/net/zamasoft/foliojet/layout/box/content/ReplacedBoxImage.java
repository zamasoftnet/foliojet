package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * An image that receives state from the box being laid out (implementations must also implement
 * {@link Image}). {@code AbstractReplacedBox.calculateSize()} calls {@link #setReplacedBox}
 * on every layout, so implementations hold mutable state that cannot be shared.
 * Unlike ordinary immutable, reentrant images (loaded from URLs, etc.), the freezing path
 * ({@code ReplacedParamsTemplate.freeze}) freezes an independent {@link #duplicate} at recording
 * time and supplies a further copy on each materialization. This prevents shared state between
 * live layout and replay, and between replays (E-6 increment 3b-6).
 */
public interface ReplacedBoxImage {
	public void setReplacedBox(AbstractReplacedBox box, double width, double height);

	/**
	 * Returns an independent copy (introduced in E-6 increment 3b-3; made a standard part of
	 * the freeze path in 3b-6, 2026-07-24). Source replay creates fresh boxes without touching
	 * the live box tree, but sharing image instances lets a replay box take over the
	 * {@link #setReplacedBox} back-reference, potentially corrupting live drawing state.
	 * Copies may share drawing content (the immutable part), but state received through
	 * {@link #setReplacedBox} must be independent for each copy.
	 * <b>The copy itself must also implement {@link ReplacedBoxImage} (and {@link Image})</b>,
	 * because each materialization supplies another copy of the frozen copy
	 * ({@code ReplacedParamsTemplate.materialize}).
	 */
	public Image duplicate();
}
