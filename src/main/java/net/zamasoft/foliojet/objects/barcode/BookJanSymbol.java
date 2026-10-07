package net.zamasoft.foliojet.objects.barcode;

import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.graphics.TextAlignment;
import uk.org.okapibarcode.graphics.TextBox;

/**
 * EAN-13 symbol for Japanese Book JAN codes.
 *
 * <p>Book JAN codes encode the same content as ordinary product EAN-13 codes, but display it
 * differently. Guard bars are not extended; the 13 human-readable digits are distributed
 * uniformly in one continuous sequence across the entire bar width.
 * This matches Copper PDF 3.2's {@code ISBNCanvasLogicHandler} and the display examples
 * from the Japan ISBN Agency.</p>
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

		// Ean normally displays three groups: the first digit, six left digits, and six right digits.
		// Book JAN places all 13 digits without grouping across the full 95-module bar width.
		this.texts.clear();
		final double baseline = this.humanReadableLocation == HumanReadableLocation.TOP ? this.fontSize
				: this.symbolHeight + this.fontSize;
		this.humanReadableBox = new TextBox(0, baseline, this.symbolWidth, this.readable, TextAlignment.JUSTIFY);
		// TextBox is also used to calculate total height. Only the drawing is performed in BarcodeImage
		// using Copper's actual font metrics.
		this.texts.add(this.humanReadableBox);
	}
}
