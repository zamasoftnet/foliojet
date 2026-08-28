package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

public class BreakAllHyphenation extends JlreqBreakingRules {
	/** 禁則の強さ({@code line-break})を重ねる(2026-08-29)。 */
	public BreakAllHyphenation(final LineBreakValue level) {
		super(level);
	}

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
