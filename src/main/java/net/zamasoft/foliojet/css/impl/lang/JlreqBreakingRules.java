package net.zamasoft.foliojet.css.impl.lang;

import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.CharacterSet;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.JapaneseBreakingRules;

/**
 * JLREQの禁則に{@code line-break}の強さ(css-text-3 §5.2)を重ねた
 * 行分割規則です(2026-08-29新設)。
 *
 * <p>
 * pdfg2dの{@link JapaneseBreakingRules}は{@code strict}に相当する
 * (拗促音・長音・繰返し記号・中点類・ハイフン類が全て行頭禁則)。
 * {@code normal}/{@code loose}は仕様の表にある文字だけ禁則から外す
 * ——判定は{@code requiresBefore}/{@code requiresAfter}の手前で行い、
 * 外す文字には{@link CharacterSet#NOTHING}を返す。分割の可否は
 * {@code atomic()}だけが決める(canSeparateはjustify専用、2026-08-22)。
 * </p>
 *
 * <p>
 * 仕様の書字系条件(「中国語・日本語のとき」)は、本エンジンの
 * 言語プロファイルが全言語でJLREQ規則を使っている(
 * {@code LanguageProfileBundle})ため常に満たすものとして扱う。
 * </p>
 *
 * <ul>
 * <li>{@code normal}で許す行頭: 小書き仮名・長音(UAX#14 CJ)、
 * 〜 U+301C・゠ U+30A0</li>
 * <li>{@code loose}でさらに許す行頭: ‐ U+2010・– U+2013、繰返し記号
 * 々〻ゝゞヽヾ、中点類 ・：；･‼⁇⁈⁉！？、接尾辞 ％℃¢°‰′″℉。
 * 行末: 接頭辞 ￥＄￡＃№¥$£#€ の直後、分離禁止文字 ‥… の同字連続の間</li>
 * </ul>
 */
public class JlreqBreakingRules extends JapaneseBreakingRules {
	/** UAX#14のCJ(小書き仮名・長音。半角形も)。normal以上で行頭を許す。 */
	private static final String CJ = "ぁぃぅぇぉゕゖっゃゅょゎァィゥェォヵㇰヶㇱㇲッㇳㇴㇵㇶㇷㇸㇹㇺャュョㇻㇼㇽㇾㇿヮー"
			+ "ｧｨｩｪｫｬｭｮｯｰ";

	/** CJK類のハイフン様文字。normal以上で行頭を許す。 */
	private static final String NORMAL_HYPHENS = "〜゠";

	/** looseで行頭を許す: ハイフン・繰返し記号・中点類・接尾辞。 */
	private static final String LOOSE_BEFORE = "‐–" + "々〻ゝゞヽヾ" + "・：；･‼⁇⁈⁉！？"
			+ "％℃¢°‰′″℉";

	/** looseで直後の分割を許す接頭辞。 */
	private static final String LOOSE_AFTER = "￥＄￡＃№¥$£#€";

	/** looseで同字連続の間の分割を許す分離禁止文字(UAX#14 IN)。 */
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
