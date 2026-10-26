package net.zamasoft.foliojet.css.impl.property.box;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.VisibilityValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Visibility extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Visibility();

	public static byte get(CSSStyle style) {
		VisibilityValue value = (VisibilityValue) style.get(INFO);
		return value.getVisibility();
	}

	protected Visibility() {
		super("visibility", true, VisibilityValue.VISIBLE_VALUE, Map.of("visible", VisibilityValue.VISIBLE_VALUE,
				"hidden", VisibilityValue.HIDDEN_VALUE, "collapse", VisibilityValue.COLLAPSE_VALUE));
	}
}