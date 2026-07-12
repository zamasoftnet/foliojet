package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import org.w3c.dom.svg.SVGPreserveAspectRatio;

import net.zamasoft.foliojet.style.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.ReplacedParams;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.BorderRenderer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;
import net.zamasoft.foliojet.pdfg2d.image.CenteredImage;
import net.zamasoft.pdfg2d.svg.SVGImage;

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

	public final byte getType() {
		return TYPE_REPLACED;
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
		StyleUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);
		//
		// ■ マージンの計算
		//
		StyleUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
	}

	public final void calculateSize(final double refWidth, final double refHeight, final double refMaxWidth, final double refMaxHeight) {
		double width = StyleUtils.computeDimensionWidth(this.params.size, refWidth);
		double height = StyleUtils.computeDimensionHeight(this.params.size, refHeight);

		if (this.params.image instanceof ReplacedBoxImage) {
			((ReplacedBoxImage) this.params.image).setReplacedBox(this, width, height);
		}
		// SPEC CSS2.1 10.3.2
		if (StyleUtils.isNone(width) && StyleUtils.isNone(height)) {
			// 両方が不確定
			width = this.params.image.getWidth();
			height = this.params.image.getHeight();
		} else if (StyleUtils.isNone(width)) {
			// 幅が不確定
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
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
		} else if (StyleUtils.isNone(height)) {
			// 高さが不確定
			if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
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
		} else if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
			width -= this.frame.getBorderWidth();
			height -= this.frame.getBorderHeight();
		}

		assert !StyleUtils.isNone(width);
		assert !StyleUtils.isNone(height);

		// SPEC CSS2.1 10.4
		double maxWidth = StyleUtils.computeDimensionWidth(this.params.maxSize, refMaxWidth);
		double minWidth = StyleUtils.computeDimensionWidth(this.params.minSize, refWidth);
		double maxHeight = StyleUtils.computeDimensionHeight(this.params.maxSize, refMaxHeight);
		double minHeight = StyleUtils.computeDimensionHeight(this.params.minSize, refHeight);
		if (StyleUtils.isNone(maxWidth)) {
			maxWidth = Double.MAX_VALUE;
		} else if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
			maxWidth -= this.frame.getBorderWidth();
		}

		if (StyleUtils.isNone(maxHeight)) {
			maxHeight = Double.MAX_VALUE;
		} else if (this.params.boxSizing == Types.BOX_SIZING_BORDER_BOX) {
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

	public void finishLayout(IFramedBox containerBox) {
		// 相対配置
		AbstractStaticPos pos = (AbstractStaticPos) this.getPos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = StyleUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = StyleUtils.computeOffsetY(pos.offset, containerBox);
		}

		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
		assert !StyleUtils.isNone(this.offsetX) : "Undefined offsetX";
		assert !StyleUtils.isNone(this.offsetY) : "Undefined offsetY";
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

	public final void getText(final StringBuilder textBuff) {
		String str = this.getReplacedParams().image.getAltString();
		if (str != null) {
			textBuff.append(str);
		}
	}
	
	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d) {
		// ignore
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, x, y);

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
