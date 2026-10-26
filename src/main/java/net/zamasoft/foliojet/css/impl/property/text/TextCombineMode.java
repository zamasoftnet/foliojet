package net.zamasoft.foliojet.css.impl.property.text;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.TextCombineValue;

/**
 * Internal property carrying the type of tate-chu-yoko (2026-08-11).
 *
 * <p>
 * Set when the {@code -cssj-text-combine} shorthand (including its standard-name alias {@code
 * text-combine-upright}) expands. Not intended for authors to write directly,
 * but kept parseable like other internal properties
 * (values are {@code none}/{@code horizontal}/{@code all}).
 * Not inherited; tate-chu-yoko applies only to the specified element.
 * </p>
 *
 * @see net.zamasoft.foliojet.css.value.TextCombineValue
 *
 * @author MIYABE Tatsuhiko
 */
public class TextCombineMode extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextCombineMode();

	public static byte get(CSSStyle style) {
		TextCombineValue value = (TextCombineValue) style.get(INFO);
		return value.getTextCombine();
	}

	protected TextCombineMode() {
		super("-cssj-text-combine-mode", false, TextCombineValue.NONE_VALUE, Map.of("none", TextCombineValue.NONE_VALUE,
				"horizontal", TextCombineValue.HORIZONTAL_VALUE, "all", TextCombineValue.ALL_VALUE));
	}
}
