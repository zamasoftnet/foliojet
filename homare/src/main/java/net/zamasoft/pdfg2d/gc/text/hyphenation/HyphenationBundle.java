package net.zamasoft.pdfg2d.gc.text.hyphenation;

import net.zamasoft.pdfg2d.gc.text.hyphenation.impl.JapaneseHyphenation;

public final class HyphenationBundle {
	private static final Hyphenation DEFAULT_HYPHENATION = new JapaneseHyphenation();

	private HyphenationBundle() {
		// utility
	}

	public static Hyphenation getHyphenation(String lang) {
		return DEFAULT_HYPHENATION;
	}
}
