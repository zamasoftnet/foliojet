package net.zamasoft.foliojet.layout.box.params;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.w3c.dom.svg.SVGPreserveAspectRatio;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.text.TextClip;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.layout.part.CenteredImage;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Pattern;

/**
 * Background.
 *
 * <p>
 * Stacks {@code background-image} layers over the background color (multiple layers added 2026-08-29).
 * Each layer is an image ({@link BackgroundImage}) or gradient ({@link PaintLayer}); as in CSS, the
 * first layer is frontmost, so drawing starts at the last. All image layers share the first layer's
 * longhand values for repetition, position, and size (per-layer {@code background-repeat}, etc.
 * are unsupported, as documented).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Background.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public class Background {
	/** Background layer (image or gradient). */
	public interface Layer {
	}

	/** Gradient layer. Fills the entire painting area. */
	public record PaintLayer(PaintValue paint) implements Layer {
	}

	/**
	 * Background color. null means no background fill.
	 */
	private final PaintValue backgroundPaint;

	/**
	 * Background layers (first is frontmost). null means no layers are drawn.
	 */
	private final Layer[] layers;

	public static final byte BORDER_BOX = 1;

	public static final byte PADDING_BOX = 2;

	public static final byte CONTENT_BOX = 3;

	public static final byte TEXT = 4;

	/**
	 * Background clipping mode.
	 */
	private final byte backgroundClip;

	/** Positioning reference for background images. Cycles the values if there are fewer than the layers. */
	private final byte[] backgroundOrigins;

	/** Background-layer blend modes. Cycles the values if there are fewer than the layers. */
	private final BlendMode[] backgroundBlendModes;

	/**
	 * Plain background.
	 */
	public static final Background NULL_BACKGROUND = new Background(null, null, BORDER_BOX,
			new byte[] { PADDING_BOX }, new BlendMode[] { BlendMode.NORMAL });

	public static Background create(PaintValue backgroundPaint, BackgroundImage backgroundImage, byte backgroundClip) {
		return create(backgroundPaint, backgroundImage == null ? null : new Layer[] { backgroundImage },
				backgroundClip, new byte[] { PADDING_BOX });
	}

	public static Background create(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip) {
		return create(backgroundPaint, layers, backgroundClip, new byte[] { PADDING_BOX });
	}

	public static Background create(PaintValue backgroundPaint, BackgroundImage backgroundImage, byte backgroundClip,
			byte[] backgroundOrigins) {
		return create(backgroundPaint, backgroundImage == null ? null : new Layer[] { backgroundImage },
				backgroundClip, backgroundOrigins);
	}

	public static Background create(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip,
			byte[] backgroundOrigins) {
		return create(backgroundPaint, layers, backgroundClip, backgroundOrigins,
				new BlendMode[] { BlendMode.NORMAL });
	}

	public static Background create(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip,
			byte[] backgroundOrigins, BlendMode[] backgroundBlendModes) {
		if (layers != null && layers.length == 0) {
			layers = null;
		}
		if (backgroundPaint == null && layers == null) {
			return NULL_BACKGROUND;
		}
		return new Background(backgroundPaint, layers, backgroundClip, backgroundOrigins, backgroundBlendModes);
	}

	private Background(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip, byte[] backgroundOrigins,
			BlendMode[] backgroundBlendModes) {
		this.backgroundPaint = backgroundPaint;
		this.layers = layers;
		this.backgroundClip = backgroundClip;
		if (backgroundOrigins == null || backgroundOrigins.length == 0) {
			throw new IllegalArgumentException("backgroundOrigins");
		}
		this.backgroundOrigins = backgroundOrigins.clone();
		if (backgroundBlendModes == null || backgroundBlendModes.length == 0) {
			throw new IllegalArgumentException("backgroundBlendModes");
		}
		this.backgroundBlendModes = backgroundBlendModes.clone();
	}

	/**
	 * Returns the background color.
	 *
	 * @return
	 */
	public PaintValue getBackgroundPaint() {
		return this.backgroundPaint;
	}

	/** Background layers (first is frontmost). null if absent. */
	public Layer[] getLayers() {
		return this.layers;
	}

	/**
	 * Summarizes gradient layers for display-list dumps.
	 * An empty string if there are no gradients (preserves existing golden data).
	 */
	public String describeGradients() {
		if (this.layers == null) {
			return "";
		}
		final StringBuilder s = new StringBuilder();
		for (final Layer layer : this.layers) {
			if (layer instanceof PaintLayer paint) {
				s.append(" bg=").append(paint.paint());
			}
		}
		return s.toString();
	}

	/**
	 * Returns the background clipping mode.
	 *
	 * @return
	 */
	public byte getBackgroundClip() {
		return this.backgroundClip;
	}

	/** Returns the background-image positioning reference for the specified layer. */
	public byte getBackgroundOrigin(int layer) {
		return this.backgroundOrigins[layer % this.backgroundOrigins.length];
	}

	/** Returns the specified layer's blend mode using CSS list-repetition rules. */
	public BlendMode getBackgroundBlendMode(int layer) {
		return this.backgroundBlendModes[layer % this.backgroundBlendModes.length];
	}

	/**
	 * Draws the background.
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
			TextClip textClip) throws GraphicsException {
		/* NoAndroid begin */
		double pbLeft = border == null ? 0 : border.getLeft().width;
		double pbTop = border == null ? 0 : border.getTop().width;
		double pbRight = border == null ? 0 : border.getRight().width;
		double pbBottom = border == null ? 0 : border.getBottom().width;
		double ppLeft = padding == null ? 0 : padding.getLeft();
		double ppTop = padding == null ? 0 : padding.getTop();
		double ppRight = padding == null ? 0 : padding.getRight();
		double ppBottom = padding == null ? 0 : padding.getBottom();

		// background-clip: text paints the border box (whose bounds also size the gradients) inside the text
		final Shape shape;
		if (border == null) {
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
		if (this.backgroundClip == TEXT && textClip != null) {
			gc.clipToText(textClip, g -> this.paint(g, shape, x, y, width, height, pbLeft, pbTop, pbRight, pbBottom,
					ppLeft, ppTop, ppRight, ppBottom));
		} else {
			this.paint(gc, shape, x, y, width, height, pbLeft, pbTop, pbRight, pbBottom, ppLeft, ppTop, ppRight,
					ppBottom);
		}
	}

	private void paint(final GC gc, final Shape shape, final double x, final double y, final double width,
			final double height, final double pbLeft, final double pbTop, final double pbRight, final double pbBottom,
			final double ppLeft, final double ppTop, final double ppRight, final double ppBottom)
			throws GraphicsException {
		try (final var gcState = gc.begin()) {
			if (this.backgroundPaint != null) {
				// Background color. Keep fill paint in its own scope: leaving an alpha-bearing
				// background color (rgba) in the outer scope causes the following background image
				// to use that alpha (α=0 made the entire image invisible in asahi.com video
				// thumbnails, 2026-08-27).
				try (final var colorState = gc.begin()) {
					this.backgroundPaint.fill(gc, shape, shape.getBounds2D());
				}
			}
			if (this.layers != null) {
				// The first layer is frontmost, so draw from the last.
				for (int i = this.layers.length - 1; i >= 0; --i) {
					final Layer layer = this.layers[i];
					try (final var layerState = gc.begin()) {
						final BlendMode previousBlend = gc.getBlendMode();
						final BlendMode layerBlend = this.getBackgroundBlendMode(i);
						final boolean blendChanged = layerBlend != BlendMode.NORMAL && layerBlend != previousBlend;
						if (blendChanged) {
							gc.setBlendMode(layerBlend);
						}
						try {
							if (layer instanceof PaintLayer paint) {
								paint.paint().fill(gc, shape, shape.getBounds2D());
							} else if (layer instanceof BackgroundImage image) {
								drawImageLayer(gc, image, shape, x, y, width, height, pbLeft, pbTop, pbRight,
										pbBottom, ppLeft, ppTop, ppRight, ppBottom, this.getBackgroundOrigin(i));
							}
						} finally {
							if (blendChanged) {
								gc.setBlendMode(previousBlend);
							}
						}
					}
				}
			}
		}
	}

	private static void drawImageLayer(GC gc, BackgroundImage backgroundImage, Shape shape, double x, double y,
			double width, double height, double pbLeft, double pbTop, double pbRight, double pbBottom, double ppLeft,
			double ppTop, double ppRight, double ppBottom, byte backgroundOrigin)
			throws GraphicsException {
		// Draw the background image.
		final double originX;
		final double originY;
		final double originWidth;
		final double originHeight;
		switch (backgroundOrigin) {
		case BORDER_BOX:
			originX = x;
			originY = y;
			originWidth = width;
			originHeight = height;
			break;
		case PADDING_BOX:
			originX = x + pbLeft;
			originY = y + pbTop;
			originWidth = width - pbLeft - pbRight;
			originHeight = height - pbTop - pbBottom;
			break;
		case CONTENT_BOX:
			originX = x + pbLeft + ppLeft;
			originY = y + pbTop + ppTop;
			originWidth = width - pbLeft - pbRight - ppLeft - ppRight;
			originHeight = height - pbTop - pbBottom - ppTop - ppBottom;
			break;
		default:
			throw new IllegalStateException(Byte.toString(backgroundOrigin));
		}

		// Size
		double imageWidth = 0, imageHeight = 0;
		if (backgroundImage.fit != BackgroundFit.NONE) {
			// background-size: contain/cover (2026-08-06). Only here, where the box's actual
			// size is known, compare aspect ratios and determine the actual image size
			// (see comments in BackgroundFit/BackgroundSize.getFit).
			double natW = backgroundImage.image.getWidth();
			double natH = backgroundImage.image.getHeight();
			if (natW > 0 && natH > 0) {
				double scale = backgroundImage.fit == BackgroundFit.CONTAIN
						? Math.min(originWidth / natW, originHeight / natH)
						: Math.max(originWidth / natW, originHeight / natH);
				imageWidth = natW * scale;
				imageHeight = natH * scale;
			}
		} else {
			Dimension size = backgroundImage.size;
			switch (size.getWidthType()) {
			case ABSOLUTE:
				imageWidth = size.getWidth();
				break;
			case RELATIVE:
				imageWidth = size.getWidth() * originWidth;
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
				imageHeight = size.getHeight() * originHeight;
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
				imageWidth = imageHeight * backgroundImage.image.getWidth() / backgroundImage.image.getHeight();
			} else if (size.getHeightType() == LengthType.AUTO) {
				imageHeight = imageWidth * backgroundImage.image.getHeight() / backgroundImage.image.getWidth();
			}
		}

		// Also reject zero intrinsic image sizes: proceeding to scale calculation (lines 265-266)
		// with zero creates a Pattern (BufferedImage) with an Infinity scale and aborts the entire
		// conversion with "Width (0) and height (0) cannot be <= 0".
		if (!(imageWidth > 0 && imageHeight > 0 && backgroundImage.image.getWidth() > 0
				&& backgroundImage.image.getHeight() > 0)) {
			return;
		}
		double offX = originX;
		double offY = originY;
		if (backgroundImage.attachment == BackgroundImage.ATTACHMENT_FIXED) {
			// Fixed position
			offX -= x;
			offY -= y;
		}

		// Position
		Offset pos = backgroundImage.position;
		switch (pos.getXType()) {
		case ABSOLUTE:
			offX += pos.getX();
			break;
		case RELATIVE:
			offX += pos.getX() * (originWidth - imageWidth);
			break;
		case MIXED:
			// Positions using calc(100% - 10px) or four-value syntax (right 10px) (2026-08-29).
			// Previously, an exception here failed the entire conversion.
			offX += pos.getX() + pos.getXRatio() * (originWidth - imageWidth);
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
			offY += pos.getY() * (originHeight - imageHeight);
			break;
		case MIXED:
			offY += pos.getY() + pos.getYRatio() * (originHeight - imageHeight);
			break;
		case AUTO:
		default:
			throw new IllegalStateException();
		}

		final double sx;
		final double sy;
		final Image image;

		SVGPreserveAspectRatio preserveAspectRatio = null;
		if (preserveAspectRatio != null
				&& preserveAspectRatio.getAlign() == SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_XMIDYMID) {
			sx = sy = 1;
			image = new CenteredImage(backgroundImage.image, imageWidth, imageHeight);
		} else {
			sx = imageWidth / backgroundImage.image.getWidth();
			sy = imageHeight / backgroundImage.image.getHeight();
			image = backgroundImage.image;
		}

		// Draw
		gc.clip(shape);
		switch (backgroundImage.repeat) {
		case BackgroundImage.REPEAT_NO: {
			// No repeat
			double tx = offX;
			double ty = offY;
			AffineTransform at = new AffineTransform(sx, 0, 0, sy, tx, ty);
			try (final var gcState2 = gc.begin()) {
				gc.transform(at);
				gc.drawImage(image);
			}
		}
			break;

		case BackgroundImage.REPEAT_X: {
			// Horizontal repeat
			try (final var gcState2 = gc.begin()) {
				double tx = offX % imageWidth;
				double ty = offY;
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
			// Vertical repeat
			try (final var gcState2 = gc.begin()) {
				double tx = offX;
				double ty = offY % imageHeight;
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
			// Tiling
			try (final var gcState2 = gc.begin()) {
				double tx = offX % imageWidth;
				double ty = offY % imageHeight;
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

	/**
	 * Returns true if the background is visible.
	 *
	 * @return
	 */
	public boolean isVisible() {
		return this.backgroundPaint != null || this.layers != null;
	}

	public String toString() {
		return super.toString() + "[paint=" + this.getBackgroundPaint() + ",layers="
				+ java.util.Arrays.toString(this.layers) + "]";
	}
}
