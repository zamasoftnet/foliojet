package jp.cssj.homare.impl.ua;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import jp.cssj.homare.style.imposition.AbstractImposition;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;

public class SinglePageImposition extends AbstractImposition {
	protected GC gc;

	protected double actualPageWidth, actualPageHeight;

	public SinglePageImposition(UserAgent ua) {
		super(ua);
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		double actualPaperWidth, actualPaperHeight;
		switch (this.autoRotate) {
		case AUTO_ROTATE_NONE:
			actualPaperWidth = this.paperWidth;
			actualPaperHeight = this.paperHeight;
			this.gc = this.ua.nextPage(actualPaperWidth, actualPaperHeight);
			this.gc.begin();
			break;
		case AUTO_ROTATE_CONTENT:
			this.gc = this.ua.nextPage(this.paperWidth, this.paperHeight);
			this.gc.begin();
			if ((this.paperWidth > this.paperHeight) != (this.pageWidth > this.pageHeight)) {
				actualPaperWidth = this.paperHeight;
				actualPaperHeight = this.paperWidth;
				AffineTransform at = AffineTransform.getRotateInstance(-Math.PI / 2.0);
				at.translate(-this.paperHeight, 0);
				this.gc.transform(at);
			} else {
				actualPaperWidth = this.paperWidth;
				actualPaperHeight = this.paperHeight;
			}
			break;
		case AUTO_ROTATE_PAPER:
			if ((this.paperWidth > this.paperHeight) != (this.pageWidth > this.pageHeight)) {
				actualPaperWidth = this.paperHeight;
				actualPaperHeight = this.paperWidth;
			} else {
				actualPaperWidth = this.paperWidth;
				actualPaperHeight = this.paperHeight;
			}
			this.gc = this.ua.nextPage(actualPaperWidth, actualPaperHeight);
			this.gc.begin();
			break;
		default:
			throw new IllegalStateException();
		}

		switch (this.align) {
		case ALIGN_CENTER:
			this.actualPageWidth = this.pageWidth;
			this.actualPageHeight = this.pageHeight;
			break;
		case ALIGN_FIT_TO_PAPER:
			this.actualPageWidth = actualPaperWidth - this.trimLeft - this.trimRight;
			this.actualPageHeight = actualPaperHeight - this.trimTop - this.trimBottom;
			break;
		case ALIGN_PRESERVE_ASPECT_RATIO:
			this.actualPageWidth = this.pageWidth;
			this.actualPageHeight = this.pageHeight;
			double maxWidth = actualPaperWidth - this.trimLeft - this.trimRight;
			double maxHeight = actualPaperHeight - this.trimTop - this.trimBottom;
			if (this.actualPageWidth != maxWidth) {
				this.actualPageHeight = this.actualPageHeight * maxWidth / this.actualPageWidth;
				this.actualPageWidth = maxWidth;
			}
			if (this.actualPageHeight > maxHeight) {
				this.actualPageWidth = this.actualPageWidth * maxHeight / this.actualPageHeight;
				this.actualPageHeight = maxHeight;
			}

			break;
		default:
			throw new IllegalStateException();
		}
		AffineTransform at = AffineTransform.getTranslateInstance(
				(actualPaperWidth - this.actualPageWidth - this.trimLeft - this.trimRight) / 2.0,
				(actualPaperHeight - this.actualPageHeight - this.trimTop - this.trimBottom) / 2.0);
		this.gc.transform(at);

		// トンボとノンブルの描画
		this.drawMarks();
		this.drawNote(this.trimLeft, 0);

		// トンボのためにずらす
		if (this.trimLeft != 0 || this.trimTop != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(this.trimLeft, this.trimTop));
		}

		// クリッピング領域
		double bgX = -this.cuttingMargin;
		double bgY = -this.cuttingMargin;
		double bgW = this.pageWidth + this.cuttingMargin * 2.0;
		double bgH = this.pageHeight + this.cuttingMargin * 2.0;

