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
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

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
		// 面付けが扱うのは仕上りサイズ(印刷面からtrimInsetだけ内側)。
		// trimInsetが0なら印刷面そのもの——既定はここを通っても値が変わらない
		final PagePlacement placement = PagePlacement.compute(this.paperWidth, this.paperHeight, this.getTrimWidth(),
				this.getTrimHeight(), trims, this.alignValue(), this.autoRotateValue());
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

		// 仕上り位置(TrimBox)と塗り足し込みの位置(BleedBox)をPDFへ書く
		// (2026-08-30、利用者報告E-6)。面付け側が仕上り線を機械的に
		// 判別できるようにするため、トンボの有無によらず常に設定する
		this.setPageBoxes(placement);

		// トンボとノンブルの描画
		this.drawMarks(trims);
		if (this.note != null) {
			String text = this.note.format(new Object[] { String.valueOf(this.pageNumber) });
			PrinterMarks.drawNote(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), text,
					this.actualPageWidth, trims);
		}

		// トンボのためにずらす。trimInsetがあるときは、印刷面の左上が
		// 仕上り線よりtrimInsetだけ外側に来るように更にずらす
		// ——原点は常に印刷面(内容の座標系)の左上
		final double ox = this.trimLeft - this.trimInset;
		final double oy = this.trimTop - this.trimInset;
		if (ox != 0 || oy != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(ox, oy));
		}

		// クリッピング領域。原点は印刷面の左上なので、trimInsetがあるときは
		// 仕上り線基準の -cuttingMargin をその分だけ右下へずらす
		// ——これが無いと**右下側の塗り足しだけが切り落とされる**
		// (実測: 156pt幅の用紙で左の帯5ptは出るのに右の帯が消えた)
		double bgX = this.trimInset - this.cuttingMargin;
		double bgY = this.trimInset - this.cuttingMargin;
		double bgW = this.getTrimWidth() + this.cuttingMargin * 2.0;
		double bgH = this.getTrimHeight() + this.cuttingMargin * 2.0;

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

	/**
	 * 仕上り位置を{@code TrimBox}、塗り足し込みの位置を{@code BleedBox}として
	 * PDFのページへ設定します(2026-08-30、利用者報告E-6)。
	 *
	 * <p>
	 * 設定しないと、面付けや印刷所の工程で「どこが仕上り線か」を機械的に
	 * 判別できず、利用者が後からPyMuPDF等で書き足すことになっていた。
	 * 座標は{@link net.zamasoft.pdfg2d.pdf.PDFPageOutput}の約束どおり
	 * <b>左上原点</b>で渡す(PDFの左下原点への変換は書き出し側が行う)。
	 * </p>
	 *
	 * <p>
	 * 内容を回転して配置する{@code output.auto-rotate}のときは、紙面座標と
	 * 内容座標の対応が単純でないため設定しない。PDF 1.3以下は
	 * TrimBox/BleedBoxを持てないので、その場合も黙って見送る。
	 * </p>
	 */
	private void setPageBoxes(final PagePlacement placement) {
		if (placement.rotateContent()) {
			return;
		}
		if (!(net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(this.gc) instanceof PDFGC pdfgc)
				|| !(pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out)) {
			return;
		}
		final double trimWidth = this.getTrimWidth(), trimHeight = this.getTrimHeight();
		if (!(trimWidth > 0 && trimHeight > 0)) {
			return;
		}
		final double paperWidth = placement.actualPaperWidth(), paperHeight = placement.actualPaperHeight();
		final double trimX = placement.centerX() + this.trimLeft;
		final double trimY = placement.centerY() + this.trimTop;
		final Rectangle2D trim = new Rectangle2D.Double(trimX, trimY, trimWidth, trimHeight);
		// 塗り足しは仕上りの外側へドブのぶん。用紙からはみ出さないよう詰める
		final double bleedX = Math.max(0, trimX - this.cuttingMargin);
		final double bleedY = Math.max(0, trimY - this.cuttingMargin);
		final Rectangle2D bleed = new Rectangle2D.Double(bleedX, bleedY,
				Math.min(paperWidth, trimX + trimWidth + this.cuttingMargin) - bleedX,
				Math.min(paperHeight, trimY + trimHeight + this.cuttingMargin) - bleedY);
		try {
			out.setBleedBox(bleed);
			out.setTrimBox(trim);
		} catch (final UnsupportedOperationException e) {
			// PDF 1.3以下。仕上り位置は表現できないので何もしない
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
