package net.zamasoft.foliojet.ua.impl;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.ua.props.OutputAutoRotate;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.foliojet.layout.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PagePlacement;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.gc.imposition.Trims;

/**
 * 1面付けの実装です。配置計算とトンボ描画は pdfg2d の
 * {@link PagePlacement} / {@link PrinterMarks} に委譲します。
 */
public class SinglePageImposition extends AbstractImposition {
	protected GC gc;

	/** nextPage() で開始し closePage() で復元する状態。 */
	protected GC.State gcState;

	protected double actualPageWidth, actualPageHeight;

	public SinglePageImposition(UserAgent ua) {
		super(ua);
	}

	private Trims trims() {
		// pdfg2d の PrinterMarks は上下左右の断ちしろとドブで動作する
		return new Trims(this.trimTop, this.trimRight, this.trimBottom, this.trimLeft, this.cuttingMargin);
	}

	private PagePlacement.Align alignValue() {
		switch (this.align) {
		case FALSE:
			return PagePlacement.Align.CENTER;
		case TRUE:
			return PagePlacement.Align.FIT_TO_PAPER;
		case PRESERVE_ASPECT_RATIO:
			return PagePlacement.Align.PRESERVE_ASPECT_RATIO;
		default:
			throw new IllegalStateException();
		}
	}

	private PagePlacement.AutoRotate autoRotateValue() {
		switch (this.autoRotate) {
		case NONE:
			return PagePlacement.AutoRotate.NONE;
		case CONTENT:
			return PagePlacement.AutoRotate.CONTENT;
		case PAPER:
			return PagePlacement.AutoRotate.PAPER;
		default:
			throw new IllegalStateException();
		}
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		final Trims trims = this.trims();
		final PagePlacement placement = PagePlacement.compute(this.paperWidth, this.paperHeight, this.pageWidth,
				this.pageHeight, trims, this.alignValue(), this.autoRotateValue());
		this.actualPageWidth = placement.actualPageWidth();
		this.actualPageHeight = placement.actualPageHeight();

		// AUTO_ROTATE_CONTENT では設定どおりの用紙のまま内容を回転する
		if (this.autoRotate == OutputAutoRotate.CONTENT) {
			this.gc = this.ua.nextPage(this.paperWidth, this.paperHeight);
		} else {
			this.gc = this.ua.nextPage(placement.actualPaperWidth(), placement.actualPaperHeight());
		}
		this.gcState = this.gc.begin();

		if (placement.rotateContent()) {
			AffineTransform at = AffineTransform.getRotateInstance(-Math.PI / 2.0);
			at.translate(-placement.actualPaperWidth(), 0);
			this.gc.transform(at);
		}

		this.gc.transform(AffineTransform.getTranslateInstance(placement.centerX(), placement.centerY()));

		// トンボとノンブルの描画
		this.drawMarks(trims);
		if (this.note != null) {
			String text = this.note.format(new Object[] { String.valueOf(this.pageNumber) });
			PrinterMarks.drawNote(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), text,
					this.actualPageWidth, trims);
		}

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
		case FALSE: {
			// 描画可能領域のクリッピング
			if (this.clip) {
				this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW, bgH));
			}
		}
			break;
		case TRUE:
		case PRESERVE_ASPECT_RATIO: {
			double hscale = placement.hscale();
			double vscale = placement.vscale();

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
		this.gcState.close();
		try {
			this.ua.closePage(this.gc);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
			this.gcState = null;
		}
	}

	protected final void drawMarks(Trims trims) throws GraphicsException {
		// トンボ
		if (this.crop) {
			PrinterMarks.drawCrop(this.gc, this.actualPageWidth, this.actualPageHeight, trims);
		}
		if (this.cross) {
			PrinterMarks.drawCross(this.gc, this.actualPageWidth, this.actualPageHeight, trims);
		}

		// 背表紙
		PrinterMarks.drawSpine(this.gc, this.actualPageWidth, this.actualPageHeight, trims, this.spineWidth);
	}
}
