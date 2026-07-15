package net.zamasoft.foliojet.style.box.params;

/**
 * ブロックボックスのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BlockParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BlockParams extends AbstractLineParams {
	public RectFrame frame = RectFrame.NULL_FRAME;

	public FirstLineParams firstLineStyle = null;

	public byte pageBreakInside = Types.PAGE_BREAK_AUTO;

	public byte orphans = 2;

	public byte widows = 2;

	public Dimension size = Dimension.AUTO_DIMENSION;

	public Dimension minSize = Dimension.ZERO_DIMENSION;

	public Dimension maxSize = Dimension.AUTO_DIMENSION;

	public byte boxSizing = Types.BOX_SIZING_CONTENT_BOX;

	public byte overflow = Types.OVERFLOW_VISIBLE;

	public Columns columns = Columns.NONE_COLUMNS;

	public ParamsType getType() {
		return ParamsType.BLOCK;
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + "[firstLineStyle=" + this.firstLineStyle
				+ ",pageBreakInside=" + this.pageBreakInside + ",orphans=" + this.orphans + ",widows=" + this.widows
				+ ",size=" + this.size + ",minSize=" + this.minSize + ",maxSize=" + this.maxSize + ",boxSizing="
				+ this.boxSizing + ",overflow=" + this.overflow + ",columns=" + this.columns + "]";
	}
}
