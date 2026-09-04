package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionShape;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 配置済み浮動体の{@code shape-outside}を排除形状
 * ({@link ExclusionShape}、論理座標)へ解決します(css-shapes-1、
 * 2026-08-29新設。{@code BlockBuilder.snapshotExclusions}が使う)。
 *
 * <p>
 * 浮動体の寸法は配置後にしか確定しない(auto幅・画像の内在寸法)ので、
 * スタイル段階では{@link ShapeOutsideParams}(長さ・%のまま)を運び、
 * 台帳のスナップショット時にここで実寸へ落とす。解決は台帳が変わる
 * たびに走るが、1浮動体あたりArea演算1〜2回+平坦化で済み、行ごとの
 * 照会はスナップショットのキャッシュに乗る。
 * </p>
 *
 * <p>
 * <b>ページ跨ぎ</b>: 救済分割の続き断片({@link VisualRescueFloatBox})は
 * 元ボックスの全体形状を{@code offset}だけ上へずらし、断片の矩形で
 * 切り抜く(次ページ先頭に円の下半分が現れる)。ブロック浮動体の
 * 通常分割(SplitOnCommit)の続き断片は元の寸法・位置を持たないため、
 * その断片ではマージンボックス矩形へ退避する(既知の制限)。
 * </p>
 */
final class FloatShapeResolver {
	private FloatShapeResolver() {
	}

	/**
	 * @param box                配置済み浮動体
	 * @param lineStart          排除帯の行方向開始(論理座標)
	 * @param pageStart          排除帯のページ方向開始(論理座標)
	 * @param ownerParams        包含ブロックの組版方向
	 * @param containingLineSize 包含ブロックの行方向幅(shape-marginの%基準)
	 * @return 解決した形状。矩形と等価・解決不能ならnull
	 */
	static ExclusionShape resolve(final IFloatBox box, final double lineStart, final double pageStart,
			final AbstractTextParams ownerParams, final double containingLineSize) {
		final WritingMode flow = ownerParams.flow;
		final ShapeOutsideParams params = box.getFloatPos().shapeOutside;
		if (params == null) {
			return null;
		}
		final IFloatBox geometry;
		final double offset;
		if (box instanceof VisualRescueFloatBox fragment) {
			geometry = (IFloatBox) fragment.getSource();
			offset = fragment.getOffset();
		} else if (box instanceof FloatBlockBox block && block.isContinuationFragment()) {
			return null;
		} else {
			geometry = box;
			offset = 0;
		}
		final AbsoluteRectFrame frame = frameOf(geometry);
		if (frame == null) {
			return null;
		}
		// 物理座標: マージンボックス左上=(0,0)
		final double width = geometry.getWidth(), height = geometry.getHeight();
		double margin = LayoutUtils.computeLength(params.margin, containingLineSize);
		if (LayoutUtils.isNone(margin) || margin < 0) {
			margin = 0;
		}
		final AxisSpan lineSpan = new AxisSpan(lineStart, lineStart + box.getLineExtent(flow));
		final AxisSpan pageSpan = new AxisSpan(pageStart, pageStart + box.getPageExtent(flow));
		final AffineTransform toLogical = AffineTransform.getTranslateInstance(lineStart, pageStart - offset);
		toLogical.concatenate(physicalToLogical(ownerParams, width, height));
		if (params.image != null) {
			return imageProfile(params.image, frame, width, height, margin, ownerParams, lineStart, pageStart - offset,
					lineSpan, pageSpan);
		}
		final ClipPathShape shape = params.shape;
		final Rectangle2D.Double ref = referenceRect(shape.referenceBox, frame, width, height);
		final Shape physical;
		if (shape instanceof ClipPathShape.BoxOnly) {
			final double[][] radii = cornerRadii(shape.referenceBox, frame, width, height);
			if (radii == null && margin == 0) {
				// 角丸なしのマージンボックス=従来の矩形。形状を持たせない方が
				// 照会が軽く、既存文書の挙動も完全に保存される
				if (shape.referenceBox == ClipPathShape.ReferenceBox.MARGIN_BOX) {
					return null;
				}
				physical = ref;
			} else {
				physical = radii == null ? ref : roundedRect(ref, radii[0], radii[1]);
			}
		} else {
			physical = shape.resolve(ref.x, ref.y, ref.width, ref.height);
		}
		final Shape dilated = ExclusionShape.dilate(physical, margin);
		return ExclusionShape.ofShape(toLogical.createTransformedShape(dilated), lineSpan, pageSpan);
	}

