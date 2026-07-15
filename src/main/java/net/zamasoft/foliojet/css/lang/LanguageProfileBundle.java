package net.zamasoft.foliojet.css.lang;

import java.util.Locale;

import net.zamasoft.foliojet.impl.css.lang.LanguageProfile_ja;

/**
 * @author MIYABE Tatsuhiko
 */
public final class LanguageProfileBundle {
	private LanguageProfileBundle() {
		// unused
	}

	private static LanguageProfile lp = new LanguageProfile_ja();

	public static LanguageProfile getLanguageProfile(Locale lang) {
		return lp;
	}
}
