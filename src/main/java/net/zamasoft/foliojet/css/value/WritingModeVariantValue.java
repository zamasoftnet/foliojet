package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

/**
 * 書字方向の字形回転種別を運ぶ内部値です。
 */
public enum WritingModeVariantValue implements Value {
	NORMAL_VALUE(WritingModeVariant.NORMAL),

	SIDEWAYS_RL_VALUE(WritingModeVariant.SIDEWAYS_CW),

	SIDEWAYS_LR_VALUE(WritingModeVariant.SIDEWAYS_CCW);

	private final WritingModeVariant writingModeVariant;

	private WritingModeVariantValue(final WritingModeVariant writingModeVariant) {
		this.writingModeVariant = writingModeVariant;
	}

	public WritingModeVariant getWritingModeVariant() {
		return this.writingModeVariant;
	}

	@Override
	public String toString() {
		return switch (this.writingModeVariant) {
		case NORMAL -> "normal";
		case SIDEWAYS_CW -> "sideways-rl";
		case SIDEWAYS_CCW -> "sideways-lr";
		};
	}
}
