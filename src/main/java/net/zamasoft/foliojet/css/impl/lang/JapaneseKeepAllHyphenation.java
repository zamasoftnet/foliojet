package net.zamasoft.foliojet.css.impl.lang;

import java.lang.Character.UnicodeBlock;

import net.zamasoft.pdfg2d.gc.text.breaking.impl.JapaneseBreakingRules;

public class JapaneseKeepAllHyphenation extends JapaneseBreakingRules {
	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2) && UnicodeBlock.of(c1) != UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
			return true;
		}
		return super.atomic(c1, c2);
	}	
}
