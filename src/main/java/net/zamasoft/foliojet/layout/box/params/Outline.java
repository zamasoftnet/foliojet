package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * outline の使用値です(2026-08-29)。境界辺の外側{@link #offset}だけ離れた
 * 位置に、{@link #border}のスタイル・幅・色で描く枠。レイアウトには影響
 * しない(CSS UI 3 §4)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Outline {
	/** 4辺共通の線。style=NONEなら不可視。 */
	public final Border border;

	/** 境界辺からアウトライン内縁までの距離。負なら境界の内側へ入る。 */
	public final double offset;

	/**
	 * 可視なアウトラインを作ります。描いても見えないもの(none・幅0・
	 * transparent)はnullを返す。
	 */
	public static Outline create(short style, double width, Color color, double offset) {
		final Border border = Border.create(style, width, color);
		if (!border.isVisible()) {
			return null;
		}
		return new Outline(border, offset);
	}

	private Outline(Border border, double offset) {
		this.border = border;
		this.offset = offset;
	}

	public String toString() {
		return "[border=" + this.border + ",offset=" + this.offset + "]";
	}
}