	private static AbsoluteRectFrame frameOf(final IFloatBox box) {
		if (box instanceof AbstractContainerBox container) {
			return container.getFrame();
		}
		if (box instanceof AbstractReplacedBox replaced) {
			return replaced.getFrame();
		}
		return null;
	}

	/**
	 * 物理座標(x右・y下)から論理座標(u=行方向・v=ページ方向)への変換。
	 * {@code LayoutUtils.drawX/drawY}の逆——RLはページ方向が右→左なので
	 * v = width - x。
	 */
	static AffineTransform physicalToLogical(final WritingMode flow, final double width) {
		return switch (flow) {
		case TB -> new AffineTransform();
		case LR -> new AffineTransform(0, 1, 1, 0, 0, 0);
		case RL -> new AffineTransform(0, -1, 1, 0, 0, width);
		};
	}

	/** 行内進行を含む物理座標→論理座標変換。 */
	static AffineTransform physicalToLogical(final AbstractTextParams params, final double width,
			final double height) {
		final boolean bottomToTop = params.flow.isVertical()
				&& params.writingModeVariant != WritingModeVariant.NORMAL
				&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
						params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		if (!bottomToTop) {
			return physicalToLogical(params.flow, width);
		}
		return switch (params.flow) {
		case TB -> new AffineTransform();
		case LR -> new AffineTransform(0, 1, -1, 0, height, 0);
		case RL -> new AffineTransform(0, -1, -1, 0, height, width);
		};
	}

	/** 参照ボックスの矩形(マージンボックス左上原点の物理座標)。 */
	static Rectangle2D.Double referenceRect(final ClipPathShape.ReferenceBox box, final AbsoluteRectFrame frame,
			final double width, final double height) {
		final double ml = frame.margin.left, mt = frame.margin.top, mr = frame.margin.right, mb = frame.margin.bottom;
		final RectBorder border = frame.frame.border;
		final double bl = border.getLeft().width, bt = border.getTop().width, br = border.getRight().width,
				bb = border.getBottom().width;
		final double pl = frame.padding.left, pt = frame.padding.top, pr = frame.padding.right,
				pb = frame.padding.bottom;
		return switch (box) {
		case MARGIN_BOX -> new Rectangle2D.Double(0, 0, width, height);
		case BORDER_BOX -> new Rectangle2D.Double(ml, mt, Math.max(0, width - ml - mr), Math.max(0, height - mt - mb));
		case PADDING_BOX -> new Rectangle2D.Double(ml + bl, mt + bt, Math.max(0, width - ml - mr - bl - br),
				Math.max(0, height - mt - mb - bt - bb));
		case CONTENT_BOX -> new Rectangle2D.Double(ml + bl + pl, mt + bt + pt,
				Math.max(0, width - ml - mr - bl - br - pl - pr), Math.max(0, height - mt - mb - bt - bb - pt - pb));
		};
	}

