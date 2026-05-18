package net.zamasoft.foliojet.impl.css.lang;

import java.lang.Character.UnicodeBlock;

import net.zamasoft.foliojet.pdfg2d.text.hyphenation.impl.JapaneseHyphenation;

public class JapaneseKeepAllHyphenation extends JapaneseHyphenation {
	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2) && UnicodeBlock.of(c1) != UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
			return true;
		}
		return super.atomic(c1, c2);
	}	
}
