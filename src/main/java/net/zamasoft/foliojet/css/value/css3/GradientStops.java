package net.zamasoft.foliojet.css.value.css3;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * グラデーションの色停止列です(css-images-3 §3.4、2026-08-29新設)。
 *
 * <p>
 * 停止位置は解析時には確定しない——{@code 50%}は勾配線の長さに対する割合、
 * {@code 10px}は絶対長で、勾配線の長さは塗る箱が決まって初めて分かる。
 * そこで割合({@link #ratio})と絶対長({@link #abs}、pt)を別々に持ち、
 * {@link #resolve}で箱の寸法から0..1の位置へ落とす。位置を省いた停止は
 * {@link #auto}で、仕様どおり前後の停止の間に等間隔で置く。
 * </p>
 *
 * <p>
 * <b>繰り返し</b>({@code repeating-*-gradient})はPDFのシェーディングに
 * 無いので、周期(最初と最後の停止の間隔)を勾配線が覆う範囲まで展開した
 * 停止列にする。展開は{@link #MAX_REPEATS}周期で打ち切る(周期が極端に
 * 短い指定で停止が爆発するのを防ぐ。それより細かい縞は印刷でも見えない。
 * 打ち切ったときだけ近似——{@link Resolved#capped()}で知らせる)。
 * 出力先が周期の繰り返し({@code REPEATING_GRADIENT}——Java2D・SVG)を
 * 持つなら、{@link #resolvePeriod}で1周期だけを作り{@code SpreadMethod.REPEAT}
 * で塗るのが厳密(2026-08-29)。
 * </p>
 */
public final class GradientStops {
	/** 繰り返しの展開上限(周期数)。 */
	public static final int MAX_REPEATS = 64;

	/**
	 * AWT/PDFが要求する「厳密に増加する位置」のための最小差。ハードストップ
	 * ({@code red 50%, blue 50%})を保ったまま満たす(2026-08-16の
	 * normalizeGradientStopsから移設)。
	 */
	private static final double EPSILON = 1e-5;

	private final Color[] colors;
	private final double[] ratio;
	private final double[] abs;
	private final boolean[] auto;

	/**
	 * 解決済みの停止列(位置は0..1で厳密に増加)。{@code capped}は繰り返しの
	 * 展開が{@link #MAX_REPEATS}で打ち切られた(=覆いきれず近似になった)こと。
	 */
	public record Resolved(double[] fractions, Color[] colors, boolean capped) {
		public Resolved(final double[] fractions, final Color[] colors) {
			this(fractions, colors, false);
		}
	}

	/**
	 * 繰り返しの1周期ぶんの停止列(2026-08-29)。位置は周期を0..1へ写した
	 * もので、勾配線の始点(位相0)から始まる——元の停止が0以外から
	 * 始まっていても、周期で折り返して位相を合わせてある。
	 *
	 * @param fractions 0..1(厳密に増加)
	 * @param colors    各位置の色
	 * @param length    周期の長さ(勾配線の長さに対する割合)
	 */
	public record Period(double[] fractions, Color[] colors, double length) {
	}

	public GradientStops(final Color[] colors, final double[] ratio, final double[] abs, final boolean[] auto) {
		if (colors.length == 0 || colors.length != ratio.length || colors.length != abs.length
				|| colors.length != auto.length) {
			throw new IllegalArgumentException();
		}
		this.colors = colors;
		this.ratio = ratio;
		this.abs = abs;
		this.auto = auto;
	}

	/** 位置が全て割合(または自動)で確定している停止列を作ります。 */
	public static GradientStops ofFractions(final double[] fractions, final Color[] colors) {
		final double[] abs = new double[fractions.length];
		final boolean[] auto = new boolean[fractions.length];
		for (int i = 0; i < fractions.length; ++i) {
			auto[i] = fractions[i] < 0;
		}
		return new GradientStops(colors, fractions, abs, auto);
	}

	public int size() {
		return this.colors.length;
	}

	public Color lastColor() {
		return this.colors[this.colors.length - 1];
	}

	public Color firstColor() {
		return this.colors[0];
	}

	/**
	 * 停止位置を確定します。
	 *
	 * @param length    勾配線の長さ(pt)。割合と絶対長の換算に使う。0以下なら
	 *                  絶対長は無視する
	 * @param repeating 繰り返すか
	 * @param cover     塗りが覆うべき範囲(勾配線の長さの倍数、1以上)。
	 *                  返す位置はこの範囲を0..1へ縮めたもの——放射の
	 *                  繰り返しで、最遠の角まで周期を展開するために使う
	 */
	public Resolved resolve(final double length, final boolean repeating, final double cover) {
		final int n = this.colors.length;
		final double[] pos = this.positions(length);
		final double span = Math.max(1.0, cover);
		final List<double[]> positions = new ArrayList<double[]>();
		final List<Color> colorList = new ArrayList<Color>();
		final double period = pos[n - 1] - pos[0];
		boolean capped = false;
		if (repeating && period > 1e-6) {
			// 周期を0以下から始めてcoverを越えるまで並べる
			int first = (int) Math.floor(-pos[0] / period);
			int last = (int) Math.ceil((span - pos[0]) / period);
			if (last - first > MAX_REPEATS) {
				last = first + MAX_REPEATS;
				capped = true;
			}
			for (int k = first; k <= last; ++k) {
				final double offset = k * period;
				// 周期の継ぎ目では前の周期の末尾と同じ位置に先頭の色が並ぶ
				// (ハードストップとして折り返る)。normalizeが最小差を空ける
				for (int i = 0; i < n; ++i) {
					positions.add(new double[] { pos[i] + offset });
					colorList.add(this.colors[i]);
				}
			}
		} else {
			for (int i = 0; i < n; ++i) {
				positions.add(new double[] { pos[i] });
				colorList.add(this.colors[i]);
			}
		}
		final double[] ps = new double[positions.size()];
		for (int i = 0; i < ps.length; ++i) {
			ps[i] = positions.get(i)[0];
		}
		final Resolved r = clip(ps, colorList.toArray(new Color[colorList.size()]), 0, span);
		return capped ? new Resolved(r.fractions(), r.colors(), true) : r;
	}

	/**
	 * 繰り返しの1周期を、勾配線の始点から始まる位相で返します。周期が
	 * 潰れている(全停止が同じ位置)ならnull——呼び出し側は{@link #resolve}の
	 * 展開へ落とす。
	 *
	 * @param length 勾配線の長さ(pt)
	 */
	public Period resolvePeriod(final double length) {
		final int n = this.colors.length;
		final double[] pos = this.positions(length);
		final double period = pos[n - 1] - pos[0];
		if (!(period > 1e-6)) {
			return null;
		}
		// 元の停止の周期内の位置(先頭を0とする)
		final double[] offset = new double[n];
		for (int i = 0; i < n; ++i) {
			offset[i] = pos[i] - pos[0];
		}
		// 勾配線の始点(絶対位置0)は周期内のどこか: shift = pos[0] mod period
		final double shift = pos[0] - Math.floor(pos[0] / period) * period;
		// 各停止を「始点から始まる周期」の位置へ折り返す。同じ位置の停止
		// (ハードストップ)は同じ側へ折れるので順序が保たれる
		final List<double[]> folded = new ArrayList<double[]>(n + 2);
		for (int i = 0; i < n; ++i) {
			double t = offset[i] + shift;
			if (t >= period) {
				t -= period;
			}
			folded.add(new double[] { t, i });
		}
		folded.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
		// 両端は周期関数としての始点の色(shift=0なら先頭色と末尾色)
		final double u = shift > 0 ? period - shift : 0;
		final List<Double> outPos = new ArrayList<Double>(n + 2);
		final List<Color> outColors = new ArrayList<Color>(n + 2);
		outPos.add(0.0);
		outColors.add(colorAt(offset, this.colors, u));
		for (final double[] f : folded) {
			outPos.add(f[0] / period);
			outColors.add(this.colors[(int) f[1]]);
		}
		outPos.add(1.0);
		outColors.add(colorAt(offset, this.colors, shift > 0 ? period - shift : period));
		final double[] fractions = new double[outPos.size()];
		for (int i = 0; i < fractions.length; ++i) {
			fractions[i] = outPos.get(i);
		}
		normalize(fractions);
		return new Period(fractions, outColors.toArray(new Color[outColors.size()]), period);
	}

	/** 停止位置を確定します(css-images-3 §3.4.3の補正込み、繰り返し展開前)。 */
	private double[] positions(final double length) {
		final int n = this.colors.length;
		final double[] pos = new double[n];
		for (int i = 0; i < n; ++i) {
			if (this.auto[i]) {
				pos[i] = Double.NaN;
			} else {
				pos[i] = this.ratio[i] + (length > 0 ? this.abs[i] / length : 0);
			}
		}
		// css-images-3 §3.4.3 の補正: 先頭・末尾の省略は0・1、後退は前の
		// 位置まで引き上げ、省略は前後の間に等間隔
		if (Double.isNaN(pos[0])) {
			pos[0] = 0;
		}
		if (Double.isNaN(pos[n - 1])) {
			pos[n - 1] = 1;
		}
		double max = pos[0];
		for (int i = 1; i < n; ++i) {
			if (!Double.isNaN(pos[i])) {
				if (pos[i] < max) {
					pos[i] = max;
				}
				max = pos[i];
			}
		}
		for (int i = 1; i < n; ++i) {
			if (Double.isNaN(pos[i])) {
				int j = i + 1;
				while (Double.isNaN(pos[j])) {
					++j;
				}
				final double a = pos[i - 1];
				final double step = (pos[j] - a) / (j - i + 1);
				for (int k = i; k < j; ++k) {
					pos[k] = a + step * (k - i + 1);
				}
				i = j;
			}
		}
		return pos;
	}

	/**
	 * 位置列を[lo,hi]へ切り詰めて0..1へ写します。範囲外の停止は落とし、
	 * 端には補間した色を置く(絶対長の停止で勾配線を越えた場合や、
	 * 繰り返しの展開で0未満・cover超になった周期に要る)。
	 */
	private static Resolved clip(final double[] pos, final Color[] colors, final double lo, final double hi) {
		final int n = pos.length;
		final List<Double> outPos = new ArrayList<Double>(n + 2);
		final List<Color> outColors = new ArrayList<Color>(n + 2);
		final double range = hi - lo;
		// 端の色
		if (pos[0] > lo) {
			outPos.add(0.0);
			outColors.add(colors[0]);
		} else if (pos[0] < lo) {
			outPos.add(0.0);
			outColors.add(colorAt(pos, colors, lo));
		}
		for (int i = 0; i < n; ++i) {
			if (pos[i] < lo || pos[i] > hi) {
				continue;
			}
			outPos.add((pos[i] - lo) / range);
			outColors.add(colors[i]);
		}
		if (pos[n - 1] < hi) {
			outPos.add(1.0);
			outColors.add(colors[n - 1]);
		} else if (pos[n - 1] > hi) {
			outPos.add(1.0);
			outColors.add(colorAt(pos, colors, hi));
		}
		final double[] fractions = new double[outPos.size()];
		for (int i = 0; i < fractions.length; ++i) {
			fractions[i] = outPos.get(i);
		}
		normalize(fractions);
		return new Resolved(fractions, outColors.toArray(new Color[outColors.size()]));
	}

	/** 位置{@code t}での補間色(位置は非減少であること)。 */
	public static Color colorAt(final double[] pos, final Color[] colors, final double t) {
		if (t <= pos[0]) {
			return colors[0];
		}
		final int n = pos.length;
		if (t >= pos[n - 1]) {
			return colors[n - 1];
		}
		for (int i = 1; i < n; ++i) {
			if (t <= pos[i]) {
				final double d = pos[i] - pos[i - 1];
				if (d <= 0) {
					return colors[i];
				}
				return mix(colors[i - 1], colors[i], (t - pos[i - 1]) / d);
			}
		}
		return colors[n - 1];
	}

	/** 2色を{@code f}(0で前者、1で後者)で混ぜます(プリマルチプライド補間)。 */
	public static Color mix(final Color a, final Color b, final double f) {
		final float fa = (float) f, ia = 1 - fa;
		final float aa = a.getAlpha(), ab = b.getAlpha();
		final float alpha = aa * ia + ab * fa;
		if (alpha <= 0) {
			return RGBAColor.create(0, 0, 0, 0);
		}
		final float r = (a.getRed() * aa * ia + b.getRed() * ab * fa) / alpha;
		final float g = (a.getGreen() * aa * ia + b.getGreen() * ab * fa) / alpha;
		final float bl = (a.getBlue() * aa * ia + b.getBlue() * ab * fa) / alpha;
		if (alpha >= 1) {
			return RGBColor.create(clamp(r), clamp(g), clamp(bl));
		}
		return RGBAColor.create(clamp(r), clamp(g), clamp(bl), alpha);
	}

	private static float clamp(final float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/**
	 * カラーストップの位置を<b>厳密な単調増加</b>へ正規化します(2026-08-16)。
	 *
	 * <p>
	 * CSSでは前のストップより小さい位置は前の位置まで引き上げられ、
	 * <b>同じ位置を重ねることも正当</b>です(いわゆるハードストップ——
	 * {@code linear-gradient(red 50%, blue 50%)}で境界をくっきり切る書き方)。
	 * ところが実際に塗る{@code java.awt.MultipleGradientPaint}は位置が
	 * 厳密に増加していることを要求し、そうでなければ
	 * {@code IllegalArgumentException: Keyframe fractions must be increasing}
	 * を投げます。これは描画の失敗では済まず、<b>そのページの変換ごと
	 * 中断させて内容を全て失わせていました</b>(実サイトのコーパスで
	 * elife-art・shadcn-docsの2件が丸ごと変換不能だった原因)。
	 * </p>
	 *
	 * <p>
	 * そこで、重なった位置には表現可能な最小の差だけを与えて追い出します。
	 * 差は{@code 1e-5}で、幅1000ptの版面でも0.01pt未満——見た目の
	 * ハードストップは保ったまま、AWTの要求を満たせます。
	 * </p>
	 */
	public static void normalize(final double[] ds) {
		for (int i = 0; i < ds.length; ++i) {
			if (Double.isNaN(ds[i])) {
				ds[i] = i == 0 ? 0 : ds[i - 1];
			}
			if (ds[i] < 0) {
				ds[i] = 0;
			} else if (ds[i] > 1) {
				ds[i] = 1;
			}
			if (i > 0 && ds[i] <= ds[i - 1]) {
				// CSSは後退を許さない(前の位置まで引き上げる)。その上で
				// AWTのために最小差を空ける
				ds[i] = ds[i - 1] + EPSILON;
			}
		}
		// 末尾が1を超えたら、後ろから詰め直して1以下に収める
		if (ds[ds.length - 1] > 1) {
			ds[ds.length - 1] = 1;
			for (int i = ds.length - 2; i >= 0; --i) {
				if (ds[i] >= ds[i + 1]) {
					ds[i] = ds[i + 1] - EPSILON;
				}
			}
			if (ds[0] < 0) {
				ds[0] = 0;
			}
		}
	}

	/** 表示リストのダンプ用。色数と位置指定の要約。 */
	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < this.colors.length; ++i) {
			if (i > 0) {
				s.append(',');
			}
			s.append(hex(this.colors[i]));
			if (!this.auto[i]) {
				s.append(' ');
				if (this.ratio[i] != 0 || this.abs[i] == 0) {
					s.append(String.format(java.util.Locale.ROOT, "%.0f%%", this.ratio[i] * 100));
				}
				if (this.abs[i] != 0) {
					s.append(String.format(java.util.Locale.ROOT, "%s%.2fpt", this.ratio[i] != 0 ? "+" : "",
							this.abs[i]));
				}
			}
		}
		return s.toString();
	}

	static String hex(final Color c) {
		final String rgb = String.format("#%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
				Math.round(c.getBlue() * 255));
		return c.getAlpha() < 1 ? rgb + String.format("%02x", Math.round(c.getAlpha() * 255)) : rgb;
	}
}
