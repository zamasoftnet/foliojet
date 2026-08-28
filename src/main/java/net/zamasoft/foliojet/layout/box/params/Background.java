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
 * <p>
 * 背景色の上に、{@code background-image}のレイヤを重ねる(2026-08-29に
 * 多層化)。レイヤは画像({@link BackgroundImage})かグラデーション
 * ({@link PaintLayer})で、CSSどおり先頭のレイヤが最前面——描画は末尾から。
 * 画像レイヤの繰り返し・位置・寸法は先頭レイヤ(longhand)の値を全画像で
 * 共有する(レイヤごとの{@code background-repeat}等は未対応、記録済み)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Background.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public class Background {
	/** 背景のレイヤ(画像かグラデーション)。 */
	public interface Layer {
	}

	/** グラデーションのレイヤ。塗る領域いっぱいに塗る。 */
	public record PaintLayer(PaintValue paint) implements Layer {
	}

	/**
	 * 背景色です。nullの場合は背景を塗りません。
	 */
	private final PaintValue backgroundPaint;

	/**
	 * 背景のレイヤです(先頭が最前面)。nullの場合はレイヤを描きません。
	 */
	private final Layer[] layers;

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

	public static Background create(PaintValue backgroundPaint, BackgroundImage backgroundImage, byte backgroundClip) {
		return create(backgroundPaint, backgroundImage == null ? null : new Layer[] { backgroundImage },
				backgroundClip);
	}

	public static Background create(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip) {
		if (layers != null && layers.length == 0) {
			layers = null;
		}
		if (backgroundPaint == null && layers == null) {
			return NULL_BACKGROUND;
		}
		return new Background(backgroundPaint, layers, backgroundClip);
	}

	private Background(PaintValue backgroundPaint, Layer[] layers, byte backgroundClip) {
		this.backgroundPaint = backgroundPaint;
		this.layers = layers;
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
	 * 最前面の背景画像を返します。無ければnull。
	 *
	 * @return
	 */
	public BackgroundImage getBackgroundImage() {
		if (this.layers != null) {
			for (final Layer layer : this.layers) {
				if (layer instanceof BackgroundImage image) {
					return image;
				}
			}
		}
		return null;
	}

	/** 背景のレイヤ(先頭が最前面)。無ければnull。 */
	public Layer[] getLayers() {
		return this.layers;
	}

	/**
	 * 表示リストのダンプ用に、グラデーションのレイヤを要約します。
	 * グラデーションが無ければ空文字列(既存goldenを変えない)。
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
				// 背景色。fill paintは自分のスコープで閉じる——アルファ付きの
				// 背景色(rgba)を外のスコープへ残すと、続く背景画像が
				// そのアルファのまま描かれる(α=0で画像が丸ごと不可視に
				// なったasahi.comの動画サムネイル、2026-08-27)
				try (final var colorState = gc.begin()) {
					this.backgroundPaint.fill(gc, shape, shape.getBounds2D());
				}
			}
			if (this.layers != null) {
				// 先頭のレイヤが最前面なので末尾から描く
				for (int i = this.layers.length - 1; i >= 0; --i) {
					final Layer layer = this.layers[i];
					try (final var layerState = gc.begin()) {
						if (layer instanceof PaintLayer paint) {
							paint.paint().fill(gc, shape, shape.getBounds2D());
						} else if (layer instanceof BackgroundImage image) {
							drawImageLayer(gc, image, shape, x, y, width, height, pbLeft, pbTop, pbRight, pbBottom);
						}
					}
				}
			}
		}
	}

	private static void drawImageLayer(GC gc, BackgroundImage backgroundImage, Shape shape, double x, double y,
			double width, double height, double pbLeft, double pbTop, double pbRight, double pbBottom)
			throws GraphicsException {
		// 背景画像描画
		double paddingWidth = width - pbLeft - pbRight;
		double paddingHeight = height - pbTop - pbBottom;

		// サイズ
		double imageWidth = 0, imageHeight = 0;
		if (backgroundImage.fit != BackgroundFit.NONE) {
			// background-size: contain/cover(2026-08-06)。箱の実寸が
			// 分かるここで初めて縦横比を比較して実寸を決める
			// (BackgroundFit/BackgroundSize.getFitのコメント参照)
			double natW = backgroundImage.image.getWidth();
			double natH = backgroundImage.image.getHeight();
			if (natW > 0 && natH > 0) {
				double scale = backgroundImage.fit == BackgroundFit.CONTAIN
						? Math.min(paddingWidth / natW, paddingHeight / natH)
						: Math.max(paddingWidth / natW, paddingHeight / natH);
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
				imageWidth = imageHeight * backgroundImage.image.getWidth() / backgroundImage.image.getHeight();
			} else if (size.getHeightType() == LengthType.AUTO) {
				imageHeight = imageWidth * backgroundImage.image.getHeight() / backgroundImage.image.getWidth();
			}
		}

		// 画像固有サイズのゼロも弾く: 0のままスケール計算(265-266行)に
		// 進むとInfinity倍率のPattern生成(BufferedImage)が
		// "Width (0) and height (0) cannot be <= 0"で変換ごと中断する
		if (!(imageWidth > 0 && imageHeight > 0 && backgroundImage.image.getWidth() > 0
				&& backgroundImage.image.getHeight() > 0)) {
			return;
		}
		double offX = pbLeft;
		double offY = pbTop;
		if (backgroundImage.attachment == BackgroundImage.ATTACHMENT_FIXED) {
			// 固定位置
			offX -= x;
			offY -= y;
		}

		// 位置
		Offset pos = backgroundImage.position;
		switch (pos.getXType()) {
		case ABSOLUTE:
			offX += pos.getX();
			break;
		case RELATIVE:
			offX += pos.getX() * (paddingWidth - imageWidth);
			break;
		case MIXED:
			// calc(100% - 10px)や4値構文(right 10px)の位置(2026-08-29)。
			// 従来はここで例外になり変換全体が失敗していた
			offX += pos.getX() + pos.getXRatio() * (paddingWidth - imageWidth);
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
		case MIXED:
			offY += pos.getY() + pos.getYRatio() * (paddingHeight - imageHeight);
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

		// 描画
		gc.clip(shape);
		switch (backgroundImage.repeat) {
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

	/**
	 * 背景が可視であればtrueを返します。
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
