package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * box-shadow の使用値1つ分です(2026-08-29)。全て絶対長。
 * 描画は{@link net.zamasoft.foliojet.layout.util.BoxDecorationRenderer}。
 *
 * @author MIYABE Tatsuhiko
 */
public final class BoxShadow {
	public final double x, y;

	/** ぼかし半径(0以上)と広がり(負可)。 */
	public final double blur, spread;

	/** 影の色(アルファ込み)。 */
	public final Color color;

	/** trueなら内側の影(パディング箱の内側に描く)。 */
	public final boolean inset;

	public BoxShadow(double x, double y, double blur, double spread, Color color, boolean inset) {
		this.x = x;
		this.y = y;
		this.blur = blur;
		this.spread = spread;
		this.color = color;
		this.inset = inset;
	}

	public String toString() {
		return "[x=" + this.x + ",y=" + this.y + ",blur=" + this.blur + ",spread=" + this.spread + ",inset="
				+ this.inset + "]";
	}
}
