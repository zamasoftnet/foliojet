package net.zamasoft.foliojet.layout.box.params;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.w3c.dom.svg.SVGPreserveAspectRatio;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.layout.part.CenteredImage;
import net.zamasoft.pdfg2d.gc.paint.Pattern;

/**
 * 背景です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Background.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public class Background {
	/**
	 * 背景色です。nullの場合は背景を塗りません。
	 */
	private final PaintValue backgroundPaint;

	/**
	 * 背景画像です。nullの場合は背景画像を描きません。
	 */
	private final BackgroundImage backgroundImage;

	public static final byte BORDER_BOX = 1;

	public static final byte PADDING_BOX = 2;

	public static final byte CONTENT_BOX = 3;

	public static final byte TEXT = 4;

	/**
	 * 背景の切り取り方法。
	 */
	private final byte backgroundClip;

	/**
	 * 無地の背景です。
	 */
	public static final Background NULL_BACKGROUND = new Background(null, null, BORDER_BOX);

	public static Background create(PaintValue backgroundPaint, BackgroundImage backgroundImage, byte backgroundCiip) {
		if (backgroundPaint == null && backgroundImage == null) {
			return NULL_BACKGROUND;
		}
		return new Background(backgroundPaint, backgroundImage, backgroundCiip);
	}

	private Background(PaintValue backgroundPaint, BackgroundImage backgroundImage, byte backgroundClip) {
		this.backgroundPaint = backgroundPaint;
		this.backgroundImage = backgroundImage;
		this.backgroundClip = backgroundClip;
	}

	/**
	 * 背景色を返します。
	 * 
	 * @return
	 */
	public PaintValue getBackgroundPaint() {
		return this.backgroundPaint;
	}

	/**
	 * 背景画像を返します。
	 * 
	 * @return
	 */
	public BackgroundImage getBackgroundImage() {
		return this.backgroundImage;
	}

	/**
	 * 背景の切り抜き方法を返します。
	 * 
	 * @return
	 */
	public byte getBackgroundClip() {
		return this.backgroundClip;
	}

	/**
	 * 背景を描画します。
	 * 
	 * @param gc
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param border TODO
	 * @throws GraphicsException TODO
	 */
	public void draw(GC gc, double x, double y, double width, double height, RectBorder border, Insets padding,
			Shape textClip) throws GraphicsException {
		/* NoAndroid begin */
		double pbLeft = border == null ? 0 : border.getLeft().width;
		double pbTop = border == null ? 0 : border.getTop().width;
		double pbRight = border == null ? 0 : border.getRight().width;
		double pbBottom = border == null ? 0 : border.getBottom().width;
		double ppLeft = padding == null ? 0 : padding.getLeft();
		double ppTop = padding == null ? 0 : padding.getTop();
		double ppRight = padding == null ? 0 : padding.getRight();
		double ppBottom = padding == null ? 0 : padding.getBottom();

		final Shape shape;
		if (this.backgroundClip == TEXT && textClip != null) {
			shape = textClip;
		} else if (border == null) {
			switch (this.backgroundClip) {
			case TEXT:
			case BORDER_BOX:
			case PADDING_BOX:
				shape = new Rectangle2D.Double(x, y, width, height);
				break;
			case CONTENT_BOX:
				shape = new Rectangle2D.Double(x + ppLeft, y + ppTop, width - ppLeft - ppRight,
						height - ppTop - ppBottom);
				break;
			default:
				throw new IllegalStateException(Byte.toString(this.backgroundClip));
			}
		} else {
			switch (this.backgroundClip) {
			case TEXT:
			case BORDER_BOX:
				shape = BorderRenderer.INSTANCE.getBorderShape(border, x, y, width, height);
				break;
			case PADDING_BOX:
				shape = new Rectangle2D.Double(x + pbLeft, y + pbTop, width - pbLeft - pbRight,
						height - pbTop - pbBottom);
				break;
			case CONTENT_BOX:
				shape = new Rectangle2D.Double(x + pbLeft + ppLeft, y + pbTop + ppTop,
						width - pbLeft - pbRight - ppLeft - ppRight, height - pbTop - pbBottom - ppTop - ppBottom);
				break;
			default:
				throw new IllegalStateException(Byte.toString(this.backgroundClip));
			}
		}
		/* NoAndroid end */
		/* Android begin *//*
							 * jp.cssj.cr.compat.XPath shape; if (border == null) { shape = new
							 * jp.cssj.cr.compat.XPath(); shape.addRect(new XRectF(x, y, width, height),
							 * android.graphics.Path.Direction.CW); } else { shape =
							 * BorderRenderer.INSTANCE.getBorderXRectF (border, x, y, width, height);
							 * }
							 *//* Android end */
		try (final var gcState = gc.begin()) {
			if (this.backgroundPaint != null) {
				// 背景色
				gc.setFillPaint(this.backgroundPaint.getPaint(shape.getBounds()));
				gc.fill(shape);
			}
			if (this.backgroundImage != null) {
				// 背景画像描画
				double paddingWidth = width - pbLeft - pbRight;
				double paddingHeight = height - pbTop - pbBottom;

				// サイズ
				double imageWidth = 0, imageHeight = 0;
				Dimension size = this.backgroundImage.size;
				switch (size.getWidthType()) {
				case ABSOLUTE:
					imageWidth = size.getWidth();
					break;
				case RELATIVE:
					imageWidth = size.getWidth() * paddingWidth;
					break;
				case AUTO:
					break;
				default:
					throw new IllegalStateException();
				}
				switch (size.getHeightType()) {
				case ABSOLUTE:
					imageHeight = size.getHeight();
					break;
				case RELATIVE:
					imageHeight = size.getHeight() * paddingHeight;
					break;
				case AUTO:
					break;
				default:
					throw new IllegalStateException();
				}
				if (size.getWidthType() == LengthType.AUTO) {
					if (size.getHeightType() == LengthType.AUTO) {
						throw new IllegalStateException();
					}
					imageWidth = imageHeight * this.backgroundImage.image.getWidth()
							/ this.backgroundImage.image.getHeight();
				} else if (size.getHeightType() == LengthType.AUTO) {
					imageHeight = imageWidth * this.backgroundImage.image.getHeight()
							/ this.backgroundImage.image.getWidth();
				}

				if (imageWidth > 0 && imageHeight > 0) {
					double offX = pbLeft;
					double offY = pbTop;
					if (this.backgroundImage.attachment == BackgroundImage.ATTACHMENT_FIXED) {
						// 固定位置
						offX -= x;
						offY -= y;
					}

					// 位置
					Offset pos = this.backgroundImage.position;
					switch (pos.getXType()) {
					case ABSOLUTE:
						offX += pos.getX();
						break;
					case RELATIVE:
						offX += pos.getX() * (paddingWidth - imageWidth);
						break;
					case AUTO:
					default:
						throw new IllegalStateException();
					}
					switch (pos.getYType()) {
					case ABSOLUTE:
						offY += pos.getY();
						break;
					case RELATIVE:
						offY += pos.getY() * (paddingHeight - imageHeight);
						break;
					case AUTO:
					default:
						throw new IllegalStateException();
					}

					final double sx;
					final double sy;
					final Image image;
				
					SVGPreserveAspectRatio preserveAspectRatio = null;
					if (preserveAspectRatio != null && preserveAspectRatio.getAlign() == SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_XMIDYMID) {
						sx = sy = 1;
						image = new CenteredImage(this.backgroundImage.image, imageWidth, imageHeight);
					} else {
						sx = imageWidth / this.backgroundImage.image.getWidth();
						sy = imageHeight / this.backgroundImage.image.getHeight();
						image = this.backgroundImage.image;
					}

					// 描画
					gc.clip(shape);
					switch (this.backgroundImage.repeat) {
					case BackgroundImage.REPEAT_NO: {
						// 繰り返しなし
						double tx = x + offX;
						double ty = y + offY;
						AffineTransform at = new AffineTransform(sx, 0, 0, sy, tx, ty);
					try (final var gcState2 = gc.begin()) {
							gc.transform(at);
							gc.drawImage(image);
					}
					}
						break;

					case BackgroundImage.REPEAT_X: {
						// 横方法繰り返し
					try (final var gcState2 = gc.begin()) {
							double tx = (x + offX) % imageWidth;
							double ty = y + offY;
							AffineTransform at = AffineTransform.getTranslateInstance(tx, ty);
							at.scale(sx, sy);

							Pattern pattern = new Pattern(image, at);
							gc.setFillPaint(pattern);
							Rectangle2D rect = new Rectangle2D.Double(x, ty, width, imageHeight);
							gc.fill(rect);
					}
					}
						break;

					case BackgroundImage.REPEAT_Y: {
						// 縦方向繰り返し
					try (final var gcState2 = gc.begin()) {
							double tx = x + offX;
							double ty = (y + offY) % imageHeight;
							AffineTransform at = AffineTransform.getTranslateInstance(tx, ty);
							at.scale(sx, sy);

							Pattern pattern = new Pattern(image, at);
							gc.setFillPaint(pattern);
							Rectangle2D rect = new Rectangle2D.Double(tx, y, imageWidth, height);
							gc.fill(rect);
					}
					}
						break;

					case BackgroundImage.REPEAT: {
						// タイリング
					try (final var gcState2 = gc.begin()) {
							double tx = (x + offX) % imageWidth;
							double ty = (y + offY) % imageHeight;
							AffineTransform at = AffineTransform.getTranslateInstance(tx, ty);
							at.scale(sx, sy);

							Pattern pattern = new Pattern(image, at);
							gc.setFillPaint(pattern);
							Rectangle2D rect = new Rectangle2D.Double(x, y, width, height);
							gc.fill(rect);
					}
					}
						break;

					default:
						throw new IllegalStateException();
					}
				}
			}
		}
	}

	/**
	 * 背景が可視であればtrueを返します。
	 * 
	 * @return
	 */
	public boolean isVisible() {
		return this.getBackgroundPaint() != null || this.getBackgroundImage() != null;
	}

	public String toString() {
		return super.toString() + "[paint=" + this.getBackgroundPaint() + ",image=" + this.getBackgroundImage() + "]";
	}
}
