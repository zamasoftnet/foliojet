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
	 * 行の高さです。
	 */
	public double lineHeight = LayoutUtils.NONE;

	public ParamsType getType() {
		return ParamsType.REPLACED;
	}

	public String toString() {
		return super.toString() + "[image=" + this.image + ",size=" + this.size + ",minSize=" + this.minSize
				+ ",maxSize=" + this.maxSize + ",boxSizing=" + this.boxSizing + ",objectFit=" + this.objectFit
				+ ",objectPosition=" + this.objectPosition + ",frame=" + this.frame + ",lineHeight="
				+ this.lineHeight + "]";
	}
}
