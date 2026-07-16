package net.zamasoft.foliojet.layout.text.breaking;

import net.zamasoft.foliojet.layout.text.breaking.JapaneseLineBreakRules;

public final class LineBreakRulesBundle {
	private static final LineBreakRules DEFAULT_HYPHENATION = new JapaneseLineBreakRules();

	private LineBreakRulesBundle() {
		// utility
	}

	public static LineBreakRules getLineBreakRules(String lang) {
		return DEFAULT_HYPHENATION;
	}
}
