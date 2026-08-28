package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;

/**
 * {@code line-break: anywhere}の行分割規則です(css-text-3 §5.2、
 * 2026-08-29新設)。
 *
 * <p>
 * 全ての文字の間を分割候補にする——約物の前後も、欧文単語の途中も、
 * 禁則を一切見ない({@code word-break: break-all}は約物の禁則を残す
 * ので、それより緩い)。{@code canSeparate}(justifyのアキ配分)は
 * JLREQのまま。
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
