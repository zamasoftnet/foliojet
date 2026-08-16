package net.zamasoft.foliojet.objects.barcode;

import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.graphics.TextAlignment;
import uk.org.okapibarcode.graphics.TextBox;

/**
 * 日本の書籍JANコード向けEAN-13シンボル。
 *
 * <p>書籍JANは通常の商品用EAN-13と符号化内容は同じだが、表示は異なる。
 * ガードバーを延長せず、13桁の可読文字をバー幅全体へ一続きに均等配置する。
 * これはCopper PDF 3.2の{@code ISBNCanvasLogicHandler}と、日本図書コード
 * 管理センターの表示例に合わせたもの。</p>
 */
final class BookJanSymbol extends Ean {
	private TextBox humanReadableBox;

	BookJanSymbol() {
		super(Mode.EAN13);
		this.setGuardPatternExtraHeight(0);
	}

	TextBox getHumanReadableBox() {
		return this.humanReadableBox;
	}

	@Override
	protected void plotSymbol() {
		this.humanReadableBox = null;
		super.plotSymbol();

		if (this.humanReadableLocation == HumanReadableLocation.NONE) {
			return;
		}

		// Eanは通常、先頭1桁・左6桁・右6桁の3群に分けて表示する。
		// 書籍JANでは13桁を分割せず、95モジュールのバー幅全体へ配置する。
		this.texts.clear();
		final double baseline = this.humanReadableLocation == HumanReadableLocation.TOP ? this.fontSize
				: this.symbolHeight + this.fontSize;
		this.humanReadableBox = new TextBox(0, baseline, this.symbolWidth, this.readable, TextAlignment.JUSTIFY);
		// TextBoxは全高の算出にも使われる。描画だけはBarcodeImage側で
		// Copperの実フォントメトリクスを使って行う。
		this.texts.add(this.humanReadableBox);
	}
}
