package net.zamasoft.foliojet.css.impl.property.text;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class WordBreak extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordBreak();

	public static byte get(CSSStyle style) {
		return ((WordBreakValue) style.get(INFO)).getWordBreak();
	}

	protected WordBreak() {
		super("word-break", true, WordBreakValue.NORMAL_VALUE, Map.of("normal", WordBreakValue.NORMAL_VALUE, "break-all",
				WordBreakValue.BREAK_ALL_VALUE, "keep-all", WordBreakValue.KEEP_ALL_VALUE, "break-word",
				WordBreakValue.BREAK_WORD_VALUE));
	}

}