package net.zamasoft.foliojet.impl.css.property.border;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;

/**
 * ボックスの4隅です。縦書き時の論理→物理の回転を担います。
 */
public enum Corner {
	TOP_LEFT("top-left"), TOP_RIGHT("top-right"), BOTTOM_RIGHT("bottom-right"), BOTTOM_LEFT("bottom-left");

	private final String text;

	private Corner(String text) {
		this.text = text;
	}

	public String text() {
		return this.text;
	}

	// 並び順: TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
	private static final Corner[] HTB_RL = { BOTTOM_LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT };

	private static final Corner[] HTB_LR = { BOTTOM_RIGHT, TOP_RIGHT, TOP_LEFT, BOTTOM_LEFT };

	private static final Corner[] VRL_TB = { TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_LEFT };

	/**
	 * 書字方向を考慮して、実際に参照すべき隅を返します(従来の「回転」処理)。
	 */
	public Corner resolve(CSSStyle style) {
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
