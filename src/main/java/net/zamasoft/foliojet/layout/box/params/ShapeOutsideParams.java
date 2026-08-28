package net.zamasoft.foliojet.layout.box.params;

/**
 * 浮動体の{@code shape-outside}・{@code shape-margin}・
 * {@code shape-image-threshold}をまとめた不変パラメータです
 * (css-shapes-1、2026-08-29新設)。
 *
 * <p>
 * {@link FloatPos}に載せて運ぶ(構築時に一度だけ書く——
 * {@code BlockBuilder}の排除域スナップショットは{@code FloatPos}を
 * 台帳追加後に読み直さない前提なので、ここは全フィールドfinalで
 * 後から書き換えられない形にしておく)。実際の排除形状は配置後の
 * 浮動体の寸法が決まってから{@code FloatShapeResolver}が解決する。
 * </p>
 *
 * <p>
 * {@code shape}と{@code image}は排他: {@code url()}指定で画素が得られた
 * ときだけ{@code image}を持ち、それ以外(basic-shape・shape-boxのみ・
 * 画像を解決できない計測パス)は{@code shape}を持つ。
 * </p>
 */
public final class ShapeOutsideParams {
	/** 基本形状または参照ボックスのみ(参照ボックスは{@code shape.referenceBox})。imageがある場合はnull。 */
	public final ClipPathShape shape;
	/** {@code url()}画像から抽出した不透明画素の輪郭(閾値適用済み)。基本形状の場合はnull。 */
	public final ShapeImage image;
	/** {@code shape-margin}(%は包含ブロックの行方向幅基準)。 */
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
	 * 画像由来の形状です。画素そのものは持たず、行ごと・列ごとの
	 * 「閾値を超える画素の範囲」だけを保持する(排除域の照会は行方向の
	 * 帯ごとの最大/最小しか要らないので、これで十分かつ画像1枚分の
	 * メモリを台帳に抱え込まずに済む)。
	 *
	 * @param width  画素幅
	 * @param height 画素高さ
	 * @param rowMin 各行の最初の不透明画素x(なければ-1)
	 * @param rowMax 各行の最後の不透明画素x(なければ-1)
	 * @param colMin 各列の最初の不透明画素y(なければ-1)
	 * @param colMax 各列の最後の不透明画素y(なければ-1)
	 */
	public record ShapeImage(int width, int height, int[] rowMin, int[] rowMax, int[] colMin, int[] colMax) {
		/**
		 * {@code shape-image-threshold}を適用して画像から輪郭範囲を抽出します
		 * (css-shapes-1 §3.2: 不透明度が閾値<b>より大きい</b>画素が形状)。
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
