package net.zamasoft.foliojet.style.box.params;

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
	public byte autoPosition = Types.AUTO_POSITION_BLOCK;

	/**
	 * 配置の基準です。
	 */
	public byte fiducial = Types.FODUCIAL_CONTEXT;

	public PosType getType() {
		return PosType.ABSOLUTE;
	}

	public String toString() {
		return super.toString() + "[location=" + this.location + ",fixed=" + this.fiducial + ",autoPosition="
				+ this.autoPosition + "]";
	}
}