	/**
	 * 参照ボックスの角半径([hr[4], vr[4]]、TL・TR・BR・BLの順)。
	 * 全て0ならnull。border-boxの半径からpadding/content-boxは
	 * 内側の辺の幅を引き、margin-boxはcss-shapes-1 §3.3の式で外側へ
	 * 広げる(比r/m≧1ならr+m、そうでなければr·(1+(r/m−1)³)。r=0なら0
	 * のまま=角は直角)。
	 */
	static double[][] cornerRadii(final ClipPathShape.ReferenceBox box, final AbsoluteRectFrame frame,
			final double width, final double height) {
		final RectBorder border = frame.frame.border;
		if (!border.isRounded()) {
			return null;
		}
		final double bw = Math.max(0, width - frame.margin.getFrameWidth());
		final double bh = Math.max(0, height - frame.margin.getFrameHeight());
		final RectBorder.Radius[] rs = { border.getTopLeft().resolve(bw, bh), border.getTopRight().resolve(bw, bh),
				border.getBottomRight().resolve(bw, bh), border.getBottomLeft().resolve(bw, bh) };
		final double[] hr = new double[4], vr = new double[4];
		// 各角に接する辺: [左/右のborder幅, 上/下のborder幅, 左/右padding, 上/下padding, 左/右margin, 上/下margin]
		final double[][] edges = {
				{ border.getLeft().width, border.getTop().width, frame.padding.left, frame.padding.top,
						frame.margin.left, frame.margin.top },
				{ border.getRight().width, border.getTop().width, frame.padding.right, frame.padding.top,
						frame.margin.right, frame.margin.top },
				{ border.getRight().width, border.getBottom().width, frame.padding.right, frame.padding.bottom,
						frame.margin.right, frame.margin.bottom },
				{ border.getLeft().width, border.getBottom().width, frame.padding.left, frame.padding.bottom,
						frame.margin.left, frame.margin.bottom } };
		boolean any = false;
		for (int i = 0; i < 4; ++i) {
			double h = rs[i].hr, v = rs[i].vr;
			switch (box) {
			case BORDER_BOX -> {
			}
			case PADDING_BOX -> {
				h -= edges[i][0];
				v -= edges[i][1];
			}
			case CONTENT_BOX -> {
				h -= edges[i][0] + edges[i][2];
				v -= edges[i][1] + edges[i][3];
			}
			case MARGIN_BOX -> {
				h = marginRadius(h, edges[i][4]);
				v = marginRadius(v, edges[i][5]);
			}
			}
			hr[i] = Math.max(0, h);
			vr[i] = Math.max(0, v);
			any |= hr[i] > 0 && vr[i] > 0;
		}
		return any ? new double[][] { hr, vr } : null;
	}

	private static double marginRadius(final double radius, final double margin) {
		if (radius <= 0 || margin <= 0) {
			return radius;
		}
		final double ratio = radius / margin;
		if (ratio >= 1) {
			return radius + margin;
		}
		return radius * (1 + Math.pow(ratio - 1, 3));
	}

	/** 楕円角の角丸矩形(半径は矩形に収まるよう一律に縮める——CSS Backgrounds §5.5)。 */
	static Shape roundedRect(final Rectangle2D.Double r, final double[] hrIn, final double[] vrIn) {
		final double[] hr = hrIn.clone(), vr = vrIn.clone();
		double f = 1;
		f = Math.min(f, r.width / Math.max(1e-9, hr[0] + hr[1]));
		f = Math.min(f, r.width / Math.max(1e-9, hr[3] + hr[2]));
		f = Math.min(f, r.height / Math.max(1e-9, vr[0] + vr[3]));
		f = Math.min(f, r.height / Math.max(1e-9, vr[1] + vr[2]));
		if (f < 1) {
			for (int i = 0; i < 4; ++i) {
				hr[i] *= f;
				vr[i] *= f;
			}
		}
		final double k = 0.5522847498;
		final double x = r.x, y = r.y, w = r.width, h = r.height;
		final Path2D.Double p = new Path2D.Double();
		p.moveTo(x + hr[0], y);
		p.lineTo(x + w - hr[1], y);
		p.curveTo(x + w - hr[1] + k * hr[1], y, x + w, y + vr[1] - k * vr[1], x + w, y + vr[1]);
		p.lineTo(x + w, y + h - vr[2]);
		p.curveTo(x + w, y + h - vr[2] + k * vr[2], x + w - hr[2] + k * hr[2], y + h, x + w - hr[2], y + h);
		p.lineTo(x + hr[3], y + h);
		p.curveTo(x + hr[3] - k * hr[3], y + h, x, y + h - vr[3] + k * vr[3], x, y + h - vr[3]);
		p.lineTo(x, y + vr[0]);
		p.curveTo(x, y + vr[0] - k * vr[0], x + hr[0] - k * hr[0], y, x + hr[0], y);
		p.closePath();
		return p;
	}

