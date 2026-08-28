package net.zamasoft.foliojet.layout.constraint;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

/**
 * {@code shape-outside}で解決済みの浮動体の排除形状です(css-shapes-1、
 * 2026-08-29新設)。
 *
 * <p>
 * 座標は{@link FloatExclusion}と同じ論理軸——u=行方向(lineSpan)、
 * v=ページ方向(pageSpan)——で持つ。書字方向による物理→論理の変換は
 * 解決側({@code FloatShapeResolver})が済ませるので、照会側は縦書きを
 * 意識しない。
 * </p>
 *
 * <p>
 * 照会は{@link #lineSpanAt}の1種類だけ: ページ方向の帯[v0, v1]の中で
 * 形状が占める行方向の範囲(最小u〜最大u)。行ボックスは自身の高さ全体で
 * 形状と交わってはならない(css-shapes-1 §4.1「line boxes are shortened
 * as necessary to avoid intersections」)ので、帯の中の<b>最大</b>張り出し
 * を返す。帯と形状が交わらなければnull=この浮動体はその帯では行を
 * 狭めない(マージンボックスの中でも、円の上下の空白部分には行が
 * 入り込める)。
 * </p>
 *
 * <p>
 * 実装は2種: 任意の{@link Shape}を平坦化した線分列
 * ({@link #ofShape})と、画像から抽出した走査線ごとの範囲
 * ({@link #ofProfile})。線分列に対する帯の極値は「帯内の頂点」と
 * 「帯の上下端との交点」だけ調べれば厳密(多角形の凸包でなくとも、
 * 帯で切った領域の極値点は必ずそのどちらかにある)。曲線は平坦化
 * 誤差0.2pt——{@code LayoutUtils.THRESHOLD}(0.5pt)より細かい。
 * </p>
 */
public abstract class ExclusionShape {
	/** 平坦化の許容誤差(pt)。THRESHOLDより細かければ十分。 */
	static final double FLATNESS = 0.2;

	/**
	 * 帯[v0, v1]で形状が占める行方向の範囲を返します。交わらなければnull。
	 * {@code v1 < v0}なら{@code v0}の1点として扱う。
	 */
	public abstract AxisSpan lineSpanAt(double v0, double v1);

	/**
	 * 論理座標の形状から作ります。{@code bounds}(浮動体の排除矩形=
	 * マージンボックス)で切り抜く——仕様§4.1「形状はマージンボックスで
	 * クリップされ、排除域を広げることはできない」。
	 */
	public static ExclusionShape ofShape(final Shape logical, final AxisSpan lineSpan, final AxisSpan pageSpan) {
		final Area area = new Area(logical);
		area.intersect(new Area(new Rectangle2D.Double(lineSpan.start(), pageSpan.start(),
				Math.max(0, lineSpan.end() - lineSpan.start()), Math.max(0, pageSpan.end() - pageSpan.start()))));
		return new Flattened(area);
	}

	/**
	 * 走査線ごとの範囲から作ります(画像形状用)。
	 *
	 * @param vStart 最初の走査線のv
	 * @param vStep  走査線の間隔(>0)
	 * @param minU   各走査線の最小u(空はNaN)
	 * @param maxU   各走査線の最大u(空はNaN)
	 */
	public static ExclusionShape ofProfile(final double vStart, final double vStep, final double[] minU,
			final double[] maxU) {
		return new Profile(vStart, vStep, minU, maxU);
	}

