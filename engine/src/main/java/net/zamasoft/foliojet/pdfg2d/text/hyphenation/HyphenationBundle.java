package net.zamasoft.foliojet.pdfg2d.text.hyphenation;

import net.zamasoft.foliojet.pdfg2d.text.hyphenation.impl.JapaneseHyphenation;

public final class HyphenationBundle {
	private static final Hyphenation DEFAULT_HYPHENATION = new JapaneseHyphenation();

	private HyphenationBundle() {
		// utility
	}

	public static Hyphenation getHyphenation(String lang) {
		return DEFAULT_HYPHENATION;
	}
}
