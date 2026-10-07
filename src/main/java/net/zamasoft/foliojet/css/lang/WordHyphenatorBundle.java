package net.zamasoft.foliojet.css.lang;

import java.util.Locale;

import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * Resolves language-specific word hyphenators (Liang algorithm) for hyphens:auto.
 * Returns null for languages without patterns.
 *
 * @author MIYABE Tatsuhiko
 */
public final class WordHyphenatorBundle {
	private static final Hyphenator ENGLISH = Hyphenator.english();

	private WordHyphenatorBundle() {
		// utility
	}

	public static Hyphenator getHyphenator(Locale lang) {
		if (lang != null && "en".equals(lang.getLanguage())) {
			return ENGLISH;
		}
		return null;
	}
}
