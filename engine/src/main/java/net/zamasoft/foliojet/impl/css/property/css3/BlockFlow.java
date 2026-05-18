package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BlockFlow.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BlockFlow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BlockFlow();

	public static byte get(CSSStyle style) {
		return ((BlockFlowValue) style.get(INFO)).getBlockProgression();
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("tb")) {
				return BlockFlowValue.TB_VALUE;
			} else if (ident.equals("rl")) {
				return BlockFlowValue.RL_VALUE;
			} else if (ident.equals("lr")) {
				return BlockFlowValue.LR_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}