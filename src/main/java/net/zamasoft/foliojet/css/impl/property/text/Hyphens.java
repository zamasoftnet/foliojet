package net.zamasoft.foliojet.css.impl.property.text;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.css3.HyphensValue;

/**
 * hyphens: none | manual | auto SPEC css-text-4
 *
 * @author MIYABE Tatsuhiko
 */
public class Hyphens extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Hyphens();

	public static byte get(CSSStyle style) {
		return ((HyphensValue) style.get(INFO)).getHyphens();
	}

	protected Hyphens() {
		super("hyphens", true, HyphensValue.MANUAL_VALUE, Map.of("none", HyphensValue.NONE_VALUE, "manual",
				HyphensValue.MANUAL_VALUE, "auto", HyphensValue.AUTO_VALUE));
	}

}
