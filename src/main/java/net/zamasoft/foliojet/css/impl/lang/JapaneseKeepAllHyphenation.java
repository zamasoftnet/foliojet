package net.zamasoft.foliojet.css.impl.lang;

import java.lang.Character.UnicodeBlock;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

public class JapaneseKeepAllHyphenation extends JlreqBreakingRules {
	/** 禁則の強さ({@code line-break})を重ねる(2026-08-29)。 */
	public JapaneseKeepAllHyphenation(final LineBreakValue level) {
		super(level);
	}

	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2) && UnicodeBlock.of(c1) != UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
			return true;
		}
		return super.atomic(c1, c2);
	}
}
