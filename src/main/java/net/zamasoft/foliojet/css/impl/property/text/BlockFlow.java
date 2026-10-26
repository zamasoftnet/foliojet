package net.zamasoft.foliojet.css.impl.property.text;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.BlockFlowValue;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * @author MIYABE Tatsuhiko
 */
public class BlockFlow extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BlockFlow();

	public static WritingMode get(CSSStyle style) {
		return ((BlockFlowValue) style.get(INFO)).getWritingMode();
	}

	protected BlockFlow() {
		super("-cssj-block-flow", true, BlockFlowValue.TB_VALUE, Map.of("tb", BlockFlowValue.TB_VALUE, "rl",
				BlockFlowValue.RL_VALUE, "lr", BlockFlowValue.LR_VALUE));
	}

}