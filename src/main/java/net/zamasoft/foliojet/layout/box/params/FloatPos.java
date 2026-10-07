package net.zamasoft.foliojet.layout.box.params;

/**
 * Float positioning parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatPos extends AbstractNormalFlowPos implements Pos {
	public FloatSide floating = FloatSide.START;

	/**
	 * {@code shape-outside}, {@code shape-margin}, and {@code shape-image-threshold}
	 * (css-shapes-1, 2026-08-29). null means {@code none} (the margin-box rectangle).
	 * Like {@code floating}, written only during construction (an assumption for exclusion-area
	 * snapshots; see the description of {@code BlockBuilder.floatingsGeneration}).
	 */
	public ShapeOutsideParams shapeOutside = null;

	public PosType getType() {
		return PosType.FLOAT;
	}

	public String toString() {
		return super.toString() + "[floating=" + this.floating
				+ (this.shapeOutside == null ? "" : ",shapeOutside=" + this.shapeOutside) + "]";
	}
}
