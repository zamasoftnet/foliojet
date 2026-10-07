package net.zamasoft.foliojet.layout.box.params;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.StructureElement;

/**
 * Content parameters.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Params.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public abstract class Params {

	public static final byte Z_INDEX_AUTO = 0;
	public static final byte Z_INDEX_SPECIFIED = 1;

	/**
	 * The corresponding source element. Live construction uses {@code CSSElement}; source replay (BoxRecipe
	 * materialize) uses {@code StructureToken} (E-6 increment 3b-4; see the {@link StructureElement} Javadoc for
	 * the contract required by readers).
	 */
	public StructureElement element = null;

	/**
	 * The logical identifier of a footnote (footnote F4, 2026-07-31; consult-codex-2026-07-31-footnote-f4.txt).
	 * This engine-owned, monotonically increasing sequence number is not exposed to CSS. The body box of the
	 * footnote source element and the inline box of the {@code ::footnote-call} pseudo-element share the same ID.
	 * At page finalization, a set membership check determines whether the call remains on this page. This is
	 * independent of the displayed number (counter "footnote"), in preparation for per-page renumbering (F5). Boxes
	 * unrelated to footnotes use -1.
	 */
	public long footnoteId = -1;

	/**
	 * The depth of the box.
	 */
	public int zIndexValue = 0;

	public byte zIndexType = Z_INDEX_AUTO;

	/** Returns true if this box establishes a stacking context. */
	public boolean isStackingContext() {
		return this.zIndexType == Z_INDEX_SPECIFIED;
	}

	/**
	 * The visibility of the box.
	 */
	public float opacity = 1f;

	/**
	 * {@code mix-blend-mode} (compositing-1, 2026-08-29). Passed to {@code GC.setBlendMode} for each drawable
	 * element (see MixBlendMode).
	 */
	public net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;

	/**
	 * {@code filter} (filter-effects-1, 2026-08-29). Applied to each drawable element (see AbstractDrawable). The
	 * computed value already combines the parent's effects.
	 */
	public net.zamasoft.foliojet.css.value.css3.FilterValue filter = net.zamasoft.foliojet.css.value.css3.FilterValue.NONE;

	private static final AffineTransform IDENTITY_TRANSFORM = new AffineTransform();
	public AffineTransform transform = IDENTITY_TRANSFORM;

	/**
	 * The percentage components of {@code translate()} (added 2026-08-03). At drawing time, multiply them by the
	 * box width and height and add them to the translation. Since percentages refer to the element's own border
	 * box, they cannot be folded into the matrix during parsing.
	 */
	public double transformTxRatio = 0, transformTyRatio = 0;

	/**
	 * The cross components when a percentage translation follows a rotation or scale (2026-08-29, see {@code
	 * TransformValue}). Multiply {@code transformTxRatioH} by the height and add it to x; multiply {@code
	 * transformTyRatioW} by the width and add it to y.
	 */
	public double transformTxRatioH = 0, transformTyRatioW = 0;
	public Offset transformOrigin = Offset.HALF_OFFSET;

	/**
	 * {@code zoom} (2026-08-29). Approximates zoom by scaling the drawing of the element and its descendants about
	 * the top-left corner of the border box (Javadoc for {@code Zoom}). Applied outside {@code transform}.
	 */
	public double zoom = 1;

	/**
	 * {@code bookmark-level} and {@code bookmark-label} (2026-10-04). Null if both are at their defaults (bookmarks
	 * are generated from heading levels and text).
	 */
	public BookmarkSpec bookmark = null;

	public abstract ParamsType getType();

	public String toString() {
		return super.toString() + "[element=" + this.element + ",zIndex=" + this.zIndexValue + ",opacity="
				+ this.opacity + "]";
	}
}
