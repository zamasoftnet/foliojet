package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BlockFlowValue implements Value {
	TB_VALUE(AbstractTextParams.FLOW_TB),

	RL_VALUE(AbstractTextParams.FLOW_RL),

	LR_VALUE(AbstractTextParams.FLOW_LR);

	private final byte blockProgresion;

	private BlockFlowValue(byte blockProgresion) {
		this.blockProgresion = blockProgresion;
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