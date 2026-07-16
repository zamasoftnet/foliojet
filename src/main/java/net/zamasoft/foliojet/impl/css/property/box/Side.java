package net.zamasoft.foliojet.impl.css.property.box;

import net.zamasoft.foliojet.style.box.params.WritingMode;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * ボックスの4辺です。縦書き時の論理→物理の回転を担います。
 */
public enum Side {
	TOP("top"), RIGHT("right"), BOTTOM("bottom"), LEFT("left");

	private final String text;

	private Side(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}

	// 並び順: TOP, RIGHT, BOTTOM, LEFT
	private static final Side[] HTB_RL = { LEFT, TOP, RIGHT, BOTTOM };

	private static final Side[] HTB_LR = { LEFT, BOTTOM, RIGHT, TOP };

	private static final Side[] VRL_TB = { RIGHT, BOTTOM, LEFT, TOP };

	/**
	 * 書字方向を考慮して、実際に参照すべき辺を返します(従来の「回転」処理)。
	 */
	public Side resolve(CSSStyle style) {
		switch (CSSJDirectionMode.get(style)) {
		case CSSJDirectionModeValue.PHYSICAL:
			return this;
		case CSSJDirectionModeValue.HORIZONTAL_TB:
			switch (BlockFlow.get(style)) {
			case WritingMode.RL:
				return HTB_RL[this.ordinal()];
			case WritingMode.LR:
				return HTB_LR[this.ordinal()];
			default:
				return this;
			}
		case CSSJDirectionModeValue.VERTICAL_RL:
			switch (BlockFlow.get(style)) {
			case WritingMode.TB:
				return VRL_TB[this.ordinal()];
			default:
				return this;
			}
		default:
			throw new IllegalStateException();
		}
	}
}
