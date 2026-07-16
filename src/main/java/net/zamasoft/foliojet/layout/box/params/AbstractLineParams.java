package net.zamasoft.foliojet.layout.box.params;

/**
 * 行のパラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractLineParams.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractLineParams extends AbstractTextParams {
	public static final byte TEXT_ALIGN_START = 1;

	public static final byte TEXT_ALIGN_CENTER = 2;

	public static final byte TEXT_ALIGN_END = 3;

	public static final byte TEXT_ALIGN_JUSTIFY = 4;

	public static final byte TEXT_ALIGN_X_JUSTIFY_CENTER = 101;

	public byte textAlign = TEXT_ALIGN_START;

	public byte textAlignLast = TEXT_ALIGN_START;

	public Length textIndent = Length.ZERO_LENGTH;;

	/**
	 * 行の高さです。
	 */
	public double lineHeight = 0;

	public String toString() {
		return super.toString() + "[textAlign=" + this.textAlign + ",textAlignLast=" + this.textAlignLast
				+ ",textIndent=" + this.textIndent + ",lineHeight=" + this.lineHeight + "]";
	}
}
