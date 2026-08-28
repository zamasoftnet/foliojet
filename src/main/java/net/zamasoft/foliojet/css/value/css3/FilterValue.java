package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * {@code filter}の値です(filter-effects-1、2026-08-29新設)。
 *
 * <p>
 * 関数列は解析時に4種の効果へ畳み込む: 色行列(grayscale/sepia/saturate/
 * hue-rotate/invert/brightness/contrast——いずれも4×5の色行列なので
 * 順に掛け合わせて1つにできる)、不透明度(opacity()、描画時の
 * グループ不透明度に掛ける)、ぼかし(blur()、標準偏差pt)、影
 * (drop-shadow()、1つだけ——複数は最後を採る)。
 * </p>
 *
 * <p>
 * 仕様では要素全体を1枚の絵にしてから効果を掛けるが、本実装は
 * mix-blend-mode/opacityと同じ流儀で描画要素(背景・境界・文字・画像)
 * ごとに掛ける。子孫の描画要素へ届けるため、計算値は親の計算値と
 * 合成する({@link #compose})——色行列は積、不透明度は積、ぼかしは和。
 * 影は宣言した要素の枠にだけ描く(子孫ごとに影が付くのを防ぐ)ので
 * 合成では引き継がない。
 * </p>
 */
public final class FilterValue implements Value {
	/** {@code drop-shadow(x y blur color)}。 */
	public record DropShadow(double x, double y, double blur, Color color) {
	}

	public static final FilterValue NONE = new FilterValue(1f, null, 0, null, null);

	/** 描画時のグループ不透明度に掛ける係数。 */
	public final float opacity;
	/** 4×5の色行列(行優先、[r g b a 1]に掛ける)。恒等ならnull。 */
	public final float[] matrix;
	/** ぼかしの標準偏差(pt)。0なら無し。 */
	public final double blur;
	public final DropShadow shadow;
	/** 宣言の字面(自身に宣言があるとき)。継承だけの値ではnull。 */
	public final String declared;

	public FilterValue(final float opacity, final float[] matrix, final double blur, final DropShadow shadow,
			final String declared) {
		this.opacity = opacity;
		this.matrix = matrix;
		this.blur = blur;
		this.shadow = shadow;
		this.declared = declared;
	}

	public boolean isNone() {
		return this.opacity == 1f && this.matrix == null && this.blur <= 0 && this.shadow == null;
	}

	/** 色行列かぼかしがあるか(描画をFilterGCで包む必要があるか)。 */
	public boolean hasColorOps() {
		return this.matrix != null || this.blur > 0;
	}

	/**
	 * 親の効果に子の効果を重ねます。子の描画に子の効果を掛け、その結果に
	 * 親の効果が掛かる順。
	 */
	public FilterValue compose(final FilterValue child) {
		if (child == null || child.isNone()) {
			return this.declared == null && this.shadow == null ? this
					: new FilterValue(this.opacity, this.matrix, this.blur, null, null);
		}
		if (this.isNone()) {
			return child;
		}
		final float[] m = this.matrix == null ? child.matrix
				: child.matrix == null ? this.matrix : multiply(this.matrix, child.matrix);
		return new FilterValue(this.opacity * child.opacity, m, this.blur + child.blur, child.shadow, child.declared);
	}

	/** 行列の積 {@code a × b}(bを先に掛ける)。 */
	public static float[] multiply(final float[] a, final float[] b) {
		final float[] r = new float[20];
		for (int row = 0; row < 4; ++row) {
			for (int col = 0; col < 5; ++col) {
				float v = 0;
				for (int k = 0; k < 4; ++k) {
					v += a[row * 5 + k] * b[k * 5 + col];
				}
				if (col == 4) {
					v += a[row * 5 + 4];
				}
				r[row * 5 + col] = v;
			}
		}
		return r;
	}

	/** 色行列を色へ掛けます。アルファ行は恒等なので変えない。 */
	public static float[] apply(final float[] m, final float r, final float g, final float b, final float a) {
		final float[] out = new float[3];
		for (int row = 0; row < 3; ++row) {
			final float v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a
					+ m[row * 5 + 4];
			out[row] = v < 0 ? 0 : v > 1 ? 1 : v;
		}
		return out;
	}

	/** ラスタのキャッシュ鍵に使う、効果の字面。 */
	public String key() {
		final StringBuilder s = new StringBuilder();
		if (this.matrix != null) {
			for (final float v : this.matrix) {
				s.append(String.format(java.util.Locale.ROOT, "%.4f,", v));
			}
		}
		s.append("blur=").append(String.format(java.util.Locale.ROOT, "%.3f", this.blur));
		return s.toString();
	}

	@Override
	public String toString() {
		return this.declared == null ? (this.isNone() ? "none" : "(inherited)") : this.declared;
	}
}
