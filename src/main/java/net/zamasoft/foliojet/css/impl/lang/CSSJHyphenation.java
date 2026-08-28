package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.CharacterSet;

public class CSSJHyphenation extends JlreqBreakingRules {
	final private CSSJBreakRuleValue include;
	final private CSSJBreakRuleValue exclude;

	/**
	 * 作者指定の禁則文字の追加・除外は{@code line-break}の緩和より優先する
	 * (2026-08-29)。
	 */
	public CSSJHyphenation(CSSJBreakRuleValue include, CSSJBreakRuleValue exclude, final LineBreakValue level) {
		super(level);
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
