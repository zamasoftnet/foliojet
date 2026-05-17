package jp.cssj.homare.style.box.params;

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
	public byte align = Types.ALIGN_START;

	/**
	 * マルチカラムの連結です。
	 */
	public byte columnSpan = COLUMN_SPAN_SINGLE;

	public byte getType() {
		return TYPE_FLOW;
	}

	public String toString() {
		return super.toString() + "[align=" + this.align + "/columnSpan=" + this.columnSpan + "]";
	}
}
