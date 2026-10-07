package net.zamasoft.foliojet.layout.box.params;

/**
 * Immutable parameters combining a float's {@code shape-outside}, {@code shape-margin}, and {@code
 * shape-image-threshold} (css-shapes-1, added 2026-08-29).
 *
 * <p>
 * Carried on {@link FloatPos}, written only once during construction. The exclusion-area snapshot in {@code
 * BlockBuilder} assumes that it does not reread {@code FloatPos} after adding it to the ledger, so all fields here
 * are final and cannot be changed later. {@code FloatShapeResolver} resolves the actual exclusion shape once the
 * placed float's dimensions are known.
 * </p>
 *
 * <p>
 * {@code shape} and {@code image} are mutually exclusive: {@code image} is present only when a {@code url()}
 * specification yields pixels. All other cases (a basic-shape, a shape-box alone, or a measurement pass that cannot
 * resolve the image) use {@code shape}.
 * </p>
 */
public final class ShapeOutsideParams {
	/**
	 * A basic shape or reference box alone (the reference box is {@code shape.referenceBox}). Null if image is
	 * present.
	 */
	public final ClipPathShape shape;
	/**
	 * The outline of opaque pixels extracted from a {@code url()} image, after thresholding. Null for a basic
	 * shape.
	 */
	public final ShapeImage image;
	/** {@code shape-margin} (% refers to the containing block's line-axis width). */
	public final Length margin;

	public ShapeOutsideParams(final ClipPathShape shape, final ShapeImage image, final Length margin) {
		if ((shape == null) == (image == null)) {
			throw new IllegalArgumentException("exactly one of shape/image must be non-null");
		}
		this.shape = shape;
		this.image = image;
		this.margin = margin == null ? Length.ZERO_LENGTH : margin;
	}

	/**
	 * An image-derived shape. Keeps no pixels, only the ranges of pixels exceeding the threshold in each row and
	 * column. Exclusion-area queries need only the maximum/minimum for each band along the line axis, so this is
	 * sufficient and avoids retaining an entire image in the ledger.
	 *
	 * @param width  width in pixels
	 * @param height height in pixels
	 * @param rowMin x of the first opaque pixel in each row (-1 if none)
	 * @param rowMax x of the last opaque pixel in each row (-1 if none)
	 * @param colMin y of the first opaque pixel in each column (-1 if none)
	 * @param colMax y of the last opaque pixel in each column (-1 if none)
	 */
	public record ShapeImage(int width, int height, int[] rowMin, int[] rowMax, int[] colMin, int[] colMax) {
		/**
		 * Extracts outline ranges from an image by applying {@code shape-image-threshold} (css-shapes-1 §3.2:
		 * pixels with opacity <b>greater than</b> the threshold form the shape).
		 */
		public static ShapeImage extract(final java.awt.image.BufferedImage pixels, final double threshold) {
			final int w = pixels.getWidth(), h = pixels.getHeight();
			final int[] rowMin = new int[h], rowMax = new int[h], colMin = new int[w], colMax = new int[w];
			java.util.Arrays.fill(rowMin, -1);
			java.util.Arrays.fill(rowMax, -1);
			java.util.Arrays.fill(colMin, -1);
			java.util.Arrays.fill(colMax, -1);
			final boolean hasAlpha = pixels.getColorModel().hasAlpha();
			final int limit = (int) Math.floor(Math.max(0, Math.min(1, threshold)) * 255);
			final int[] row = new int[w];
			for (int y = 0; y < h; ++y) {
				pixels.getRGB(0, y, w, 1, row, 0, w);
				for (int x = 0; x < w; ++x) {
					final int alpha = hasAlpha ? (row[x] >>> 24) : 255;
					if (alpha > limit) {
						if (rowMin[y] < 0) {
							rowMin[y] = x;
						}
						rowMax[y] = x;
						if (colMin[x] < 0) {
							colMin[x] = y;
						}
						colMax[x] = y;
					}
				}
			}
			return new ShapeImage(w, h, rowMin, rowMax, colMin, colMax);
		}
	}

	public String toString() {
		return "ShapeOutsideParams[shape=" + this.shape + ",image=" + (this.image != null) + ",margin=" + this.margin
				+ "]";
	}
}
