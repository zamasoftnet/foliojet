package jp.cssj.homare.impl.css.lang;

import net.zamasoft.pdfg2d.gc.text.hyphenation.impl.JapaneseHyphenation;

public class BreakAllHyphenation extends JapaneseHyphenation {
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
