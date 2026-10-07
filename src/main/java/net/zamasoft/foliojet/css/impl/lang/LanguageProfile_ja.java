package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;
import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.foliojet.css.impl.property.text.WordBreak;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJBreakCharacters;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJNoBreakCharacters;
import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.foliojet.css.impl.property.text.LineBreak;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * @author MIYABE Tatsuhiko
 */
public class LanguageProfile_ja implements LanguageProfile {
	private static final ValueListValue QUOTES = new ValueListValue(
			new Value[] { new QuotesValue("「", "」"), new QuotesValue("『", "』"), });

	/**
	 * Rules for each {@code line-break} strictness level (strict/normal/loose) (2026-08-29).
	 * Indices are given by {@link #levelIndex}. {@code anywhere} ignores kinsoku (line-breaking rules),
	 * so there is only one instance regardless of {@code word-break}.
	 */
	private final TextBreakingRules[] normalHyph = { new JlreqBreakingRules(LineBreakValue.STRICT),
			new JlreqBreakingRules(LineBreakValue.NORMAL), new JlreqBreakingRules(LineBreakValue.LOOSE) };

	private final TextBreakingRules[] breakAllHyph = { new BreakAllHyphenation(LineBreakValue.STRICT),
			new BreakAllHyphenation(LineBreakValue.NORMAL), new BreakAllHyphenation(LineBreakValue.LOOSE) };

	private final TextBreakingRules[] keepAllHyph = { new JapaneseKeepAllHyphenation(LineBreakValue.STRICT),
			new JapaneseKeepAllHyphenation(LineBreakValue.NORMAL),
			new JapaneseKeepAllHyphenation(LineBreakValue.LOOSE) };

	private final TextBreakingRules anywhereHyph = new AnywhereBreakingRules();

	/**
	 * Maps a {@code line-break} value to the JLREQ rule strictness. {@code auto} is equivalent
	 * to {@code strict} (the default for print; see the {@code LineBreak} Javadoc).
	 */
	static LineBreakValue effectiveLevel(final LineBreakValue value) {
		return value == LineBreakValue.AUTO ? LineBreakValue.STRICT : value;
	}

	private static int levelIndex(final LineBreakValue level) {
		switch (level) {
		case NORMAL:
			return 1;
		case LOOSE:
			return 2;
		default:
			return 0;
		}
	}

	public String getLanguage() {
		return "ja";
	}

	public boolean isWhitespace(char c) {
		if (c == '　' || c == 0xA0) {
			return false;
		}

		return Character.isWhitespace(c);
	}

	public int countFirstLetter(char[] ch, int off, int len) {
		int i = 0;

		// Skip whitespace.
		for (; i < len; ++i) {
			if (!isWhitespace(ch[off + i])) {
				break;
			}
		}

		// Prevent breaks between brackets and the next character, and within numbers.
		short state = 0;
		for (; i < len; ++i) {
			int type = Character.getType(ch[off + i]);
			switch (state) {
			case 0: {// Initial state
				switch (type) {
				case Character.START_PUNCTUATION:
				case Character.END_PUNCTUATION:
				case Character.OTHER_PUNCTUATION: {
					// Brackets
					state = 0;
				}
					break;

				case Character.DECIMAL_DIGIT_NUMBER:
				case Character.LETTER_NUMBER:
				case Character.OTHER_NUMBER: {
					// Digits
					state = 1;
				}
					break;

				default: {
					return i + 1;
				}
				}
			}
				break;

			case 1: {// Found a digit.
				switch (type) {
				case Character.DECIMAL_DIGIT_NUMBER:
				case Character.LETTER_NUMBER:
				case Character.OTHER_NUMBER:
					break;

				default: {
					return i;
				}
				}
			}
				break;
			}
		}
		return len;
	}

	public ValueListValue getQuotes() {
		return QUOTES;
	}

	public TextBreakingRules getTextBreakingRules(final CSSStyle style) {
		// Kinsoku processing. Apply the line-break strictness (css-text-3 §5.2) to each
		// word-break rule (2026-08-29). anywhere ignores kinsoku entirely.
		final LineBreakValue level = effectiveLevel(LineBreak.get(style));
		if (level == LineBreakValue.ANYWHERE) {
			return this.anywhereHyph;
		}
		final int index = levelIndex(level);
		switch (WordBreak.get(style)) {
		case WordBreakValue.NORMAL:
		case WordBreakValue.BREAK_WORD:
			final CSSJBreakRuleValue include = CSSJNoBreakCharacters.get(style);
			final CSSJBreakRuleValue exclude = CSSJBreakCharacters.get(style);
			if (include != CSSJBreakRuleValue.NONE_VALUE || exclude != CSSJBreakRuleValue.NONE_VALUE) {
				return new CSSJHyphenation(include, exclude, level);
			}

			return this.normalHyph[index];

		case WordBreakValue.KEEP_ALL:
			return this.keepAllHyph[index];

		case WordBreakValue.BREAK_ALL:
			return this.breakAllHyph[index];
		default:
			throw new IllegalStateException();
		}

	}
}