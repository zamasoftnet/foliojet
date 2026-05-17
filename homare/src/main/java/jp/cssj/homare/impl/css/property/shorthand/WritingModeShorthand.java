package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.value.BlockFlowValue;
import jp.cssj.homare.css.value.DirectionValue;
import jp.cssj.homare.impl.css.property.Direction;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WritingModeShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WritingModeShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new WritingModeShorthand();

	protected WritingModeShorthand() {
		super("-cssj-writing-mode");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
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
			break;
		default:
			throw new PropertyException();
		}
	}

}