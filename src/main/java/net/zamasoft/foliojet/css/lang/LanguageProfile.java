package net.zamasoft.foliojet.css.lang;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * Language-specific functionality.
 *
 * @author MIYABE Tatsuhiko
 */
public interface LanguageProfile {
	/**
	 * Returns the two-letter language code.
	 *
	 * @return
	 */
	public String getLanguage();

	/**
	 * Returns true if the given character is whitespace.
	 *
	 * @param ch
	 * @return
	 */
	public boolean isWhitespace(char ch);

	/**
	 * Counts the characters selected by the :first-letter pseudo-element.
	 *
	 * @param ch
	 *             the character array.
	 * @param off
	 *             the start position of the string.
	 * @param len
	 *             the length of the string.
	 * @return the number of characters to select starting at off.
	 */
	public int countFirstLetter(char[] ch, int off, int len);

	/**
	 * Returns a list of quotation mark pairs (Quotes).
	 *
	 * @return
	 */
	public ValueListValue getQuotes();

	
	/**
	 * Returns the hyphenation rules.
	 * @param style
	 * @return
	 */
	public TextBreakingRules getTextBreakingRules(final CSSStyle style);
}
