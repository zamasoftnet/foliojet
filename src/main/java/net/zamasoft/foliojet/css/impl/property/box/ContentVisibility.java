package net.zamasoft.foliojet.css.impl.property.box;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.ContentVisibilityValue;

/**
 * content-visibility property (css-contain-2, 2026-08-10).
 * See {@link ContentVisibilityValue} for how it is applied.
 *
 * @author MIYABE Tatsuhiko
 */
public class ContentVisibility extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ContentVisibility();

	public static byte get(CSSStyle style) {
		ContentVisibilityValue value = (ContentVisibilityValue) style.get(INFO);
		return value.getContentVisibility();
	}

	protected ContentVisibility() {
		super("content-visibility", false, ContentVisibilityValue.VISIBLE_VALUE, Map.of("visible",
				ContentVisibilityValue.VISIBLE_VALUE, "hidden", ContentVisibilityValue.HIDDEN_VALUE, "auto",
				ContentVisibilityValue.AUTO_VALUE));
	}
}
