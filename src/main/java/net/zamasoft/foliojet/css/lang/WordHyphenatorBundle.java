package net.zamasoft.foliojet.css.lang;

import java.util.Locale;

import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * hyphens:auto のための言語別分綴器(Liangアルゴリズム)の解決です。
 * パターンを持たない言語には null を返します。
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
