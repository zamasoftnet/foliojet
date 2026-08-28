package net.zamasoft.foliojet.layout.util;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.DoubleFunction;

import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.BoxShadow;
import net.zamasoft.foliojet.layout.box.params.Outline;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * box-shadow と outline の描画です(2026-08-29)。
 *
 * <p>
 * 描画順は{@link net.zamasoft.foliojet.layout.part.AbsoluteRectFrame#draw}が
 * 決める: 外側の影 → 背景 → 内側の影 → 境界 → アウトライン。
 * </p>
 *
 * <p>
 * <b>ぼかしの近似。</b>PDFにはぼかしのプリミティブが無い(ソフトマスク+
 * 画像で作れるが、ベクタ出力・ファイルサイズ・決定性の全てで損)。そこで
 * 影の縁を{@link #BLUR_STEPS}個の同心の塗りで階段状に再現する。各段は
 * ガウス分布の分位点の位置(σ=blur/2、Chrome/Skiaと同じ換算)に置き、
 * 各段のアルファは全段が重なった中心で指定色のアルファに一致するよう
 * {@code 1-(1-α)^(1/N)}にする。単一の半透明帯より縁の減衰が滑らかで、
 * カードによくある {@code 0 2px 8px rgba(0,0,0,.15)} でChromeの見た目に
 * 近い(段差は12段・α=0.15なら1段あたり約1.3%で肉眼では見えない)。
 * 広がりの外縁は±1.73σ≒±0.87×blurで、Chromeの見た目の裾(≒blur)より
 * わずかに詰まる。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class BoxDecorationRenderer {
	/**
	 * ぼかしの各段の縁の位置(σ単位)。N=12の標準正規分布の分位点
	 * ((k+0.5)/N)を外側から並べたもの。外側ほど薄くなる階段の縁が、
	 * ガウス減衰の等確率区間に対応する。8段では濃い影(α=.5)の4倍拡大で
	 * 段差が見えたので12段にした(1段あたりα=.15なら約1.3%、α=.5でも約6%)。
	 */
	private static final double[] BLUR_STEPS = { 1.7317, 1.1503, 0.8122, 0.5485, 0.3186, 0.1046, -0.1046, -0.3186,
			-0.5485, -0.8122, -1.1503, -1.7317 };

	private BoxDecorationRenderer() {
		// unused
	}

	/**
	 * 外側の影を描きます。背景より前に呼ぶこと。
	 * 影は境界箱の外だけに描く(CSS Backgrounds 3 §7.1: 箱の背景が透明でも
	 * 影は箱の下に透けない)ので、even-oddで境界箱を抜いたクリップを掛ける。
	 */
	public static void drawOuterShadows(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final BoxShadow[] shadows = frame.shadows;
		if (shadows == null || w <= 0 || h <= 0) {
			return;
		}
		double extent = 0;
		for (final BoxShadow s : shadows) {
			if (!s.inset) {
				extent = Math.max(extent, Math.abs(s.x) + Math.abs(s.y) + Math.max(0, s.spread) + s.blur);
			}
		}
		if (extent <= 0) {
			// 外側の影が無いか、あっても箱の下に隠れる(広がりも偏りも無い)
			return;
		}
		final Radius[] radii = resolvedRadii(frame.border, w, h);
		final Path2D.Double clip = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		clip.append(new Rectangle2D.Double(x - extent - 1, y - extent - 1, w + extent * 2 + 2, h + extent * 2 + 2),
				false);
		clip.append(roundedShape(x, y, w, h, radii), false);
		try (final var state = gc.begin()) {
			gc.clip(clip);
			// 先頭の影が最前面なので後ろから描く
			for (int i = shadows.length - 1; i >= 0; --i) {
				final BoxShadow s = shadows[i];
				if (s.inset) {
					continue;
				}
				fillLayers(gc, s, d -> expandedShape(x + s.x, y + s.y, w, h, radii, s.spread + d));
			}
		}
	}

	/**
	 * 内側の影を描きます。背景の後、境界の前に呼ぶこと。パディング箱に
	 * クリップし、パディング箱から「広がりぶん縮めてずらした穴」を
	 * even-oddで抜いた帯を塗る。
	 */
	public static void drawInsetShadows(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final BoxShadow[] shadows = frame.shadows;
		if (shadows == null) {
			return;
		}
		boolean any = false;
		for (final BoxShadow s : shadows) {
			any |= s.inset;
		}
		if (!any) {
			return;
		}
		final RectBorder border = frame.border;
		final double bl = border.getLeft().width, bt = border.getTop().width;
		final double br = border.getRight().width, bb = border.getBottom().width;
		final double px = x + bl, py = y + bt, pw = w - bl - br, ph = h - bt - bb;
		if (pw <= 0 || ph <= 0) {
			return;
		}
		// パディング箱の角丸は境界箱の半径から境界幅を引いたもの
		// (CSS Backgrounds 3 §5.2)
		final Radius[] outer = resolvedRadii(border, w, h);
		final Radius[] radii = { shrink(outer[0], bl, bt), shrink(outer[1], br, bt), shrink(outer[2], bl, bb),
				shrink(outer[3], br, bb) };
		final Shape paddingShape = roundedShape(px, py, pw, ph, radii);
		try (final var state = gc.begin()) {
			gc.clip(paddingShape);
			for (int i = shadows.length - 1; i >= 0; --i) {
				final BoxShadow s = shadows[i];
				if (!s.inset) {
					continue;
				}
				fillLayers(gc, s, d -> {
					final Shape hole = expandedShape(px + s.x, py + s.y, pw, ph, radii, -(s.spread + d));
					if (hole == null) {
						// 穴が潰れた=パディング箱全面が影
						return paddingShape;
					}
					final Path2D.Double band = new Path2D.Double(Path2D.WIND_EVEN_ODD);
					band.append(paddingShape, false);
					band.append(hole, false);
					return band;
				});
			}
		}
	}

	/**
	 * アウトラインを描きます。境界の後に呼ぶこと。境界辺からoffset+幅だけ
	 * 外へ広げた矩形に、4辺同じ線の{@link RectBorder}として描く(点線・
	 * 二重線・溝などの線種は境界の描画をそのまま流用)。角丸は境界の半径に
	 * 同じ距離を足す(Chromeと同じ)。
	 */
	public static void drawOutline(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final Outline outline = frame.outline;
		if (outline == null) {
			return;
		}
		final Border line = outline.border;
		final double d = outline.offset + line.width;
		final double ow = w + d * 2, oh = h + d * 2;
		if (ow <= 0 || oh <= 0) {
			return;
		}
		final Radius[] radii = resolvedRadii(frame.border, w, h);
		final RectBorder rect = RectBorder.create(line, line, line, line, grow(radii[0], d), grow(radii[1], d),
				grow(radii[2], d), grow(radii[3], d));
		try (final var state = gc.begin()) {
			BorderRenderer.INSTANCE.drawRectBorder(gc, rect, x - d, y - d, ow, oh);
		}
	}

	/**
	 * 1つの影を段階塗りします。{@code shapeAt}は縁の位置のずれ(外向き正)から
	 * 塗る形を返す(nullなら潰れていて塗らない)。
	 */
	private static void fillLayers(GC gc, BoxShadow s, DoubleFunction<Shape> shapeAt) throws GraphicsException {
		final float alpha = s.color.getAlpha();
		if (alpha <= 0) {
			return;
		}
		try (final var state = gc.begin()) {
			gc.setFillPaint(s.color);
			if (s.blur <= 0) {
				gc.setFillAlpha(alpha);
				final Shape shape = shapeAt.apply(0);
				if (shape != null) {
					gc.fill(shape);
				}
				return;
			}
			final int n = BLUR_STEPS.length;
			// 全段が重なる中心で合成アルファがalphaになる1段あたりの値
			gc.setFillAlpha((float) (1 - Math.pow(1 - alpha, 1.0 / n)));
			final double sigma = s.blur / 2;
			for (int k = 0; k < n; ++k) {
				final Shape shape = shapeAt.apply(BLUR_STEPS[k] * sigma);
				if (shape != null) {
					gc.fill(shape);
				}
			}
		}
	}

	private static Radius[] resolvedRadii(RectBorder border, double w, double h) {
		return new Radius[] { border.getTopLeft().resolve(w, h), border.getTopRight().resolve(w, h),
				border.getBottomLeft().resolve(w, h), border.getBottomRight().resolve(w, h) };
	}

	private static Shape roundedShape(double x, double y, double w, double h, Radius[] radii) {
		return BorderRenderer.INSTANCE.getRoundedShape(x, y, w, h, radii[0], radii[1], radii[2], radii[3]);
	}

	/**
	 * 矩形を{@code d}だけ外へ広げ(負なら縮め)、角丸半径も同じだけ増減した
	 * 形を返します。縮めて潰れたらnull。
	 */
	private static Shape expandedShape(double x, double y, double w, double h, Radius[] radii, double d) {
		final double nw = w + d * 2, nh = h + d * 2;
		if (nw <= 0 || nh <= 0) {
			return null;
		}
		return BorderRenderer.INSTANCE.getRoundedShape(x - d, y - d, nw, nh, grow(radii[0], d), grow(radii[1], d),
				grow(radii[2], d), grow(radii[3], d));
	}

	/** 半径を{@code d}だけ増減します。直角(0)は直角のまま。 */
	private static Radius grow(Radius r, double d) {
		if (r.hr <= 0 && r.vr <= 0) {
			return Radius.ZERO_RADIUS;
		}
		return Radius.create(Math.max(0, r.hr + d), Math.max(0, r.vr + d));
	}

	/** 半径を水平・垂直に別々の量だけ減らします。 */
	private static Radius shrink(Radius r, double dh, double dv) {
		if (r.hr <= 0 && r.vr <= 0) {
			return Radius.ZERO_RADIUS;
		}
		return Radius.create(Math.max(0, r.hr - dh), Math.max(0, r.vr - dv));
	}
}