		switch (this.align) {
		case ALIGN_CENTER: {
			// 描画可能領域のクリッピング
			if (this.clip) {
				this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW, bgH));
			}
		}
			break;
		case ALIGN_FIT_TO_PAPER:
		case ALIGN_PRESERVE_ASPECT_RATIO: {
			// 拡大率
			double hscale;
			double vscale;
			if (this.pageWidth != 0) {
				hscale = this.actualPageWidth / this.pageWidth;
			} else {
				hscale = 0;
			}
			if (this.pageHeight != 0) {
				vscale = this.actualPageHeight / this.pageHeight;
			} else {
				vscale = 0;
			}

			// 描画可能領域のクリッピング
			if (this.clip) {
				this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW * hscale, bgH * vscale));
			}

			// ページにあわせて拡大
			if (hscale != 0 && vscale != 0) {
				this.gc.transform(AffineTransform.getScaleInstance(hscale, vscale));
			}
		}
			break;
		default:
			throw new IllegalArgumentException();
		}

		return this.gc;
	}

	public void closePage() throws GraphicsException {
		this.gc.end();
		try {
			this.ua.closePage(this.gc);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
		}
	}

	protected final void drawNote(double x, double y) throws GraphicsException {
		// 情報
		if (this.note == null) {
			return;
		}
		final double paperWidth = this.actualPageWidth + this.trimLeft + this.trimRight;

		String text = this.note.format(new Object[] { String.valueOf(this.pageNumber) });
		double fontSize = this.trimTop / 6.0;
		y = y + this.trimTop - this.cuttingMargin - fontSize;
		double width = paperWidth / 2.0 - this.trimLeft;
		StyleUtils.drawText(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), fontSize, text, x, y, width);
	}

	protected final void drawMarks() throws GraphicsException {
		// トンボ
		if (this.crop) {
			this.drawCrop();
		}
		if (this.cross) {
			this.drawCross();
		}

		// 背表紙
		if (this.spineWidth > 0) {
			double middle = this.actualPageWidth / 2.0 + this.trimLeft;
			double bouter = this.actualPageHeight + this.trimTop + this.cuttingMargin;
			double touter = this.trimTop - this.cuttingMargin;
			double bottom = this.actualPageHeight + this.trimTop + this.trimBottom;
			double lbc = middle - this.spineWidth / 2.0;
			double rbc = middle + this.spineWidth / 2.0;

			this.gc.draw(new Line2D.Double(lbc, 0, lbc, touter));
			this.gc.draw(new Line2D.Double(rbc, 0, rbc, touter));
			this.gc.draw(new Line2D.Double(lbc, bottom, lbc, bouter));
			this.gc.draw(new Line2D.Double(rbc, bottom, rbc, bouter));
		}
	}

	protected final void drawCrop() throws GraphicsException {
		// コーナートンボ
		this.gc.setStrokePaint(GrayColor.BLACK);
		this.gc.setLineWidth(0.3f);
		this.gc.setLinePattern(GC.STROKE_SOLID);

		double louter = this.trimLeft - this.cuttingMargin;
		double touter = this.trimTop - this.cuttingMargin;
		double router = this.actualPageWidth + this.trimLeft + this.cuttingMargin;
		double rinner = this.actualPageWidth + this.trimLeft;
		double right = this.actualPageWidth + this.trimLeft + this.trimRight;
		double bouter = this.actualPageHeight + this.trimTop + this.cuttingMargin;
		double binner = this.actualPageHeight + this.trimTop;
		double bottom = this.actualPageHeight + this.trimTop + this.trimBottom;

		// 左上
		this.gc.draw(new Line2D.Double(0, touter, louter, touter));
		this.gc.draw(new Line2D.Double(0, this.trimTop, louter, this.trimTop));
		this.gc.draw(new Line2D.Double(louter, 0, louter, touter));
		this.gc.draw(new Line2D.Double(this.trimLeft, 0, this.trimLeft, touter));

		// 右上
		this.gc.draw(new Line2D.Double(router, touter, right, touter));
		this.gc.draw(new Line2D.Double(router, this.trimTop, right, this.trimTop));
		this.gc.draw(new Line2D.Double(router, 0, router, touter));
		this.gc.draw(new Line2D.Double(rinner, 0, rinner, touter));

		// 左下
		this.gc.draw(new Line2D.Double(0, bouter, louter, bouter));
		this.gc.draw(new Line2D.Double(0, binner, louter, binner));
		this.gc.draw(new Line2D.Double(louter, bottom, louter, bouter));
		this.gc.draw(new Line2D.Double(this.trimLeft, bottom, this.trimLeft, bouter));

		// 右下
		this.gc.draw(new Line2D.Double(router, bouter, right, bouter));
		this.gc.draw(new Line2D.Double(router, binner, right, binner));
		this.gc.draw(new Line2D.Double(router, bottom, router, bouter));
		this.gc.draw(new Line2D.Double(rinner, bottom, rinner, bouter));

	}

	protected final void drawCross() throws GraphicsException {
		// センタートンボ
		this.gc.setStrokePaint(GrayColor.BLACK);
		this.gc.setLineWidth(0.3f);
		this.gc.setLinePattern(GC.STROKE_SOLID);

		{
			double middle = this.actualPageWidth / 2.0 + this.trimLeft;
			double lmiddle = middle - (this.trimLeft + this.trimRight) / 2.0;
			double rmiddle = middle + (this.trimLeft + this.trimRight) / 2.0;
			double tmiddle = this.trimTop - this.cuttingMargin * 2.0;
			double touter = this.trimTop - this.cuttingMargin;
			double bmiddle = this.actualPageHeight + this.trimTop + this.cuttingMargin * 2.0;
			double bouter = this.actualPageHeight + this.trimTop + this.cuttingMargin;
			double bottom = this.actualPageHeight + this.trimTop + this.trimBottom;

			// 上
			this.gc.draw(new Line2D.Double(lmiddle, tmiddle, rmiddle, tmiddle));
			this.gc.draw(new Line2D.Double(middle, 0, middle, touter));

			// 下
			this.gc.draw(new Line2D.Double(lmiddle, bmiddle, rmiddle, bmiddle));
			this.gc.draw(new Line2D.Double(middle, bottom, middle, bouter));
		}

		{
			double lmiddle = this.trimLeft - this.cuttingMargin * 2.0;
			double middle = this.actualPageHeight / 2.0 + this.trimTop;
			double tmiddle = middle - (this.trimTop + this.trimBottom) / 2.0;
			double bmiddle = middle + (this.trimTop + this.trimBottom) / 2.0;
			double louter = this.trimLeft - this.cuttingMargin;
			double rmiddle = this.actualPageWidth + this.trimLeft + this.cuttingMargin * 2.0;
			double router = this.actualPageWidth + this.trimLeft + this.cuttingMargin;
			double right = this.actualPageWidth + this.trimLeft + this.trimRight;

			// 左
			this.gc.draw(new Line2D.Double(lmiddle, tmiddle, lmiddle, bmiddle));
			this.gc.draw(new Line2D.Double(0, middle, louter, middle));

			// 右
			this.gc.draw(new Line2D.Double(rmiddle, tmiddle, rmiddle, bmiddle));
			this.gc.draw(new Line2D.Double(right, middle, router, middle));
		}
	}
}
