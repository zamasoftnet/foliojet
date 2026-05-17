package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.BlockFlowValue;
import jp.cssj.homare.css.value.DirectionValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.impl.css.property.Direction;
import jp.cssj.homare.impl.css.property.LineHeight;
import jp.cssj.homare.impl.css.property.TextIndent;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextCombineShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextCombineShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextCombineShorthand();

	protected TextCombineShorthand() {
		super("-cssj-text-combine");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String ident = lu.getStringValue();
			if (ident.equals("horizontal")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
				primitives.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(LineHeight.INFO, PercentageValue.FULL);
			} else {
				throw new PropertyException();
			}
			if (lu.getNextLexicalUnit() != null) {
				throw new PropertyException();
			}
		} else {
			throw new PropertyException();
		}
	}

}