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
			// all は text-combine-upright(標準名)の値、horizontal は
			// -cssj-text-combine/-epub-text-combine の値(2026-08-02)。
			// 縦中横として同じ意味なので同じ処理へ寄せる。仕様の
			// digits <integer> は未対応
			if (ident.is("horizontal") || ident.is("all")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
				primitives.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(LineHeight.INFO, PercentageValue.FULL);
				// **縦中横の中では字間・語間を無効にする**(2026-08-11)。
				// 組んだ数字は1文字分の枠に収める「一つの文字」なので、
				// 親の字間が中に入ると末尾に余白が付き、枠の中で左へ寄る
				// (書籍の部扉「第2部」の2が左寄りだった)
				primitives.set(LetterSpacing.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(WordSpacing.INFO, AbsoluteLengthValue.ZERO);
				// **allとhorizontalの違いは幅の扱い**(2026-08-11)。allは
				// 1em幅へ収める(css-writing-modes-3 §9.1)、horizontalは
				// 自然幅のまま。展開先の4プロパティでは区別が残らないので
				// 内部プロパティで運ぶ
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