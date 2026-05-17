package jp.cssj.homare.style.box.params;

/**
 * table,inline-table,table-caption,table-cell以外のテーブル関連ボックスのパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: InnerTableParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class InnerTableParams extends Params {
	public Background background = Background.NULL_BACKGROUND;

	public RectBorder border = RectBorder.NONE_RECT_BORDER;

	public Length size = Length.AUTO_LENGTH;

	public Length minSize = Length.ZERO_LENGTH;

	public Length maxSize = Length.AUTO_LENGTH;

	public byte pageBreakInside = Types.PAGE_BREAK_AUTO;

	public byte getType() {
		return TYPE_INNER_TABLE;
	}

	public String toString() {
		return super.toString() + "[background=" + this.background + ",border=" + this.border + ",size=" + this.size
				+ ",minSize=" + this.minSize + ",maxSize=" + this.maxSize + ",pageBreakInside=" + this.pageBreakInside
				+ "]";
	}
}
