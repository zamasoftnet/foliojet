package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.WritingMode;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BlockFlowValue implements Value {
	TB_VALUE(WritingMode.TB),

	RL_VALUE(WritingMode.RL),

	LR_VALUE(WritingMode.LR);

	private final WritingMode writingMode;

	private BlockFlowValue(WritingMode writingMode) {
		this.writingMode = writingMode;
	}

	public WritingMode getWritingMode() {
		return this.writingMode;
	}

	public String toString() {
		switch (this.writingMode) {
		case TB:
			return "tb";

		case RL:
			return "rl";

		case LR:
			return "lr";

		default:
			throw new IllegalStateException();
		}
	}
}
