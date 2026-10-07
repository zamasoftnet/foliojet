package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

/**
 * Line-breaking rules for {@code line-break: anywhere} (css-text-3 §5.2,
 * added 2026-08-29).
 *
 * <p>
 * Allows breaks between all characters, including around punctuation and within Latin words,
 * without applying any kinsoku (line-breaking rules). This is less restrictive than
 * {@code word-break: break-all}, which retains punctuation restrictions.
 * {@code canSeparate} (spacing distribution for justification) still follows JLREQ.
 * </p>
 */
public class AnywhereBreakingRules extends JlreqBreakingRules {
	public AnywhereBreakingRules() {
		super(LineBreakValue.ANYWHERE);
	}

	@Override
	public boolean atomic(final char c1, final char c2) {
		return false;
	}
}
