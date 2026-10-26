package net.zamasoft.foliojet.css.impl.property.box;

import java.util.Map;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.css3.BoxSizingValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BoxSizing extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BoxSizing();

	public static BoxSizingMode get(CSSStyle style) {
		return ((BoxSizingValue) style.get(INFO)).getBoxSizing();
	}

	protected BoxSizing() {
		super("box-sizing", false, BoxSizingValue.CONTENT_BOX_VALUE, Map.of("content-box",
				BoxSizingValue.CONTENT_BOX_VALUE, "border-box", BoxSizingValue.BORDER_BOX_VALUE));
	}

}