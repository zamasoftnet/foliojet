package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import org.w3c.dom.svg.SVGPreserveAspectRatio;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
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
import net.zamasoft.foliojet.layout.part.CenteredImage;

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

	/**
	 * 再生用の新品インスタンスを返します(P0。外部レビュー指摘)。
	 * 再生の隔離原則「再生は新品のボックス木を作る」に対し、Replaced
	 * イベントだけが同一インスタンスの再演で、計測(scratch)再生が
	 * 生きているボックスの frame/size を変異させ得た。params/pos は
	 * 共有(記録後不変)、幾何は再生ごとに新規。
	 */
	public final AbstractReplacedBox newReplayInstance() {
		return this.newReplayInstance(this.params);
	}

	/**
	 * 指定の params で再生用の新品インスタンスを返します(E-6増分3b-3、
	 * 2026-07-24)。{@code params.image}が{@link ReplacedBoxImage}
	 * (calculateSize が back-reference を画像へ書き込む)の場合、
	 * replay 駆動側が複製画像入りの独立 params を渡してライブ側の
	 * 画像状態を隔離するための注入点。pos は共有(記録後不変)。
	 */
	public abstract AbstractReplacedBox newReplayInstance(ReplacedParams params);

	public final void calculateSize(final double refWidth, final double refHeight, final double refMaxWidth, final double refMaxHeight) {
		double width = LayoutUtils.computeDimensionWidth(this.params.size, refWidth);
		double height = LayoutUtils.computeDimensionHeight(this.params.size, refHeight);

		if (this.params.image instanceof ReplacedBoxImage) {
			((ReplacedBoxImage) this.params.image).setReplacedBox(this, width, height);
		}
		// SPEC CSS2.1 10.3.2
		if (LayoutUtils.isNone(width) && LayoutUtils.isNone(height)) {
			// 両方が不確定
			width = this.params.image.getWidth();
			height = this.params.image.getHeight();
		} else if (LayoutUtils.isNone(width)) {
			// 幅が不確定
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				height -= this.frame.getBorderHeight();
			}
			double intrinsicWidth = this.params.image.getWidth();
			double intrinsicHeight = this.params.image.getHeight();
			if (intrinsicHeight != 0) {
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
			if (intrinsicWidth != 0) {
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

		public ReplacedBoxDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				AbsoluteRectFrame frame, Image image, double width, double height) {
			super(pageBox, clip, opacity, transform, frame, width, height, null);
			this.image = image;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			super.innerDraw(gc, x, y);
			x += this.frame.getFrameLeft();
			y += this.frame.getFrameTop();
			double width = this.width - this.frame.getFrameWidth();
			double height = this.height - this.frame.getFrameHeight();
			if (width > 0 && height > 0) {
				double imageWidth = this.image.getWidth();
				double imageHeight = this.image.getHeight();
				final AffineTransform at = AffineTransform.getTranslateInstance(x, y);
				final Image image;
				SVGPreserveAspectRatio preserveAspectRatio = null;
				if (preserveAspectRatio != null && preserveAspectRatio.getAlign() == SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_XMIDYMID) {
					image = new CenteredImage(this.image, width, height);
				} else {
					at.scale(width / imageWidth, height / imageHeight);
					image = this.image;
				}
				try (final var gcState = gc.begin()) {
					/* NoAndroid begin */
					if (this.frame.frame.border.isRounded()) {
						Shape shape = BorderRenderer.INSTANCE.getBorderShape(this.frame.frame.border, x, y, width,
								height);
						gc.clip(shape);
					}
					/* NoAndroid end */
					gc.transform(at);
					gc.drawImage(image);
				}
			}
		}
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
					this.params.image, this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
			pageBox.endStruct(drawer, this.params.element, structCount, x, y);
		}
	}
}
