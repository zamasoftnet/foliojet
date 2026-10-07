package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.CharacterSet;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.JapaneseBreakingRules;

/**
 * Line-breaking rules that apply the {@code line-break} strictness (css-text-3 §5.2)
 * on top of JLREQ kinsoku (line-breaking rules) (added 2026-08-29).
 *
 * <p>
 * pdfg2d's {@link JapaneseBreakingRules} corresponds to {@code strict}
 * (small kana, prolonged sound marks, iteration marks, middle-dot-like punctuation,
 * and hyphens are all prohibited at line starts).
 * {@code normal}/{@code loose} remove restrictions only for the characters in the specification's
 * table. Checks run before {@code requiresBefore}/{@code requiresAfter}, returning
 * {@link CharacterSet#NOTHING} for exempt characters. Only {@code atomic()} determines
 * whether a break is allowed (canSeparate is for justification only, 2026-08-22).
 * </p>
 *
 * <p>
 * The specification's writing-system condition ("for Chinese and Japanese") is treated as
 * always satisfied because this engine's language profiles use JLREQ rules for all languages
 * ({@code LanguageProfileBundle}).
 * </p>
 *
 * <ul>
 * <li>Allowed at line starts in {@code normal}: small kana and prolonged sound marks (UAX#14 CJ),
 * 〜 U+301C and ゠ U+30A0</li>
 * <li>Additionally allowed at line starts in {@code loose}: ‐ U+2010, – U+2013, iteration marks
 * 々〻ゝゞヽヾ, middle-dot-like punctuation ・：；･‼⁇⁈⁉！？, and suffixes ％℃¢°‰′″℉.
 * At line ends: immediately after prefixes ￥＄￡＃№¥$£#€, and between identical consecutive
 * inseparable characters ‥…</li>
 * </ul>
 */
public class JlreqBreakingRules extends JapaneseBreakingRules {
	/**
	 * UAX#14 CJ (small kana and prolonged sound marks, including half-width forms).
	 * Allowed at line starts in normal or looser modes.
	 */
	private static final String CJ = "ぁぃぅぇぉゕゖっゃゅょゎァィゥェォヵㇰヶㇱㇲッㇳㇴㇵㇶㇷㇸㇹㇺャュョㇻㇼㇽㇾㇿヮー"
			+ "ｧｨｩｪｫｬｭｮｯｰ";

	/** CJK hyphen-like characters. Allowed at line starts in normal or looser modes. */
	private static final String NORMAL_HYPHENS = "〜゠";

	/** Allowed at line starts in loose: hyphens, iteration marks, middle-dot-like punctuation, and suffixes. */
	private static final String LOOSE_BEFORE = "‐–" + "々〻ゝゞヽヾ" + "・：；･‼⁇⁈⁉！？"
			+ "％℃¢°‰′″℉";

	/** Prefixes after which loose allows a break. */
	private static final String LOOSE_AFTER = "￥＄￡＃№¥$£#€";

	/** Inseparable characters (UAX#14 IN) between whose identical repetitions loose allows breaks. */
	private static final String LOOSE_INSEPARABLE = "‥…";

	private final LineBreakValue level;

	public JlreqBreakingRules(final LineBreakValue level) {
		this.level = level;
	}

	public final LineBreakValue getLevel() {
		return this.level;
	}

	private boolean atLeastNormal() {
		return this.level == LineBreakValue.NORMAL || this.level == LineBreakValue.LOOSE;
	}

	@Override
	protected CharacterSet requiresBefore(final char c) {
		if (this.atLeastNormal()) {
			if (CJ.indexOf(c) != -1 || NORMAL_HYPHENS.indexOf(c) != -1) {
				return CharacterSet.NOTHING;
			}
			if (this.level == LineBreakValue.LOOSE && LOOSE_BEFORE.indexOf(c) != -1) {
				return CharacterSet.NOTHING;
			}
		}
		return super.requiresBefore(c);
	}

	@Override
	protected CharacterSet requiresAfter(final char c) {
		if (this.level == LineBreakValue.LOOSE
				&& (LOOSE_AFTER.indexOf(c) != -1 || LOOSE_INSEPARABLE.indexOf(c) != -1)) {
			return CharacterSet.NOTHING;
		}
		return super.requiresAfter(c);
	}
}
