package net.zamasoft.foliojet.ua.impl;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.foliojet.layout.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputNUpOrder;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.gc.imposition.Trims;

/**
 * N-up面付けの実装です。1枚の物理用紙に複数の論理ページを格子状に縮小配置します。
 * <p>
 * 用紙サイズが自動(fitPaper)の場合、用紙は「論理ページ+断ちしろ」と同じ大きさになり、
 * 各ページが1/Nに縮小される一般的なプリンタのN-up動作になります。
 * 用紙サイズを明示した場合はその断ちしろ内に格子を組みます。
 * 格子の行列数は、シート先頭ページの縦横比で拡大率が最大になる組み合わせを選びます。
 * </p>
 */
public class NUpImposition extends AbstractImposition {
	private final int pagesPerSheet;

	private final OutputNUpOrder order;

	private GC gc;

	/** シート全体の状態(シート開始で保存、シート終了で復元)。 */
	private GC.State sheetState;

	/** 論理ページごとの状態。 */
	private GC.State pageState;

	/** シート内の次のセル番号。 */
	private int cell;

	private int sheetNumber;

	private int rows, cols;

	private double montageWidth, montageHeight;

	public NUpImposition(UserAgent ua, int pagesPerSheet, OutputNUpOrder order) {
		super(ua);
		assert pagesPerSheet >= 2;
		this.pagesPerSheet = pagesPerSheet;
		this.order = order;
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		if (this.gc == null) {
			this.nextSheet();
		}
		final double cellWidth = this.montageWidth / this.cols;
		final double cellHeight = this.montageHeight / this.rows;
		final int index = this.cell++;
		final int col, row;
		switch (this.order) {
		case HORIZONTAL_REVERSE:
			row = index / this.cols;
			col = this.cols - 1 - index % this.cols;
			break;
		case VERTICAL:
			col = index / this.rows;
			row = index % this.rows;
			break;
		case VERTICAL_REVERSE:
			col = this.cols - 1 - index / this.rows;
			row = index % this.rows;
			break;
		default:
			row = index / this.cols;
			col = index % this.cols;
			break;
		}
		final double cellX = col * cellWidth;
		final double cellY = row * cellHeight;

		this.pageState = this.gc.begin();
		if (this.clip) {
			this.gc.clip(new Rectangle2D.Double(cellX, cellY, cellWidth, cellHeight));
		}
		// セルに合わせて縦横比を維持したまま拡縮し、中央に配置する。
		// 収めるのは仕上りサイズで、塗り足し(trimInset)はその外へはみ出す
		// ——セルのクリップで隣へ流れないようにしてある
		final double trimWidth = this.getTrimWidth();
		final double trimHeight = this.getTrimHeight();
		double scale = Math.min(cellWidth / trimWidth, cellHeight / trimHeight);
		if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
			scale = 1;
		}
		this.gc.transform(AffineTransform.getTranslateInstance(cellX + (cellWidth - scale * trimWidth) / 2.0,
				cellY + (cellHeight - scale * trimHeight) / 2.0));
		if (scale != 1) {
			this.gc.transform(AffineTransform.getScaleInstance(scale, scale));
		}
		if (this.trimInset != 0) {
			// 原点を印刷面(内容の座標系)の左上へ戻す
			this.gc.transform(AffineTransform.getTranslateInstance(-this.trimInset, -this.trimInset));
		}
		return this.gc;
	}

	private void nextSheet() throws GraphicsException {
		++this.sheetNumber;
		// シート先頭ページ時点の用紙・ページ寸法で格子を確定する
		this.montageWidth = this.paperWidth - this.trimLeft - this.trimRight;
		this.montageHeight = this.paperHeight - this.trimTop - this.trimBottom;
		this.chooseGrid();

		this.gc = this.ua.nextPage(this.paperWidth, this.paperHeight);
		this.sheetState = this.gc.begin();

		// 断ちしろ内全域をモンタージュとして扱うため、センタリング移動は不要
		final Trims trims = new Trims(this.trimTop, this.trimRight, this.trimBottom, this.trimLeft,
				this.cuttingMargin);
		if (this.crop) {
			PrinterMarks.drawCrop(this.gc, this.montageWidth, this.montageHeight, trims);
		}
		if (this.cross) {
			PrinterMarks.drawCross(this.gc, this.montageWidth, this.montageHeight, trims);
		}
		PrinterMarks.drawSpine(this.gc, this.montageWidth, this.montageHeight, trims, this.spineWidth);
		if (this.note != null) {
			String text = this.note.format(new Object[] { String.valueOf(this.sheetNumber) });
			PrinterMarks.drawNote(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), text,
					this.montageWidth, trims);
		}

		if (this.trimLeft != 0 || this.trimTop != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(this.trimLeft, this.trimTop));
		}
		this.cell = 0;
	}

	/**
	 * 格子の行列数を決めます。シート先頭ページの縦横比に対して
	 * 拡大率が最大になる因数の組み合わせを選びます。
	 */
	private void chooseGrid() {
		double best = -1;
		for (int c = 1; c <= this.pagesPerSheet; ++c) {
			if (this.pagesPerSheet % c != 0) {
				continue;
			}
			final int r = this.pagesPerSheet / c;
			final double scale = Math.min((this.montageWidth / c) / this.pageWidth,
					(this.montageHeight / r) / this.pageHeight);
			if (scale > best) {
				best = scale;
				this.cols = c;
				this.rows = r;
			}
		}
	}

	public void closePage() throws GraphicsException {
		this.pageState.close();
		this.pageState = null;
		if (this.cell >= this.pagesPerSheet) {
			this.closeSheet();
		}
	}

	public void finish() throws GraphicsException {
		// 端数ページで終わったシートを閉じる
		if (this.gc != null) {
			this.closeSheet();
		}
	}

	private void closeSheet() throws GraphicsException {
		this.sheetState.close();
		try {
			this.ua.closePage(this.gc);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
			this.sheetState = null;
		}
	}
}
