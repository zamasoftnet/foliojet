package net.zamasoft.foliojet.layout.box.params;

/**
 * Block box parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BlockParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BlockParams extends AbstractLineParams {
	public RectFrame frame = RectFrame.NULL_FRAME;

	public FirstLineParams firstLineStyle = null;

	public PageBreakMode pageBreakInside = PageBreakMode.AUTO;

	public byte orphans = 2;

	public byte widows = 2;

	public Dimension size = Dimension.AUTO_DIMENSION;

	public Dimension minSize = Dimension.ZERO_DIMENSION;

	public Dimension maxSize = Dimension.AUTO_DIMENSION;

	/**
	 * Intrinsic size keywords specified for line-direction width/min-width/max-width (height properties
	 * in vertical writing) (2026-08-29). null if absent. When present, the corresponding line-direction
	 * value in {@code size}/{@code minSize}/{@code maxSize} is AUTO (see {@link IntrinsicSize}).
	 */
	public IntrinsicSize intrinsicLine = null;

	public IntrinsicSize intrinsicMinLine = null;

	public IntrinsicSize intrinsicMaxLine = null;

	public BoxSizingMode boxSizing = BoxSizingMode.CONTENT_BOX;

	public OverflowMode overflow = OverflowMode.VISIBLE;

	public static final byte TEXT_OVERFLOW_CLIP = 0;
	public static final byte TEXT_OVERFLOW_ELLIPSIS = 1;

	/**
	 * {@code text-overflow} (css-overflow-3, 2026-08-29). Meaningful only when overflow is not visible
	 * (TextBuilder.applyTextOverflow).
	 */
	public byte textOverflow = TEXT_OVERFLOW_CLIP;

	/**
	 * Line count for {@code line-clamp}/{@code -webkit-line-clamp} (0 = none; 2026-08-29).
	 * Truncates this block's inline content (including lines in nested blocks) at N lines; if more content
	 * follows, truncates line N with an ellipsis ({@code TextBuilder} and {@code LineClampState}).
	 * Keeps N×line-height in {@code maxSize} and {@code overflow:hidden} as safeguards.
	 */
	public int lineClamp = 0;

	/**
	 * {@code display: flow-root} (2026-08-29). Like overflow:hidden, creates an independent BFC,
	 * prevents inner floats from leaking into the parent's exclusion area, and extends auto height
	 * to the bottom of inner floats. Does not apply a drawing clip.
	 */
	public boolean flowRoot = false;

	/**
	 * Width/height ratio for {@code aspect-ratio} (0 = unspecified; 2026-08-29). For non-replaced boxes,
	 * adding {@code auto} has no meaning, so only the ratio is stored.
	 * Applied by {@code FlowBlockBox.calculateSize}/{@code AbstractStaticBlockBox.shrinkToFit}.
	 */
	public double aspectRatio = 0;

	/**
	 * {@code align-content} places all content of a normal block container along the block axis
	 * (CSS Box Alignment Level 3 §5.1.1).
	 * Flex/Grid use the same-named fields specific to their respective layouts.
	 */
	public BoxAlignment blockAlignContent = BoxAlignment.NORMAL;

	/**
	 * Paint clip from the gradient approximation of mask-image (see MaskImage).
	 * Applies only the same drawing clip as overflow: hidden, without affecting layout.
	 */
	public boolean paintClip = false;

	/** {@code clip-path} shape (null if absent; 2026-08-22). */
	public ClipPathShape clipPath = null;

	/**
	 * Returns whether content overflowing the box is excluded from drawing (2026-10-02).
	 *
	 * <p>
	 * If any of {@code overflow} clipping, {@link #paintClip}, or {@code clip-path} applies, overflow
	 * is not measured as painted content (for page-splitting metrics). Although {@code clip-path} can
	 * specify shapes extending outside the reference box, it is treated here as clipping at the box:
	 * after splitting, each fragment clips again using its own reference box ({@link ClipPathShape}),
	 * so counting overflow when cutting would reveal previously hidden content in the next fragment.
	 * </p>
	 */
	public boolean clipsOverflowPaint() {
		return this.overflow.clipsPaint() || this.paintClip || this.clipPath != null;
	}

	public Columns columns = Columns.NONE_COLUMNS;

	/**
	 * Whether line-direction sizing requires measuring content (intrinsic size) (2026-08-29).
	 * If true for a normal-flow block, routes it through the same two-pass path as floats
	 * (TwoPassBlockBuilder → shrinkToFit).
	 */
	public boolean hasIntrinsicLine() {
		return this.intrinsicLine != null || this.intrinsicMinLine != null || this.intrinsicMaxLine != null;
	}

	public ParamsType getType() {
		return ParamsType.BLOCK;
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + "[firstLineStyle=" + this.firstLineStyle
				+ ",pageBreakInside=" + this.pageBreakInside + ",orphans=" + this.orphans + ",widows=" + this.widows
				+ ",size=" + this.size + ",minSize=" + this.minSize + ",maxSize=" + this.maxSize + ",boxSizing="
				+ this.boxSizing + ",overflow=" + this.overflow + ",textOverflow=" + this.textOverflow + ",lineClamp=" + this.lineClamp
				+ ",aspectRatio=" + this.aspectRatio
				+ ",blockAlignContent=" + this.blockAlignContent
				+ ",paintClip=" + this.paintClip + ",columns="
				+ this.columns + "]";
	}
}
