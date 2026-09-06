package net.zamasoft.foliojet.layout.box.params;

/**
 * 絶対配置の配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsolutePos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AbsolutePos implements Pos {
	/**
	 * 上下左右の位置指定です。
	 */
	public Insets location = Insets.AUTO_INSETS;

	/**
	 * locationがAUTOの場合の位置です。
	 */
	public AutoPosition autoPosition = AutoPosition.BLOCK;

	/**
	 * 配置の基準です。
	 */
	public Fiducial fiducial = Fiducial.CONTEXT;

	public PosType getType() {
		return PosType.ABSOLUTE;
	}

	/** 静的位置が必要な論理ブロック軸。縦組みでは左右の inset を調べる。 */
	public boolean usesStaticPageAxis(final WritingMode flow) {
		return flow.isVertical()
				? this.location.getLeftType() == LengthType.AUTO && this.location.getRightType() == LengthType.AUTO
				: this.location.getTopType() == LengthType.AUTO && this.location.getBottomType() == LengthType.AUTO;
	}

	public String toString() {
		return super.toString() + "[location=" + this.location + ",fixed=" + this.fiducial + ",autoPosition="
				+ this.autoPosition + "]";
	}
}
