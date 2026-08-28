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

	/** {@code aspect-ratio}の幅/高さ(0=指定なし。2026-08-29)。 */
	public double aspectRatio = 0;

	/**
	 * {@code aspect-ratio: auto <ratio>}か(2026-08-29)。trueなら画像の
	 * 固有比率があるときはそちらを優先し、無いときだけ指定比率を使う。
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
