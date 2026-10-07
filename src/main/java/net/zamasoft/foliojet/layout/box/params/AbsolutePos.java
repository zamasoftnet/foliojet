package net.zamasoft.foliojet.layout.box.params;

/**
 * Parameters for absolute positioning.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsolutePos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AbsolutePos implements Pos {
	/**
	 * Position settings for the top, bottom, left, and right.
	 */
	public Insets location = Insets.AUTO_INSETS;

	/**
	 * Position when location is AUTO.
	 */
	public AutoPosition autoPosition = AutoPosition.BLOCK;

	/**
	 * Positioning reference.
	 */
	public Fiducial fiducial = Fiducial.CONTEXT;

	public PosType getType() {
		return PosType.ABSOLUTE;
	}

	/** Logical block axis requiring a static position. In vertical writing, checks the left/right insets. */
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
