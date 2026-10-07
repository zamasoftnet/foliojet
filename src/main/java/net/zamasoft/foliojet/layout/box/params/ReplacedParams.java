package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.image.Image;

public class ReplacedParams extends AbstractTextParams {
	public Image image = null;

	public Dimension size = Dimension.AUTO_DIMENSION;

	public Dimension minSize = Dimension.ZERO_DIMENSION;

	public Dimension maxSize = Dimension.AUTO_DIMENSION;

	public BoxSizingMode boxSizing = BoxSizingMode.CONTENT_BOX;

	public ObjectFitMode objectFit = ObjectFitMode.FILL;

	public Offset objectPosition = Offset.HALF_OFFSET;

	public RectFrame frame = RectFrame.NULL_FRAME;

	/**
	 * The line height.
	 */
	public double lineHeight = LayoutUtils.NONE;

	/**
	 * {@code clip-path} (2026-08-29). {@link BlockParams} holds it for blocks, but replaced elements have no {@link
	 * BlockParams}, so copy the same shape here. Without this, clip-path on {@code <img>} was silently ignored.
	 */
	public ClipPathShape clipPath = null;

	/** The width/height ratio of {@code aspect-ratio} (0 = unspecified; 2026-08-29). */
	public double aspectRatio = 0;

	/**
	 * Whether this is {@code aspect-ratio: auto <ratio>} (2026-08-29). If true, prefer the image's intrinsic ratio
	 * when available; use the specified ratio only otherwise.
	 */
	public boolean aspectRatioAuto = false;

	public ParamsType getType() {
		return ParamsType.REPLACED;
	}

	public String toString() {
		return super.toString() + "[image=" + this.image + ",size=" + this.size + ",minSize=" + this.minSize
				+ ",maxSize=" + this.maxSize + ",boxSizing=" + this.boxSizing + ",objectFit=" + this.objectFit
				+ ",objectPosition=" + this.objectPosition + ",frame=" + this.frame + ",lineHeight="
				+ this.lineHeight + ",aspectRatio=" + this.aspectRatio + (this.aspectRatioAuto ? "(auto)" : "") + "]";
	}
}
