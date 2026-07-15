package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.impl.css.property.text.Direction;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class WritingModeShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new WritingModeShorthand();

	protected WritingModeShorthand() {
		super("-cssj-writing-mode");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("lr-tb") || ident.equals("lr") || ident.equals("horizontal-tb")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
			} else if (ident.equals("rl-tb") || ident.equals("rl")) {
				primitives.set(Direction.INFO, DirectionValue.RTL_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.TB_VALUE);
			} else if (ident.equals("tb-rl") || ident.equals("tb") || ident.equals("vertical-rl")) {
				primitives.set(Direction.INFO, DirectionValue.LTR_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.RL_VALUE);
			} else if (ident.equals("tb-lr") || ident.equals("vertical-lr")) {
				primitives.set(Direction.INFO, DirectionValue.RTL_VALUE);
				primitives.set(BlockFlow.INFO, BlockFlowValue.LR_VALUE);
			} else {
				throw new PropertyException();
			}
			return;
		}
		throw new PropertyException();
	}

}