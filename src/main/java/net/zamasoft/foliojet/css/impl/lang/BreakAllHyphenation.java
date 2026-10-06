package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

public class BreakAllHyphenation extends JlreqBreakingRules {
	/** 禁則の強さ({@code line-break})を重ねる(2026-08-29)。 */
	public BreakAllHyphenation(final LineBreakValue level) {
		super(level);
	}

	/**
	 * 欧文の語の中でも割る。ただし約物の禁則は保つ(2026-10-06、jigensha の報告: 半角の「!?」「,」「.)」が
	 * 行頭に来た。全角の「！？」「。」「）」は来ない)。css-text-3 の break-all は語の中の字を和字と同じに扱うだけで、
	 * 行頭・行末の禁則(UAX #14 の CL・EX・IS・OP など)は外さない。
	 */
	public boolean atomic(char c1, char c2) {
		if (this.isCJK(c1) && this.isCJK(c2)) {
			return super.atomic(c1, c2);
		}
		// 行頭禁則(閉じ括弧・句読点・感嘆符など)
		if (this.requiresBefore(c2).contains(c1)) {
			return true;
		}
		// 行末禁則(始め括弧・始めの引用符)。欧字・数字の後ろの「次も欧字」の要求は break-all では外す
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
