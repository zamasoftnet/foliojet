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

	/**
	 * 行方向のwidth/min-width/max-width(縦書きはheight系)に書かれた
	 * 固有寸法キーワード(2026-08-29)。無ければnull。あるとき対応する
	 * {@code size}/{@code minSize}/{@code maxSize}の行方向はAUTO
	 * ({@link IntrinsicSize}参照)。
	 */
	public IntrinsicSize intrinsicLine = null;

	public IntrinsicSize intrinsicMinLine = null;

	public IntrinsicSize intrinsicMaxLine = null;

	public BoxSizingMode boxSizing = BoxSizingMode.CONTENT_BOX;

	public OverflowMode overflow = OverflowMode.VISIBLE;

	public static final byte TEXT_OVERFLOW_CLIP = 0;
	public static final byte TEXT_OVERFLOW_ELLIPSIS = 1;

	/**
	 * {@code text-overflow}(css-overflow-3、2026-08-29)。overflowが
	 * visible以外のときだけ意味を持つ(TextBuilder.applyTextOverflow)。
	 */
	public byte textOverflow = TEXT_OVERFLOW_CLIP;

	/**
	 * {@code line-clamp}/{@code -webkit-line-clamp}の行数(0=none。
	 * 2026-08-29)。このブロックのインライン内容(入れ子のブロックの行も
	 * 含む)をN行で打ち切り、後続があればN行目の末尾を省略記号で切る
	 * ({@code TextBuilder}と{@code LineClampState})。{@code maxSize}の
	 * N×line-heightと{@code overflow:hidden}は保険として残す。
	 */
	public int lineClamp = 0;

	/**
	 * {@code display: flow-root}(2026-08-29)。overflow:hiddenと同じく
	 * 独立BFCを作り、内側のfloatを親の排除域へ漏らさず、auto高さは
	 * 内側のfloatの下端まで伸びる。描画クリップは掛けない。
	 */
	public boolean flowRoot = false;

	/**
	 * {@code aspect-ratio}の幅/高さ(0=指定なし。2026-08-29)。非置換
	 * ボックスでは{@code auto}併記に意味が無いため比率だけを持つ。
	 * 適用は{@code FlowBlockBox.calculateSize}/
	 * {@code AbstractStaticBlockBox.shrinkToFit}。
	 */
	public double aspectRatio = 0;

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

	/**
	 * 行方向の寸法決定に内容の実測(固有寸法)が要るかどうか(2026-08-29)。
	 * 通常フローのブロックでtrueなら、浮動体と同じ2パス経路
	 * (TwoPassBlockBuilder → shrinkToFit)へ回す。
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
