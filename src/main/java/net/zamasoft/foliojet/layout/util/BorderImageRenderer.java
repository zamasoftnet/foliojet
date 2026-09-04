package net.zamasoft.foliojet.layout.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.BorderImage;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@code border-image}の9スライス描画です(2026-08-30新設)。
 *
 * <p>
 * SPEC css-backgrounds-3 §6。画像を{@code border-image-slice}で9つの領域へ
 * 切り、四隅は伸縮せず、4辺と中央({@code fill}指定時のみ)を目的の矩形へ
 * 引き伸ばして描く。<b>画像が読めて実際に描けるときは、従来の
 * {@code border-style}描画を完全に置き換える</b>(仕様どおり)。
 *
 * <p>
 * {@code border-image-repeat}の{@code repeat}/{@code round}/{@code space}も
 * 実際にタイルする(2026-08-30)。{@code GC}には画像の部分矩形をタイルする
 * 基本操作が無い({@code Pattern}は画像全体をタイルする)ので、<b>目的の辺で
 * クリップしてから、スライス1枚ぶんの描画をタイルの数だけ繰り返す</b>。
 */
public final class BorderImageRenderer {
	@FunctionalInterface
	private interface Tile {
		void draw(GC gc, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh)
				throws GraphicsException;
	}

	public static final BorderImageRenderer INSTANCE = new BorderImageRenderer();

	private BorderImageRenderer() {
	}

