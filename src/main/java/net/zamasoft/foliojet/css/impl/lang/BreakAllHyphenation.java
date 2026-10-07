package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

public class BreakAllHyphenation extends JlreqBreakingRules {
	/** Applies the kinsoku (line-breaking rules) strictness ({@code line-break}) on top (2026-08-29). */
	public BreakAllHyphenation(final LineBreakValue level) {
		super(level);
	}

	/**
	 * Allows breaks even within Latin words, but retains kinsoku (line-breaking rules) for punctuation
	 * (2026-10-06, jigensha report: half-width "!?", ",", and ".)" appeared at line starts,
	 * but full-width "！？", "。", and "）" did not). css-text-3 break-all only treats letters within
	 * words like Japanese characters; it does not remove line-start or line-end restrictions
	 * (UAX #14 CL, EX, IS, OP, etc.).
	 */
	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2)) {
			return super.atomic(c1, c2);
		}
		// Line-start restrictions (closing brackets, punctuation, exclamation marks, etc.).
		if (this.requiresBefore(c2).contains(c1)) {
			return true;
		}
		// Line-end rules (opening brackets/quotes). break-all allows non-Latin characters after Latin letters/digits.
		final int type = Character.getType(c1);
		return (type == Character.START_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION)
				&& this.requiresAfter(c1).contains(c2);
	}

	public boolean canSeparate(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2)) {
			return super.canSeparate(c1, c2);
		}
		return true;
	}

}
