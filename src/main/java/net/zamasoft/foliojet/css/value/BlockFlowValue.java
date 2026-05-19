package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BlockFlowValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BlockFlowValue implements Value {
	public static final BlockFlowValue TB_VALUE = new BlockFlowValue(AbstractTextParams.FLOW_TB);

	public static final BlockFlowValue RL_VALUE = new BlockFlowValue(AbstractTextParams.FLOW_RL);

	public static final BlockFlowValue LR_VALUE = new BlockFlowValue(AbstractTextParams.FLOW_LR);

	private final byte blockProgresion;

	private BlockFlowValue(byte blockProgresion) {
		this.blockProgresion = blockProgresion;
	}

	public short getValueType() {
		return TYPE_BLOCK_FLOW;
	}

	public byte getBlockProgression() {
		return this.blockProgresion;
	}

	public String toString() {
		switch (this.blockProgresion) {
		case AbstractTextParams.FLOW_TB:
			return "tb";

		case AbstractTextParams.FLOW_RL:
			return "rl";

		case AbstractTextParams.FLOW_LR:
			return "lr";

		default:
			throw new IllegalStateException();
		}
	}
}