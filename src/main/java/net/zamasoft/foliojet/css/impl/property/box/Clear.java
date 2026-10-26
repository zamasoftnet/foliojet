package net.zamasoft.foliojet.css.impl.property.box;

import java.util.Map;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.ClearValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Clear extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Clear();

	public static ClearMode get(CSSStyle style) {
		ClearValue value = (ClearValue) style.get(INFO);
		return value.getClear();
	}

	private Clear() {
		super("clear", false, ClearValue.NONE_VALUE, Map.of("none", ClearValue.NONE_VALUE, "left", ClearValue.LEFT_VALUE,
				"right", ClearValue.RIGHT_VALUE, "start", ClearValue.START_VALUE, "end", ClearValue.END_VALUE, "both",
				ClearValue.BOTH_VALUE));
	}

}