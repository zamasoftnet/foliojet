package net.zamasoft.foliojet.layout.text.spacing;

/**
 * 和文スペーシングの文字クラスです(和文詰めS0、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt Q2/S0。JLREQの文字クラスの
 * サブセット)。分類は必ずUnicode code point(int)で行う——既存の
 * charベース禁則APIと違い補助面を落とさない(答申Q2)。
 *
 * <p>
 * 文字集合は移管元(OpenTypeFont.getKerningのCL01/CL02/CL0607、
 * TextBuilderの天付きCL01)と同一。S1の出力不変移管の基準になるため、
 * ここを変えるときは必ずdisplay-list goldenの意図的差分として扱うこと。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum JapaneseSpacingClass {

	/** CL01: 始め括弧類。 */
	OPENING,

	/** CL02: 終わり括弧類。 */
	CLOSING,

	/** CL06/CL07: 句点類・読点類(移管元は区別しない)。 */
	PUNCTUATION,

	/** 対象外。 */
	OTHER;

	private static final String CL01 = "‘“（〔［｛〈《「『【⦅〖«〝";

	private static final String CL02 = "’”）〕］｝〉》」』】⦆〙〗»〟";

	private static final String CL0607 = "。．、，";

	/** code pointを分類します。 */
	public static JapaneseSpacingClass of(final int codePoint) {
		if (codePoint < 0x2000 || codePoint > 0xFFFF) {
			return OTHER; // 対象は全てBMPの記号領域
		}
		final char c = (char) codePoint;
		if (CL01.indexOf(c) >= 0) {
			return OPENING;
		}
		if (CL02.indexOf(c) >= 0) {
			return CLOSING;
		}
		if (CL0607.indexOf(c) >= 0) {
			return PUNCTUATION;
		}
		return OTHER;
	}
}
