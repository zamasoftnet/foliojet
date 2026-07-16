package net.zamasoft.foliojet.impl.css.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class BlockFlow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BlockFlow();

	public static WritingMode get(CSSStyle style) {
		return ((BlockFlowValue) style.get(INFO)).getWritingMode();
	}

	protected BlockFlow() {
		super("-cssj-block-flow");
	}

	public Value getDefault(CSSStyle style) {
		return BlockFlowValue.TB_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("tb")) {
				return BlockFlowValue.TB_VALUE;
			} else if (ident.equals("rl")) {
				return BlockFlowValue.RL_VALUE;
			} else if (ident.equals("lr")) {
				return BlockFlowValue.LR_VALUE;
			}
		}
		throw new PropertyException();
	}

}