	/**
	 * 形状を{@code margin}だけ外側へ膨らませます({@code shape-margin})。
	 * 丸い端点・丸い接合の太さ{@code 2*margin}の線で縁取った領域と元の
	 * 領域の和は、円板とのミンコフスキー和に等しい(厳密なオフセット)。
	 */
	public static Shape dilate(final Shape shape, final double margin) {
		if (!(margin > 0)) {
			return shape;
		}
		final Area area = new Area(shape);
		area.add(new Area(new BasicStroke((float) (margin * 2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
				.createStrokedShape(shape)));
		return area;
	}

	/** 平坦化した線分列。 */
	static final class Flattened extends ExclusionShape {
		/** [u0, v0, u1, v1]×n。 */
		private final double[] edges;

		Flattened(final Shape shape) {
			final java.util.ArrayList<double[]> list = new java.util.ArrayList<>();
			final double[] c = new double[6];
			double startU = 0, startV = 0, lastU = 0, lastV = 0;
			boolean open = false;
			for (PathIterator it = shape.getPathIterator(null, FLATNESS); !it.isDone(); it.next()) {
				switch (it.currentSegment(c)) {
				case PathIterator.SEG_MOVETO:
					if (open) {
						list.add(new double[] { lastU, lastV, startU, startV });
					}
					startU = lastU = c[0];
					startV = lastV = c[1];
					open = true;
					break;
				case PathIterator.SEG_LINETO:
					list.add(new double[] { lastU, lastV, c[0], c[1] });
					lastU = c[0];
					lastV = c[1];
					break;
				case PathIterator.SEG_CLOSE:
					if (open) {
						list.add(new double[] { lastU, lastV, startU, startV });
						lastU = startU;
						lastV = startV;
					}
					open = false;
					break;
				default:
					throw new IllegalStateException("flattened iterator must not emit curves");
				}
			}
			if (open) {
				list.add(new double[] { lastU, lastV, startU, startV });
			}
			this.edges = new double[list.size() * 4];
			for (int i = 0; i < list.size(); ++i) {
				System.arraycopy(list.get(i), 0, this.edges, i * 4, 4);
			}
		}

		@Override
		public AxisSpan lineSpanAt(final double v0, final double v1in) {
			final double v1 = Math.max(v0, v1in);
			double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < this.edges.length; i += 4) {
				final double ua = this.edges[i], va = this.edges[i + 1], ub = this.edges[i + 2],
						vb = this.edges[i + 3];
				if (va >= v0 && va <= v1) {
					min = Math.min(min, ua);
					max = Math.max(max, ua);
				}
				if (vb >= v0 && vb <= v1) {
					min = Math.min(min, ub);
					max = Math.max(max, ub);
				}
				// 帯の上下端を跨ぐ線分との交点(端点が帯内の場合は上で拾済み)
				final double lo = Math.min(va, vb), hi = Math.max(va, vb);
				if (hi - lo > 0) {
					for (final double edge : new double[] { v0, v1 }) {
						if (edge > lo && edge < hi) {
							final double u = ua + (ub - ua) * (edge - va) / (vb - va);
							min = Math.min(min, u);
							max = Math.max(max, u);
						}
					}
				}
			}
			if (min > max) {
				return null;
			}
			return new AxisSpan(min, max);
		}
	}

	/** 走査線ごとの範囲。 */
	static final class Profile extends ExclusionShape {
		private final double vStart, vStep;
		private final double[] minU, maxU;

		Profile(final double vStart, final double vStep, final double[] minU, final double[] maxU) {
			if (!(vStep > 0) || minU.length != maxU.length) {
				throw new IllegalArgumentException();
			}
			this.vStart = vStart;
			this.vStep = vStep;
			this.minU = minU;
			this.maxU = maxU;
		}

		@Override
		public AxisSpan lineSpanAt(final double v0, final double v1in) {
			final double v1 = Math.max(v0, v1in);
			// 走査線kは[vStart + k*step, vStart + (k+1)*step)を占める
			int from = (int) Math.floor((v0 - this.vStart) / this.vStep);
			int to = (int) Math.ceil((v1 - this.vStart) / this.vStep) - 1;
			if (to < from) {
				to = from;
			}
			from = Math.max(from, 0);
			to = Math.min(to, this.minU.length - 1);
			double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
			for (int k = from; k <= to; ++k) {
				if (Double.isNaN(this.minU[k])) {
					continue;
				}
				min = Math.min(min, this.minU[k]);
				max = Math.max(max, this.maxU[k]);
			}
			if (min > max) {
				return null;
			}
			return new AxisSpan(min, max);
		}
	}
}
