package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum OverflowValue implements Value {
	VISIBLE_VALUE(OverflowMode.VISIBLE),

	HIDDEN_VALUE(OverflowMode.HIDDEN),

	// 2026-08-09まで両者のOverflowModeが入れ替わっていた(HIDDEN以外を
	// 区別する処理が無かったため実害なし)。クリップ導入を機に正した
	AUTO_VALUE(OverflowMode.AUTO),

	SCROLL_VALUE(OverflowMode.SCROLL);

	private final OverflowMode overflow;

	private OverflowValue(OverflowMode overflow) {
		this.overflow = overflow;
	}

	public OverflowMode getOverflow() {
		return this.overflow;
	}

	public String toString() {
		switch (this.overflow) {
		case OverflowMode.VISIBLE:
			return "visible";

		case OverflowMode.HIDDEN:
			return "hidden";

		case OverflowMode.SCROLL:
			return "scroll";

		case OverflowMode.AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}