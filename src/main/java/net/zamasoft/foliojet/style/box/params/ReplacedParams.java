package net.zamasoft.foliojet.style.box.params;

import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.pdfg2d.gc.image.Image;

public class ReplacedParams extends AbstractTextParams {
	public Image image = null;

	public Dimension size = Dimension.AUTO_DIMENSION;

	public Dimension minSize = Dimension.ZERO_DIMENSION;

	public Dimension maxSize = Dimension.AUTO_DIMENSION;

	public BoxSizingMode boxSizing = BoxSizingMode.CONTENT_BOX;

	public RectFrame frame = RectFrame.NULL_FRAME;

	/**
	 * 行の高さです。
	 */
	public double lineHeight = StyleUtils.NONE;

	public ParamsType getType() {
		return ParamsType.REPLACED;
	}

	public String toString() {
		return super.toString() + "[image=" + this.image + ",size=" + this.size + ",minSize=" + this.minSize
				+ ",maxSize=" + this.maxSize + ",boxSizing=" + this.boxSizing + ",frame=" + this.frame + ",lineHeight="
				+ this.lineHeight + "]";
	}
}