	/**
	 * 画像形状。画像はコンテンツボックスに合わせて拡縮して置く
	 * (css-shapes-1 §3.2「used content box sizeを幅・高さとする置換要素の
	 * ように配置」)。走査線はページ方向に沿って取り、shape-marginは
	 * 行方向±m・ページ方向±m(角が丸でなく四角になる近似——円板との
	 * ミンコフスキー和の外接、誤差は角で最大(√2−1)m)。
	 */
	private static ExclusionShape imageProfile(final ShapeOutsideParams.ShapeImage image,
			final AbsoluteRectFrame frame, final double width, final double height, final double margin,
			final AbstractTextParams ownerParams, final double uOrigin, final double vOrigin, final AxisSpan lineSpan,
			final AxisSpan pageSpan) {
		final WritingMode flow = ownerParams.flow;
		final boolean bottomToTop = flow.isVertical()
				&& ownerParams.writingModeVariant != WritingModeVariant.NORMAL
				&& TypesettingMode.inlineProgression(flow, ownerParams.writingModeVariant,
						ownerParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		final Rectangle2D.Double content = referenceRect(ClipPathShape.ReferenceBox.CONTENT_BOX, frame, width,
				height);
		if (content.width <= 0 || content.height <= 0) {
			return null;
		}
		final double sx = content.width / image.width(), sy = content.height / image.height();
		// 走査線の並び(v昇順)と、各走査線の行方向範囲(u)
		final int n;
		final double vStart, vStep;
		final double[] minU, maxU;
		switch (flow) {
		case TB -> {
			// v=y(画像の行)、u=x
			n = image.height();
			vStart = content.y;
			vStep = sy;
			minU = new double[n];
			maxU = new double[n];
			for (int k = 0; k < n; ++k) {
				if (image.rowMin()[k] < 0) {
					minU[k] = maxU[k] = Double.NaN;
				} else {
					minU[k] = content.x + image.rowMin()[k] * sx;
					maxU[k] = content.x + (image.rowMax()[k] + 1) * sx;
				}
			}
		}
		case LR, RL -> {
			// v=x(画像の列; RLは右→左なので列を逆順に)、u=y
			n = image.width();
			vStep = sx;
			vStart = flow == WritingMode.LR ? content.x : width - content.x - content.width;
			minU = new double[n];
			maxU = new double[n];
			for (int k = 0; k < n; ++k) {
				final int col = flow == WritingMode.LR ? k : n - 1 - k;
				if (image.colMin()[col] < 0) {
					minU[k] = maxU[k] = Double.NaN;
				} else {
					if (bottomToTop) {
						minU[k] = height - content.y - (image.colMax()[col] + 1) * sy;
						maxU[k] = height - content.y - image.colMin()[col] * sy;
					} else {
						minU[k] = content.y + image.colMin()[col] * sy;
						maxU[k] = content.y + (image.colMax()[col] + 1) * sy;
					}
				}
			}
		}
		default -> throw new IllegalStateException();
		}
		// shape-margin: u方向は±m、v方向は近傍m以内の走査線の和
		if (margin > 0) {
			final int reach = (int) Math.ceil(margin / vStep);
			final double[] dMin = new double[n], dMax = new double[n];
			for (int k = 0; k < n; ++k) {
				double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
				for (int j = Math.max(0, k - reach); j <= Math.min(n - 1, k + reach); ++j) {
					if (!Double.isNaN(minU[j])) {
						lo = Math.min(lo, minU[j] - margin);
						hi = Math.max(hi, maxU[j] + margin);
					}
				}
				if (lo > hi) {
					dMin[k] = dMax[k] = Double.NaN;
				} else {
					dMin[k] = lo;
					dMax[k] = hi;
				}
			}
			System.arraycopy(dMin, 0, minU, 0, n);
			System.arraycopy(dMax, 0, maxU, 0, n);
		}
		// 論理座標へ平行移動し、断片の排除矩形で切り抜く
		final double uLo = lineSpan.start(), uHi = lineSpan.end();
		int first = -1, last = -1;
		for (int k = 0; k < n; ++k) {
			if (!Double.isNaN(minU[k])) {
				minU[k] = Math.max(uLo, uOrigin + minU[k]);
				maxU[k] = Math.min(uHi, uOrigin + maxU[k]);
				if (minU[k] > maxU[k]) {
					minU[k] = maxU[k] = Double.NaN;
				}
			}
			final double v0 = vOrigin + vStart + k * vStep, v1 = v0 + vStep;
			if (v1 <= pageSpan.start() || v0 >= pageSpan.end()) {
				minU[k] = maxU[k] = Double.NaN;
			} else {
				if (first < 0) {
					first = k;
				}
				last = k;
			}
		}
		if (first < 0) {
			return ExclusionShape.ofProfile(vOrigin + vStart, vStep, new double[0], new double[0]);
		}
		final double[] pm = java.util.Arrays.copyOfRange(minU, first, last + 1);
		final double[] px = java.util.Arrays.copyOfRange(maxU, first, last + 1);
		return ExclusionShape.ofProfile(vOrigin + vStart + first * vStep, vStep, pm, px);
	}
}
