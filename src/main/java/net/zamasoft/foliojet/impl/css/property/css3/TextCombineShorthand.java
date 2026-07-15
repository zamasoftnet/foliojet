package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.impl.css.property.Direction;
import net.zamasoft.foliojet.impl.css.property.LineHeight;
import net.zamasoft.foliojet.impl.css.property.TextIndent;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextCombineShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextCombineShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new TextCombineShorthand();

	protected TextCombineShorthand() {
		super("-cssj-text-combine");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("horizontal")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
				primitives.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
				primitives.set(LineHeight.INFO, PercentageValue.FULL);
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