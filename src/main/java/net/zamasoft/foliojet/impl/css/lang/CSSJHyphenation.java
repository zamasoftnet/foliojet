package net.zamasoft.foliojet.impl.css.lang;

import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.foliojet.layout.text.breaking.CharacterSet;
import net.zamasoft.foliojet.layout.text.breaking.JapaneseLineBreakRules;

public class CSSJHyphenation extends JapaneseLineBreakRules {
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
