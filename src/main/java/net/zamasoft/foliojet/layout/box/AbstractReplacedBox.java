package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;

import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 画像ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractReplacedBox.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public abstract class AbstractReplacedBox extends AbstractBox {
	protected final ReplacedParams params;

	protected final AbsoluteRectFrame frame;

	protected double width = 0;

	protected double height = 0;

	protected double offsetX = 0;

	protected double offsetY = 0;

	public AbstractReplacedBox(final ReplacedParams params) {
		this.params = params;
		this.frame = new AbsoluteRectFrame(params.frame);
	}

	public final BoxType getType() {
		return BoxType.REPLACED;
	}

	public final Params getParams() {
		return this.params;
	}

	public final ReplacedParams getReplacedParams() {
		return this.params;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final void calculateFrame(final double lineAxis) {
		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
	}

	/** aspect-ratioによるcontent-box高さ(box-sizingの箱に比率が掛かる。2026-08-29)。 */
	private double ratioHeight(final double contentWidth, final double ratio) {
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			return Math.max(0, (contentWidth + this.frame.getBorderWidth()) / ratio - this.frame.getBorderHeight());
		}
		return contentWidth / ratio;
	}

	/** aspect-ratioによるcontent-box幅({@link #ratioHeight}の逆。2026-08-29)。 */
	private double ratioWidth(final double contentHeight, final double ratio) {
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			return Math.max(0, (contentHeight + this.frame.getBorderHeight()) * ratio - this.frame.getBorderWidth());
		}
		return contentHeight * ratio;
	}

	public final void calculateSize(final double refWidth, final double refHeight, final double refMaxWidth, final double refMaxHeight) {
		double width = LayoutUtils.computeDimensionWidth(this.params.size, refWidth);
		double height = LayoutUtils.computeDimensionHeight(this.params.size, refHeight);

		if (this.params.image instanceof ReplacedBoxImage) {
			((ReplacedBoxImage) this.params.image).setReplacedBox(this, width, height);
		}
		// aspect-ratio(2026-08-29、css-sizing-4 §5): 指定比率が固有比率に
		// 優先する。auto併記のときだけ固有比率(幅・高さとも正)を優先し、
		// 固有比率の無い画像(壊れた画像・寸法なしSVG)で指定比率を使う
		double ratio = 0;
		if (this.params.aspectRatio > 0) {
			final boolean natural = this.params.image.getWidth() > 0 && this.params.image.getHeight() > 0;
			ratio = this.params.aspectRatioAuto && natural ? 0 : this.params.aspectRatio;
		}
		// SPEC CSS2.1 10.3.2
		if (LayoutUtils.isNone(width) && LayoutUtils.isNone(height)) {
			// 両方が不確定
			width = this.params.image.getWidth();
			height = this.params.image.getHeight();
			if (ratio > 0) {
				// 固有幅を保ち高さを比率で決める(幅が無ければ高さから逆算)
				if (width > 0) {
					height = this.ratioHeight(width, ratio);
				} else if (height > 0) {
					width = this.ratioWidth(height, ratio);
				}
			}
		} else if (LayoutUtils.isNone(width)) {
			// 幅が不確定
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				height -= this.frame.getBorderHeight();
			}
			double intrinsicWidth = this.params.image.getWidth();
			double intrinsicHeight = this.params.image.getHeight();
			if (ratio > 0) {
				width = this.ratioWidth(height, ratio);
			} else if (intrinsicHeight != 0) {
				width = intrinsicWidth * height / intrinsicHeight;
			} else {
				// 元画像の高さがゼロの場合[最小のレイアウトにするポリシー]
				width = 0;
			}
		} else if (LayoutUtils.isNone(height)) {
			// 高さが不確定
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				width -= this.frame.getBorderWidth();
			}
			double intrinsicHeight = this.params.image.getHeight();
			double intrinsicWidth = this.params.image.getWidth();
			if (ratio > 0) {
				height = this.ratioHeight(width, ratio);
			} else if (intrinsicWidth != 0) {
				height = intrinsicHeight * width / intrinsicWidth;
			} else {
				// 元画像の幅がゼロの場合[最小のレイアウトにするポリシー]
				height = 0;
			}
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			width -= this.frame.getBorderWidth();
			height -= this.frame.getBorderHeight();
		}

		assert !LayoutUtils.isNone(width);
		assert !LayoutUtils.isNone(height);

		// SPEC CSS2.1 10.4
		double maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, refMaxWidth);
		double minWidth = LayoutUtils.computeDimensionWidth(this.params.minSize, refWidth);
		double maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, refMaxHeight);
		double minHeight = LayoutUtils.computeDimensionHeight(this.params.minSize, refHeight);
		// 固有寸法計測では包含ブロックが未確定(NONE)になりうる。このとき
		// %のmin-sizeは循環寄与なので0として扱う。番兵を数値の最小寸法として
		// Math.maxへ渡すと、置換要素が10^308pt級へ膨張してしまう。
		if (LayoutUtils.isNone(minWidth)) {
			minWidth = 0;
		}
		if (LayoutUtils.isNone(minHeight)) {
			minHeight = 0;
		}
		if (LayoutUtils.isNone(maxWidth)) {
			maxWidth = Double.MAX_VALUE;
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			maxWidth -= this.frame.getBorderWidth();
		}

		if (LayoutUtils.isNone(maxHeight)) {
			maxHeight = Double.MAX_VALUE;
		} else if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			maxHeight -= this.frame.getBorderHeight();
		}
		maxWidth = Math.max(minWidth, maxWidth);
		maxHeight = Math.max(minHeight, maxHeight);

		if (width > maxWidth) {
			if (height > maxHeight) {
				if (maxWidth / width <= maxHeight / height) {
					// #5
					height = Math.max(minHeight, maxWidth * height / width);
					width = maxWidth;
				} else {
					// #6
					width = Math.max(minWidth, maxHeight * width / height);
					height = maxHeight;
				}
			} else if (height < minHeight) {
				// #10
				width = maxWidth;
				height = minWidth;
			} else {
				// #1
				height = Math.max(maxWidth * height / width, minHeight);
				width = maxWidth;
			}
		} else if (width < minWidth) {
			if (height < minHeight) {
				if (minWidth / width <= minHeight / height) {
					// #7
					if (height != 0) {
						width = Math.min(maxWidth, minHeight * width / height);
					} else {
						width = minWidth;
					}
					height = minHeight;
				} else {
					// #8
					if (width != 0) {
						height = Math.min(maxHeight, minWidth * height / width);
					} else {
						height = minHeight;
					}
					width = minWidth;
				}
			} else if (height > maxHeight) {
				// #9
				width = minWidth;
				height = maxHeight;
			} else {
				// #2
				if (width != 0) {
					height = Math.min(minWidth * height / width, maxHeight);
				} else {
					height = minHeight;
				}
				width = minWidth;
			}
		} else if (height > maxHeight) {
			// #3
			width = Math.max(maxHeight * width / height, minWidth);
			height = maxHeight;
		} else if (height < minHeight) {
			// #4
			if (height != 0) {
				width = Math.min(minHeight * width / height, maxWidth);
			} else {
				width = minWidth;
			}
			height = minHeight;
		}
		this.width = width;
		this.height = height;
	}

	public void finishLayoutSelf(IFramedBox containerBox) {
		// 相対配置
		AbstractStaticPos pos = (AbstractStaticPos) this.getPos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}

		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		assert !LayoutUtils.isNone(this.offsetX) : "Undefined offsetX";
		assert !LayoutUtils.isNone(this.offsetY) : "Undefined offsetY";
	}

	/**
	 * リーフ(子を持たない)なので何もしません。
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, java.util.Deque<FinishLayoutStep> worklist) {
	}

	protected static class ReplacedBoxDrawable extends AbsoluteRectFrameDrawable {
		protected final Image image;

		protected final ObjectFitMode objectFit;

		protected final Offset objectPosition;

		public ReplacedBoxDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				AbsoluteRectFrame frame, Image image, ObjectFitMode objectFit, Offset objectPosition, double width,
				double height) {
			super(pageBox, clip, opacity, transform, frame, width, height, null);
			this.image = image;
			this.objectFit = objectFit;
			this.objectPosition = objectPosition;
		}

		@Override
		public String describe() {
			// 脚注ラベル(F5)は解決済み番号を表示リストへ出す——goldenが
			// 座標だけでなく番号文字そのものを固定できるように
			if (this.image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label) {
				return String.format(java.util.Locale.ROOT, "FootnoteLabel[\"%s\" w=%.2f h=%.2f]",
						label.getAltString(), this.width, this.height);
			}
			// object-fit/object-positionが既定でない場合は実描画矩形を出す
			// (innerDrawの内部変換は表示リストに現れないため、goldenで
			// 収まり方を固定できるようにここで同じ計算を晒す)。既定の
			// 出力は従来のまま——既存goldenは不変
			if (this.objectFit != ObjectFitMode.FILL || !isCenterPosition(this.objectPosition)) {
				final double width = this.width - this.frame.getFrameWidth();
				final double height = this.height - this.frame.getFrameHeight();
				if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
					final double[] r = this.fitRect(width, height);
					return super.describe() + String.format(java.util.Locale.ROOT,
							"[objectFit=%s dx=%.2f dy=%.2f w=%.2f h=%.2f]", this.objectFit, r[0], r[1], r[2], r[3]);
				}
			}
			return super.describe();
		}

		/**
		 * {@code filter: drop-shadow()}: ラスタ画像なら不透明度のシルエットを
		 * ぼかした影を、画像と同じ位置・寸法でずらして描く(2026-08-29)。
		 * ラスタでなければ箱の形の影(AbsoluteRectFrameDrawable)。
		 */
		@Override
		protected void drawFilterShadow(GC gc, double x, double y) throws GraphicsException {
			final net.zamasoft.foliojet.css.value.css3.FilterValue.DropShadow s = this.filter.shadow;
			final double left = x + this.frame.getFrameLeft(), top = y + this.frame.getFrameTop();
			final double width = this.width - this.frame.getFrameWidth();
			final double height = this.height - this.frame.getFrameHeight();
			if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
				final double[] r = this.fitRect(width, height);
				final double sx = r[2] / this.image.getWidth(), sy = r[3] / this.image.getHeight();
				// ぼかし半径の半分が標準偏差(filter-effects-1 §9.2)。画像の
				// 論理単位へ換算する
				final double sigma = s.blur() > 0 ? s.blur() / 2 / Math.sqrt(sx * sy) : 0;
				final net.zamasoft.foliojet.layout.util.FilterOps.Shadow shadow = net.zamasoft.foliojet.layout.util.FilterOps
						.shadowOf(this.image, s.color(), sigma);
				if (shadow != null) {
					final AffineTransform at = AffineTransform.getTranslateInstance(
							left + r[0] + s.x() - shadow.padX() * sx, top + r[1] + s.y() - shadow.padY() * sy);
					at.scale(sx, sy);
					try (final var gcState = gc.begin()) {
						gc.transform(at);
						gc.drawImage(shadow.image());
					}
					return;
				}
			}
			super.drawFilterShadow(gc, x, y);
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			super.innerDraw(gc, x, y);
			x += this.frame.getFrameLeft();
			y += this.frame.getFrameTop();
			double width = this.width - this.frame.getFrameWidth();
			double height = this.height - this.frame.getFrameHeight();
			// 固有サイズ0の画像はスケール計算がゼロ除算(Infinity)になるため
			// 描画しない(壊れた変換行列でバックエンドを巻き込むより安全)
			if (width > 0 && height > 0 && this.image.getWidth() > 0 && this.image.getHeight() > 0) {
				final double[] r = this.fitRect(width, height);
				final double dx = r[0], dy = r[1], drawWidth = r[2], drawHeight = r[3];
				final AffineTransform at = AffineTransform.getTranslateInstance(x + dx, y + dy);
				at.scale(drawWidth / this.image.getWidth(), drawHeight / this.image.getHeight());
				// 内容ボックスからはみ出す場合(cover/none等)はブラウザ同様に
				// クリップする
				final boolean overflows = dx < -0.001 || dy < -0.001 || dx + drawWidth > width + 0.001
						|| dy + drawHeight > height + 0.001;
				try (final var gcState = gc.begin()) {
					/* NoAndroid begin */
					if (this.frame.frame.border.isRounded()) {
						Shape shape = BorderRenderer.INSTANCE.getBorderShape(this.frame.frame.border, x, y, width,
								height);
						gc.clip(shape);
					}
					/* NoAndroid end */
					if (overflows) {
						gc.clip(new Rectangle2D.Double(x, y, width, height));
					}
					gc.transform(at);
					gc.drawImage(this.image);
				}
			}
		}

		private double[] fitRect(final double width, final double height) {
			return objectFitRect(this.objectFit, this.objectPosition, this.image.getWidth(), this.image.getHeight(),
					width, height);
		}
	}

	/**
	 * SPEC css-images-3 object-fit/object-position: 内容ボックス
	 * (width×height)への実描画矩形{dx, dy, drawWidth, drawHeight}を
	 * 返します。描画({@code ReplacedBoxDrawable})とリンク注釈の座標変換
	 * ({@code AbstractVisitor})が同じ幾何を共有するための単一の計算です。
	 * 呼び出し側で寸法が正であることを確認してください。
	 */
	public static double[] objectFitRect(final ObjectFitMode objectFit, final Offset objectPosition,
			final double imageWidth, final double imageHeight, final double width, final double height) {
		// 実描画寸法(concrete object size)
		final double drawWidth, drawHeight;
		switch (objectFit) {
		case CONTAIN: {
			double s = Math.min(width / imageWidth, height / imageHeight);
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		case COVER: {
			double s = Math.max(width / imageWidth, height / imageHeight);
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		case NONE:
			drawWidth = imageWidth;
			drawHeight = imageHeight;
			break;
		case SCALE_DOWN: {
			double s = Math.min(1, Math.min(width / imageWidth, height / imageHeight));
			drawWidth = imageWidth * s;
			drawHeight = imageHeight * s;
			break;
		}
		default:
			drawWidth = width;
			drawHeight = height;
			break;
		}
		// object-positionによる余白(負にもなる)への割り付け
		final double dx = positionOffset(objectPosition.getX(), objectPosition.getXRatio(),
				objectPosition.getXType(), width - drawWidth);
		final double dy = positionOffset(objectPosition.getY(), objectPosition.getYRatio(),
				objectPosition.getYType(), height - drawHeight);
		return new double[] { dx, dy, drawWidth, drawHeight };
	}

	private static double positionOffset(double value, double ratio, LengthType type, double space) {
		switch (type) {
		case ABSOLUTE:
			return value;
		case RELATIVE:
			return value * space;
		case MIXED:
			return value + ratio * space;
		default:
			return space / 2;
		}
	}

	/** 既定のobject-position(50% 50%)か。Offsetは値クラスなので成分で比較する。 */
	static boolean isCenterPosition(final Offset pos) {
		return pos.getXType() == LengthType.RELATIVE && pos.getYType() == LengthType.RELATIVE
				&& pos.getX() == .5 && pos.getY() == .5;
	}

	public final void pushGetTextSteps(final StringBuilder textBuff, java.util.Deque<GetTextStep> worklist) {
		String str = this.getReplacedParams().image.getAltString();
		if (str != null) {
			textBuff.append(str);
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d,
			java.util.Deque<TextShapeStep> worklist) {
		// ignore
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity != 0) {
			// Tagged PDF: wrap an image in a Figure element so its alternate
			// text attaches to the figure rather than the enclosing block.
			// No-op for non-image replaced content and when untagged.
			final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
			final Drawable drawable = new ReplacedBoxDrawable(pageBox, clip, this.params.opacity, transform, this.frame,
					this.params.image, this.params.objectFit, this.params.objectPosition, this.getWidth(),
					this.getHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x, y);
			pageBox.endStruct(drawer, this.params.element, structCount, x, y);
		}
	}
}
