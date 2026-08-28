package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.TextTransformValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;
import net.zamasoft.foliojet.css.value.ext.CSSJBreakRuleValue;
import net.zamasoft.foliojet.css.impl.property.text.WordBreak;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJBreakCharacters;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJNoBreakCharacters;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
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
	 * {@code line-break}の強さ(strict/normal/loose)ごとの規則(2026-08-29)。
	 * 添字は{@link #levelIndex}。{@code anywhere}は禁則を見ないので
	 * {@code word-break}によらず1つ。
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
	 * {@code line-break}の値をJLREQ規則の強さへ写します。{@code auto}は
	 * {@code strict}相当(印刷物向けの既定——{@code LineBreak}のjavadoc)。
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

		// 空白文字は飛ばす
		for (; i < len; ++i) {
			if (!isWhitespace(ch[off + i])) {
				break;
			}
		}

		// 括弧と次の文字、数字は分割されないようにする
		short state = 0;
		for (; i < len; ++i) {
			int type = Character.getType(ch[off + i]);
			switch (state) {
			case 0: {// 初期状態
				switch (type) {
				case Character.START_PUNCTUATION:
				case Character.END_PUNCTUATION:
				case Character.OTHER_PUNCTUATION: {
					// 括弧
					state = 0;
				}
					break;

				case Character.DECIMAL_DIGIT_NUMBER:
				case Character.LETTER_NUMBER:
				case Character.OTHER_NUMBER: {
					// 数字
					state = 1;
				}
					break;

				default: {
					return i + 1;
				}
				}
			}
				break;

			case 1: {// 数字が見つかった
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

	public void transform(TextTransformValue transform, char[] ch, int off, int len) {
		switch (transform.getTextTransform()) {
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE: {
			boolean spaceBefore = true;
			for (int i = 0; i < len; ++i) {
				char c = ch[i + off];
				if (Character.isLetter(c)) {
					if (spaceBefore) {
						ch[i + off] = Character.toUpperCase(c);
					}
					spaceBefore = false;
				} else {
					spaceBefore = true;
				}
			}
		}
			break;

		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE: {
			for (int i = 0; i < len; ++i) {
				ch[i + off] = Character.toLowerCase(ch[i + off]);
			}
		}
			break;

		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE: {
			for (int i = 0; i < len; ++i) {
				ch[i + off] = Character.toUpperCase(ch[i + off]);
			}
		}
			break;

		case AbstractTextParams.TEXT_TRANSFORM_NONE:
			break;

		default:
			throw new IllegalStateException();
		}
	}

	public TextBreakingRules getTextBreakingRules(final CSSStyle style) {
		// 禁則処理。line-break(css-text-3 §5.2)の強さをword-breakの
		// 各規則へ重ねる(2026-08-29)。anywhereは禁則そのものを見ない
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