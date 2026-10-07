package net.zamasoft.foliojet.layout.box.params;

/**
 * Normal-flow positioning parameters.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowPos extends AbstractNormalFlowPos implements Pos {
	public static final byte COLUMN_SPAN_SINGLE = 1;
	public static final byte COLUMN_SPAN_ALL = -1;

	/**
	 * Horizontal box alignment.
	 */
	public Align align = Align.START;

	/**
	 * Multi-column spanning.
	 */
	public byte columnSpan = COLUMN_SPAN_SINGLE;

	/**
	 * Explicit Grid item placement (Grid G4a). Read only when the element becomes an item as a direct
	 * child of a Grid container (ignored for other elements).
	 */
	public GridItemSpec gridItem = GridItemSpec.AUTO;

	/**
	 * Flex item sizing and alignment settings (Flex F1a). Read only when the element becomes an item
	 * as a direct child of a Flex container (ignored for other elements).
	 */
	public FlexItemSpec flexItem = FlexItemSpec.DEFAULT;

	public PosType getType() {
		return PosType.FLOW;
	}

	public String toString() {
		return super.toString() + "[align=" + this.align + "/columnSpan=" + this.columnSpan + "]";
	}
}