	/**
	 * 描けたら真を返します。偽のときは呼び出し側が従来の境界線描画を続けます。
	 *
	 * @param x 境界ボックスの左上(x)
	 * @param y 境界ボックスの左上(y)
	 * @param w 境界ボックスの幅
	 * @param h 境界ボックスの高さ
	 */
	public boolean draw(final GC gc, final RectBorder border, final double x, final double y, final double w,
			final double h) throws GraphicsException {
		final BorderImage borderImage = border.getBorderImage();
		if (borderImage == null) {
			return false;
		}

		// 境界画像領域 = 境界ボックスをoutsetだけ外へ広げた矩形
		final double outTop = outset(borderImage.getOutset().top(), border.getTop());
		final double outRight = outset(borderImage.getOutset().right(), border.getRight());
		final double outBottom = outset(borderImage.getOutset().bottom(), border.getBottom());
		final double outLeft = outset(borderImage.getOutset().left(), border.getLeft());
		final double ax = x - outLeft;
		final double ay = y - outTop;
		final double aw = w + outLeft + outRight;
		final double ah = h + outTop + outBottom;
		if (aw <= 0 || ah <= 0) {
			return false;
		}

		final double iw, ih;
		final boolean paintSource;
		final Tile tile;
		if (borderImage.getSource() instanceof BorderImage.ImageSource imageSource) {
			final Image image = imageSource.image();
			iw = image.getWidth();
			ih = image.getHeight();
			paintSource = false;
			tile = (tileGc, sx, sy, sw, sh, dx, dy, dw, dh) ->
				slice(tileGc, image, sx, sy, sw, sh, dx, dy, dw, dh);
		} else {
			final PaintValue paint = ((BorderImage.PaintSource) borderImage.getSource()).paint();
			final Rectangle2D virtualSource = new Rectangle2D.Double(0, 0, aw, ah);
			iw = aw;
			ih = ah;
			paintSource = true;
			tile = (tileGc, sx, sy, sw, sh, dx, dy, dw, dh) ->
				paintSlice(tileGc, paint, virtualSource, sx, sy, sw, sh, dx, dy, dw, dh);
		}
		if (iw <= 0 || ih <= 0) {
			return false;
		}

		// スライス位置(画像座標系のpt)。合計が画像を超えたら比例縮小する
		final double[] sliceV = fitSlices(slice(borderImage.getSlice().top(), ih),
				slice(borderImage.getSlice().bottom(), ih), ih);
		final double[] sliceH = fitSlices(slice(borderImage.getSlice().left(), iw),
				slice(borderImage.getSlice().right(), iw), iw);

		// 描画先の枠幅。autoは画像ならスライス寸法、自然寸法の無いpaintならborder-width
		double wTop = width(borderImage.getWidth().top(), border.getTop(), ah,
				paintSource ? border.getTop().width : sliceV[0]);
		double wBottom = width(borderImage.getWidth().bottom(), border.getBottom(), ah,
				paintSource ? border.getBottom().width : sliceV[1]);
		double wLeft = width(borderImage.getWidth().left(), border.getLeft(), aw,
				paintSource ? border.getLeft().width : sliceH[0]);
		double wRight = width(borderImage.getWidth().right(), border.getRight(), aw,
				paintSource ? border.getRight().width : sliceH[1]);
		// SPEC §6.3: 対辺の和が領域を超えたら、両軸で最小の比率を4辺すべてへ掛ける
		final double f = Math.min(ratio(aw, wLeft + wRight), ratio(ah, wTop + wBottom));
		if (f < 1) {
			wTop *= f;
			wBottom *= f;
			wLeft *= f;
			wRight *= f;
		}

		final double midW = aw - wLeft - wRight;
		final double midH = ah - wTop - wBottom;
		final double srcMidW = iw - sliceH[0] - sliceH[1];
		final double srcMidH = ih - sliceV[0] - sliceV[1];

		// 断片化(改ページ・段抜け)で落ちた辺は描かない
		final boolean top = borderImage.hasTop();
		final boolean right = borderImage.hasRight();
		final boolean bottom = borderImage.hasBottom();
		final boolean left = borderImage.hasLeft();

		// 四隅(伸縮しない指定でも目的の枠幅へは合わせる。仕様どおり)
		if (top && left) {
			tile.draw(gc, 0, 0, sliceH[0], sliceV[0], ax, ay, wLeft, wTop);
		}
		if (top && right) {
			tile.draw(gc, iw - sliceH[1], 0, sliceH[1], sliceV[0], ax + aw - wRight, ay, wRight, wTop);
		}
		if (bottom && left) {
			tile.draw(gc, 0, ih - sliceV[1], sliceH[0], sliceV[1], ax, ay + ah - wBottom, wLeft, wBottom);
		}
		if (bottom && right) {
			tile.draw(gc, iw - sliceH[1], ih - sliceV[1], sliceH[1], sliceV[1], ax + aw - wRight,
					ay + ah - wBottom, wRight, wBottom);
		}
		// タイル1枚の自然な寸法。反対軸を枠幅へ合わせたときの寸法になる
		// (SPEC css-backgrounds-3 §6.5: まず交差方向を枠幅へ合わせ、
		// それから送り方向へ敷く)
		final BorderImage.Repeat hRepeat = borderImage.getHorizontalRepeat();
		final BorderImage.Repeat vRepeat = borderImage.getVerticalRepeat();
		final double tileTop = natural(srcMidW, sliceV[0], wTop);
		final double tileBottom = natural(srcMidW, sliceV[1], wBottom);
		final double tileLeft = natural(srcMidH, sliceH[0], wLeft);
		final double tileRight = natural(srcMidH, sliceH[1], wRight);

		// 4辺(送り方向だけタイルし、交差方向は枠幅いっぱいへ伸縮する)
		if (top) {
			this.tiled(gc, tile, sliceH[0], 0, srcMidW, sliceV[0], ax + wLeft, ay, midW, wTop, hRepeat,
					BorderImage.Repeat.STRETCH, tileTop, wTop);
		}
		if (bottom) {
			this.tiled(gc, tile, sliceH[0], ih - sliceV[1], srcMidW, sliceV[1], ax + wLeft, ay + ah - wBottom,
					midW, wBottom, hRepeat, BorderImage.Repeat.STRETCH, tileBottom, wBottom);
		}
		if (left) {
			this.tiled(gc, tile, 0, sliceV[0], sliceH[0], srcMidH, ax, ay + wTop, wLeft, midH,
					BorderImage.Repeat.STRETCH, vRepeat, wLeft, tileLeft);
		}
		if (right) {
			this.tiled(gc, tile, iw - sliceH[1], sliceV[0], sliceH[1], srcMidH, ax + aw - wRight, ay + wTop,
					wRight, midH, BorderImage.Repeat.STRETCH, vRepeat, wRight, tileRight);
		}
		// 中央(fill指定時のみ)。両軸ともタイルし、寸法は上辺・左辺の倍率に従う
		if (borderImage.isFill()) {
			this.tiled(gc, tile, sliceH[0], sliceV[0], srcMidW, srcMidH, ax + wLeft, ay + wTop, midW, midH,
					hRepeat, vRepeat, tileTop, tileLeft);
		}
		return true;
	}

