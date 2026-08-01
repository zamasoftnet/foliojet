package net.zamasoft.foliojet.layout.box.params;

/**
 * 通常のフローの配置パラメータです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowPos.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowPos extends AbstractNormalFlowPos implements Pos {
	public static final byte COLUMN_SPAN_SINGLE = 1;
	public static final byte COLUMN_SPAN_ALL = -1;

	/**
	 * ボックスの水平方向配置です。
	 */
	public Align align = Align.START;

	/**
	 * マルチカラムの連結です。
	 */
	public byte columnSpan = COLUMN_SPAN_SINGLE;

	/**
	 * Grid itemの明示配置です(Grid G4a)。Grid直下の子としてitem化
	 * されるときだけ参照される(それ以外の要素では無視)。
	 */
	public GridItemSpec gridItem = GridItemSpec.AUTO;

	/**
	 * Flex itemの伸縮・整列指定です(Flex F1a)。Flex直下の子として
	 * item化されるときだけ参照される(それ以外の要素では無視)。
	 */
	public FlexItemSpec flexItem = FlexItemSpec.DEFAULT;

	public PosType getType() {
		return PosType.FLOW;
	}

	public String toString() {
		return super.toString() + "[align=" + this.align + "/columnSpan=" + this.columnSpan + "]";
	}
}
