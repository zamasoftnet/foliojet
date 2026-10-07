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
 * Nine-slice rendering of {@code border-image} (added 2026-08-30).
 *
 * <p>
 * SPEC css-backgrounds-3 §6. Splits the image into nine regions with {@code border-image-slice},
 * keeps the four corners unstretched, and stretches the four edges and center
 * (only when {@code fill} is specified) to their destination rectangles.
 * <b>When the image can be loaded and actually drawn, it completely replaces conventional
 * {@code border-style} rendering</b> (as specified).
 *
 * <p>
 * {@code repeat}/{@code round}/{@code space} in {@code border-image-repeat} also actually tile
 * (2026-08-30). {@code GC} has no primitive for tiling a subrectangle of an image
 * ({@code Pattern} tiles the entire image), so <b>clip to the destination edge, then repeat
 * drawing one slice for each tile</b>.
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
	 * Returns true if drawn. If false, the caller continues with conventional border rendering.
	 *
	 * @param x top-left x of the border box
	 * @param y top-left y of the border box
	 * @param w width of the border box
	 * @param h height of the border box
	 */
	public boolean draw(final GC gc, final RectBorder border, final double x, final double y, final double w,
			final double h) throws GraphicsException {
		final BorderImage borderImage = border.getBorderImage();
		if (borderImage == null) {
			return false;
		}

		// Border image area = the border box expanded outward by outset
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

		// Slice positions (pt in image coordinates). Scale proportionally if their sum exceeds the image
		final double[] sliceV = fitSlices(slice(borderImage.getSlice().top(), ih),
				slice(borderImage.getSlice().bottom(), ih), ih);
		final double[] sliceH = fitSlices(slice(borderImage.getSlice().left(), iw),
				slice(borderImage.getSlice().right(), iw), iw);

		// Border widths: auto uses the image slice size, or border-width for paint without natural dimensions
		double wTop = width(borderImage.getWidth().top(), border.getTop(), ah,
				paintSource ? border.getTop().width : sliceV[0]);
		double wBottom = width(borderImage.getWidth().bottom(), border.getBottom(), ah,
				paintSource ? border.getBottom().width : sliceV[1]);
		double wLeft = width(borderImage.getWidth().left(), border.getLeft(), aw,
				paintSource ? border.getLeft().width : sliceH[0]);
		double wRight = width(borderImage.getWidth().right(), border.getRight(), aw,
				paintSource ? border.getRight().width : sliceH[1]);
		// SPEC §6.3: if opposite sides exceed the area, multiply all four by the smallest ratio across both axes
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

		// Do not draw edges removed by fragmentation (page breaks or breaks through columns)
		final boolean top = borderImage.hasTop();
		final boolean right = borderImage.hasRight();
		final boolean bottom = borderImage.hasBottom();
		final boolean left = borderImage.hasLeft();

		// Four corners (even without stretching specified, fit the destination border width, as required by the spec)
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
		// Natural size of one tile, after fitting the cross axis to the border width
		// (SPEC css-backgrounds-3 §6.5: first fit the cross direction to the border width,
		// then tile in the advance direction)
		final BorderImage.Repeat hRepeat = borderImage.getHorizontalRepeat();
		final BorderImage.Repeat vRepeat = borderImage.getVerticalRepeat();
		final double tileTop = natural(srcMidW, sliceV[0], wTop);
		final double tileBottom = natural(srcMidW, sliceV[1], wBottom);
		final double tileLeft = natural(srcMidH, sliceH[0], wLeft);
		final double tileRight = natural(srcMidH, sliceH[1], wRight);

		// Four edges (tile only in the advance direction; stretch across the full border width)
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
		// Center (only for fill). Tile on both axes, using the top/left edge scale factors for size
		if (borderImage.isFill()) {
			this.tiled(gc, tile, sliceH[0], sliceV[0], srcMidW, srcMidH, ax + wLeft, ay + wTop, midW, midH,
					hRepeat, vRepeat, tileTop, tileLeft);
		}
		return true;
	}

	/**
	 * Size of one tile in the advance direction. Multiplies the original advance-direction size
	 * {@code srcAlong} by the scale factor that fits the cross direction to border width {@code crossDst}.
	 * Returns zero (= stretch) if the cross-axis slice size is zero, since the scale cannot be determined.
	 */
	private static double natural(final double srcAlong, final double srcCross, final double crossDst) {
		return srcCross <= 0 ? 0 : srcAlong * (crossDst / srcCross);
	}

	/**
	 * Tiles a subrectangle of the image across the destination.
	 *
	 * <p>
	 * For {@code stretch} (the default), does not tile and goes directly to {@link #slice}.
	 * To <b>leave the default output bit-for-bit unchanged</b>, both the branch and the drawing call
	 * use the same path.
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
			// No tile fits with space. Do not draw that edge, as specified
			return;
		}
		if (xs.length == 1 && ys.length == 1 && xs[0][0] == 0 && ys[0][0] == 0 && xs[0][1] == dw
				&& ys[0][1] == dh) {
			tile.draw(gc, sx, sy, sw, sh, dx, dy, dw, dh);
			return;
		}
		// repeat tiles from the center, so both ends overflow. Clip at the outside
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
	 * Tile positions and sizes along one axis (a sequence of {@code {start, size}}).
	 *
	 * <p>
	 * SPEC css-backgrounds-3 §6.5:
	 * <ul>
	 * <li>{@code repeat} — tiles at natural size, <b>centered</b>, clipping both ends</li>
	 * <li>{@code round} — adjusts size to fit an integer number of tiles (no clipping)</li>
	 * <li>{@code space} — places an integer number of natural-size tiles and distributes the
	 * remainder as equal gaps. <b>If no tile fits, do not draw that edge</b></li>
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
			// Center the tiles. Add one extra tile to account for clipping at both ends
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
	 * Draws the image subrectangle {@code (sx, sy, sw, sh)} stretched to fill the destination
	 * {@code (dx, dy, dw, dh)}.
	 *
	 * <p>
	 * {@code GC} has no operation for drawing an image subrectangle (it always draws the whole image
	 * at {@code (0,0,width,height)}), so <b>clip at the destination, then translate and scale to place
	 * the subrectangle there</b>; the rest of the image lies outside the clip.
	 * Uses the same approach as {@code object-fit} ({@code AbstractReplacedBox}).
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

	/** Stretches a virtual image subrectangle to the destination and fills it with paint. */
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

	/** One side of {@code border-image-outset}. Numbers multiply the corresponding border width. */
	private static double outset(final BorderImage.Component component, final Border side) {
		return switch (component.unit()) {
		case NUMBER -> component.absolute() * side.width;
		case ABSOLUTE -> component.absolute();
		default -> 0;
		};
	}

	/** Returns one side of {@code border-image-slice} in pt in image coordinates. */
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
	 * Scales proportionally so the sum of opposite slices does not exceed the image.
	 *
	 * <p>
	 * The SPEC only makes the center empty on overlap, without scaling, but that makes corner regions
	 * overlap and draw twice. Proportional scaling likewise makes the center zero, while avoiding
	 * overlap (approximation).
	 */
	private static double[] fitSlices(final double a, final double b, final double imageSize) {
		final double sum = a + b;
		if (sum <= imageSize || sum == 0) {
			return new double[] { a, b };
		}
		final double f = imageSize / sum;
		return new double[] { a * f, b * f };
	}

	/**
	 * One side of {@code border-image-width}. Numbers multiply the corresponding border width;
	 * auto uses slice size.
	 */
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