	/**
	 * タイル1枚の送り方向の寸法です。交差方向を枠幅{@code crossDst}へ
	 * 合わせたときの倍率を、送り方向の元寸法{@code srcAlong}へ掛けます。
	 * 交差方向のスライスが0なら倍率が決まらないので0(=伸縮扱い)を返します。
	 */
	private static double natural(final double srcAlong, final double srcCross, final double crossDst) {
		return srcCross <= 0 ? 0 : srcAlong * (crossDst / srcCross);
	}

	/**
	 * 画像の部分矩形を、描画先へタイルして描きます。
	 *
	 * <p>
	 * {@code stretch}(既定)のときはタイルせず、そのまま
	 * {@link #slice}へ落とす——<b>既定の出力を1ビットも変えないため</b>に、
	 * 分岐だけでなく描画の呼び出しも同じ経路にしてある。
	 */
	private void tiled(final GC gc, final Tile tile, final double sx, final double sy, final double sw,
			final double sh, final double dx, final double dy, final double dw, final double dh,
			final BorderImage.Repeat hRepeat, final BorderImage.Repeat vRepeat, final double naturalW,
			final double naturalH) throws GraphicsException {
		if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
			return;
		}
		final double[][] xs = axisTiles(dw, naturalW, hRepeat);
		final double[][] ys = axisTiles(dh, naturalH, vRepeat);
		if (xs.length == 0 || ys.length == 0) {
			// space で1枚も入らなかった。SPEC どおりその辺は描かない
			return;
		}
		if (xs.length == 1 && ys.length == 1 && xs[0][0] == 0 && ys[0][0] == 0 && xs[0][1] == dw
				&& ys[0][1] == dh) {
			tile.draw(gc, sx, sy, sw, sh, dx, dy, dw, dh);
			return;
		}
		// repeat は中央揃えで敷き詰めるので両端がはみ出す。外側で切る
		try (final var gcState = gc.begin()) {
			gc.clip(new Rectangle2D.Double(dx, dy, dw, dh));
			for (final double[] xt : xs) {
				for (final double[] yt : ys) {
					tile.draw(gc, sx, sy, sw, sh, dx + xt[0], dy + yt[0], xt[1], yt[1]);
				}
			}
		}
	}

	/**
	 * 1軸ぶんのタイルの位置と寸法({@code {開始, 寸法}}の並び)です。
	 *
	 * <p>
	 * SPEC css-backgrounds-3 §6.5:
	 * <ul>
	 * <li>{@code repeat} — 自然な寸法のまま<b>中央揃え</b>で敷き詰め、
	 * 両端は切れる</li>
	 * <li>{@code round} — 整数枚に収まるよう寸法を調節する(切れない)</li>
	 * <li>{@code space} — 自然な寸法のまま整数枚だけ置き、余りを均等な
	 * 隙間に配る。<b>1枚も入らなければその辺は描かない</b></li>
	 * </ul>
	 */
	private static double[][] axisTiles(final double available, final double natural,
			final BorderImage.Repeat repeat) {
		if (repeat == BorderImage.Repeat.STRETCH || natural <= 0 || available <= 0) {
			return new double[][] { { 0, available } };
		}
		switch (repeat) {
		case ROUND: {
			final int n = Math.max(1, (int) Math.round(available / natural));
			final double size = available / n;
			final double[][] out = new double[n][];
			for (int i = 0; i < n; ++i) {
				out[i] = new double[] { i * size, size };
			}
			return out;
		}
		case SPACE: {
			final int n = (int) Math.floor(available / natural);
			if (n <= 0) {
				return new double[0][];
			}
			final double gap = (available - n * natural) / (n + 1);
			final double[][] out = new double[n][];
			for (int i = 0; i < n; ++i) {
				out[i] = new double[] { gap + i * (natural + gap), natural };
			}
			return out;
		}
		default: {
			// 中央揃え。両端の切れる分を見込んで1枚多く置く
			final int n = (int) Math.ceil(available / natural) + 1;
			final double start = (available - n * natural) / 2;
			final double[][] out = new double[n][];
			for (int i = 0; i < n; ++i) {
				out[i] = new double[] { start + i * natural, natural };
			}
			return out;
		}
		}
	}

	/**
	 * 画像の部分矩形{@code (sx, sy, sw, sh)}を、描画先{@code (dx, dy, dw, dh)}
	 * いっぱいに引き伸ばして描きます。
	 *
	 * <p>
	 * {@code GC}には画像の部分矩形を描く操作がない(常に画像全体を
	 * {@code (0,0,幅,高さ)}へ描く)ので、<b>描画先でクリップしてから、部分矩形が
	 * そこへ載るように平行移動・拡大する</b>——画像の残りはクリップの外へ出る。
	 * {@code object-fit}の実装({@code AbstractReplacedBox})と同じ流儀。
	 */
	private static void slice(final GC gc, final Image image, final double sx, final double sy, final double sw,
			final double sh, final double dx, final double dy, final double dw, final double dh)
			throws GraphicsException {
		if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
			return;
		}
		final double scaleX = dw / sw;
		final double scaleY = dh / sh;
		try (final var gcState = gc.begin()) {
			gc.clip(new Rectangle2D.Double(dx, dy, dw, dh));
			gc.transform(new AffineTransform(scaleX, 0, 0, scaleY, dx - sx * scaleX, dy - sy * scaleY));
			gc.drawImage(image);
		}
	}

	/** 仮想画像の部分矩形を、描画先へ引き伸ばしてpaintで塗ります。 */
	private static void paintSlice(final GC gc, final PaintValue paint, final Rectangle2D virtualSource,
			final double sx, final double sy, final double sw, final double sh, final double dx, final double dy,
			final double dw, final double dh) throws GraphicsException {
		if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
			return;
		}
		final AffineTransform at = AffineTransform.getTranslateInstance(dx, dy);
		at.scale(dw / sw, dh / sh);
		at.translate(-sx, -sy);
		try (final var gcState = gc.begin()) {
			gc.clip(new Rectangle2D.Double(dx, dy, dw, dh));
			gc.transform(at);
			paint.fill(gc, new Rectangle2D.Double(sx, sy, sw, sh), virtualSource);
		}
	}

	/** {@code border-image-outset}の1辺。数値は対応する境界線幅の倍数。 */
	private static double outset(final BorderImage.Component component, final Border side) {
		return switch (component.unit()) {
		case NUMBER -> component.absolute() * side.width;
		case ABSOLUTE -> component.absolute();
		default -> 0;
		};
	}

	/** {@code border-image-slice}の1辺を画像座標系のptで返します。 */
	private static double slice(final BorderImage.Component component, final double imageSize) {
		final double value = switch (component.unit()) {
		case ABSOLUTE -> component.absolute();
		case RELATIVE -> component.ratio() * imageSize;
		case MIXED -> component.absolute() + component.ratio() * imageSize;
		default -> 0;
		};
		return Math.max(0, Math.min(imageSize, value));
	}

	/**
	 * 対辺のスライスの和が画像を超えないよう比例縮小します。
	 *
	 * <p>
	 * SPEC上は重なった場合「中央が空になる」だけで縮小はしないが、そうすると
	 * 隅の領域が重なって二重に描かれる。比例縮小なら中央が0になる点は同じで、
	 * 重なりが起きない(近似)。
	 */
	private static double[] fitSlices(final double a, final double b, final double imageSize) {
		final double sum = a + b;
		if (sum <= imageSize || sum == 0) {
			return new double[] { a, b };
		}
		final double f = imageSize / sum;
		return new double[] { a * f, b * f };
	}

	/** {@code border-image-width}の1辺。数値は対応する境界線幅の倍数、autoはスライス寸法。 */
	private static double width(final BorderImage.Component component, final Border side, final double areaSize,
			final double sliceSize) {
		return switch (component.unit()) {
		case NUMBER -> component.absolute() * side.width;
		case ABSOLUTE -> component.absolute();
		case RELATIVE -> component.ratio() * areaSize;
		case MIXED -> component.absolute() + component.ratio() * areaSize;
		case AUTO -> sliceSize;
		};
	}

	private static double ratio(final double available, final double used) {
		return used <= available || used <= 0 ? 1 : available / used;
	}
}
