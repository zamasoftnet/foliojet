package net.zamasoft.foliojet.layout.box.params;

/**
 * ブロックボックスのパラメータです。
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

	public BoxSizingMode boxSizing = BoxSizingMode.CONTENT_BOX;

	public OverflowMode overflow = OverflowMode.VISIBLE;

	/**
	 * 通常ブロックコンテナの内容全体をブロック軸に配置する
	 * {@code align-content} (CSS Box Alignment Level 3 §5.1.1)。
	 * Flex/Grid は各レイアウト固有の同名フィールドを使う。
	 */
	public BoxAlignment blockAlignContent = BoxAlignment.NORMAL;

	/**
	 * mask-imageのグラデーション近似によるペイントクリップ(MaskImage参照)。
	 * overflow: hiddenと同じ描画クリップだけを適用し、レイアウトには影響しない。
	 */
	public boolean paintClip = false;

	/** {@code clip-path}の形状(なければnull。2026-08-22)。 */
	public ClipPathShape clipPath = null;

	public Columns columns = Columns.NONE_COLUMNS;

	public ParamsType getType() {
		return ParamsType.BLOCK;
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + "[firstLineStyle=" + this.firstLineStyle
				+ ",pageBreakInside=" + this.pageBreakInside + ",orphans=" + this.orphans + ",widows=" + this.widows
				+ ",size=" + this.size + ",minSize=" + this.minSize + ",maxSize=" + this.maxSize + ",boxSizing="
				+ this.boxSizing + ",overflow=" + this.overflow + ",blockAlignContent=" + this.blockAlignContent
				+ ",paintClip=" + this.paintClip + ",columns="
				+ this.columns + "]";
	}
}
