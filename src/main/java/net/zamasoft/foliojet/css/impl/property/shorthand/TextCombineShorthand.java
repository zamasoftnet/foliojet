package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.TextCombineValue;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.css.impl.property.text.TextCombineMode;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.impl.property.text.TextIndent;
import net.zamasoft.foliojet.css.impl.property.text.LetterSpacing;
import net.zamasoft.foliojet.css.impl.property.text.WordSpacing;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextCombineShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextCombineShorthand();

	protected TextCombineShorthand() {
		super("-cssj-text-combine");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			// all is a value of text-combine-upright (the standard name); horizontal is a value
			// of -cssj-text-combine/-epub-text-combine (2026-08-02).
			// Both mean tate-chu-yoko, so use the same processing. The specified
			// digits <integer> is unsupported.
			if (ident.is("none")) {
				// Initial value none (2026-09-04, user report: `body.horizontal .tcy { text-combine-upright: none }`
				// failed with 2816). Only disables tate-chu-yoko; leaves longhands such as direction and line pitch unchanged.
				primitives.set(TextCombineMode.INFO, TextCombineValue.NONE_VALUE);
			} else if (ident.is("horizontal") || ident.is("all")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
				primitives.set(WritingModeVariant.INFO, WritingModeVariantValue.NORMAL_VALUE);
				primitives.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(LineHeight.INFO, PercentageValue.FULL);
				// **Disable letter and word spacing inside tate-chu-yoko** (2026-08-11).
				// The combined digits are "one character" fitted into a single-character frame.
				// Inherited letter spacing adds trailing space and shifts the text left inside the frame
				// (the 2 in "第2部" on a book's part title page was shifted left).
				primitives.set(LetterSpacing.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(WordSpacing.INFO, AbsoluteLengthValue.ZERO);
				// **all and horizontal differ in width handling** (2026-08-11). all fits into
				// 1em (css-writing-modes-3 §9.1), while horizontal retains
				// the natural width. The four expanded properties lose this distinction,
				// so carry it in an internal property.
				primitives.set(TextCombineMode.INFO,
						ident.is("all") ? TextCombineValue.ALL_VALUE : TextCombineValue.HORIZONTAL_VALUE);
			} else {
				throw new PropertyException();
			}
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
		} else {
			throw new PropertyException();
		}
	}

}
