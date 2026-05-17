package jp.cssj.homare.impl.css.lang;

import jp.cssj.homare.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.pdfg2d.gc.text.hyphenation.impl.CharacterSet;
import net.zamasoft.pdfg2d.gc.text.hyphenation.impl.JapaneseHyphenation;

public class CSSJHyphenation extends JapaneseHyphenation {
	final private CSSJBreakRuleValue include;
	final private CSSJBreakRuleValue exclude;

	public CSSJHyphenation(CSSJBreakRuleValue include, CSSJBreakRuleValue exclude) {
		this.include = include;
		this.exclude = exclude;
	}

	protected CharacterSet requiresBefore(char c) {
		if (this.include.getHead().indexOf(c) != -1) {
			return CharacterSet.ALL;
		}
		if (this.exclude.getHead().indexOf(c) != -1) {
			return CharacterSet.NOTHING;
		}
		return super.requiresBefore(c);
	}

	protected CharacterSet requiresAfter(char c) {
		if (this.include.getTail().indexOf(c) != -1) {
			return CharacterSet.ALL;
		}
		if (this.exclude.getTail().indexOf(c) != -1) {
			return CharacterSet.NOTHING;
		}
		return super.requiresAfter(c);
	}

}
