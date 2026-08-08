package net.zamasoft.foliojet.layout.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

public abstract class AbstractDrawable implements Drawable {
	protected final Shape clip;
	protected final PageBox pageBox;
	protected final float opacity;
	protected final AffineTransform transform;

	public AbstractDrawable(final PageBox pageBox, final Shape clip, final float opacity,
			final AffineTransform transform) {
		this.pageBox = pageBox;
		this.clip = clip;
		this.opacity = opacity;
		this.transform = transform;
	}

	/**
	 * 表示リストダンプ用に、非恒等のGC変換を{@code describe}文字列へ
	 * 追記します(2026-08-08)。ダンプの座標はGC変換前の値のため、
	 * transformを含む回帰はこれが無いとgoldenに一切現れない
	 * (ParamsFieldsの%translate脱落を10日間素通りさせた穴)。
	 * 変換を使わない既存goldenは不変。
	 */
	protected final String describeTransform(final String base) {
		if (this.transform.isIdentity()) {
			return base;
		}
		final double[] m = new double[6];
		this.transform.getMatrix(m);
		return base + String.format(java.util.Locale.ROOT, " tf=[%.2f %.2f %.2f %.2f %.2f %.2f]", m[0], m[1], m[2],
				m[3], m[4], m[5]);
	}

	public final void draw(GC gc, double x, double y) throws GraphicsException {
		GC.State state = null;
		if (this.clip != null || !this.transform.isIdentity()) {
			state = gc.begin();
			if (this.clip != null) {
				gc.clip(this.clip);
			}
			if (!this.transform.isIdentity()) {
				gc.transform(this.transform);
			}
		}

		/* NoAndroid begin */
		final GC xgc;
		final GroupImageGC ggc;
		float alpha = gc.getFillAlpha();
		if (this.opacity != 1f) {
			// 透明化開始
			xgc = gc;
			ggc = gc.createGroupImage(this.pageBox.getWidth(), this.pageBox.getHeight());
			gc = ggc;
		} else {
			xgc = ggc = null;
			gc.setFillAlpha(this.opacity);
		}
		/* NoAndroid end */

		this.innerDraw(gc, x, y);

		/* NoAndroid begin */
		if (this.opacity != 1f) {
			// 透明化終了
			Image gi = ggc.finish();
			xgc.setFillAlpha(this.opacity);
			xgc.drawImage(gi);
			xgc.setFillAlpha(alpha);
			gc = xgc;
		} else {
			gc.setFillAlpha(alpha);
		}
		/* NoAndroid end */

		if (state != null) {
			state.close();
		}
	}

	public abstract void innerDraw(GC gc, double x, double y) throws GraphicsException;
}
