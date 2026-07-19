package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.layout.text.breaking.JapaneseLineBreakRules;

public class BreakAllHyphenation extends JapaneseLineBreakRules {
	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2)) {
			return super.atomic(c1, c2);
		}
		return false;
	}
 
	public boolean canSeparate(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2)) {
			return super.canSeparate(c1, c2);
		}
		return true;
	}
	
}
