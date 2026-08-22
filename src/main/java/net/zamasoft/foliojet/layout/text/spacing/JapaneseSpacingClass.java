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

	/** CL05: 中点類(JLREQ 3.1.5——字形が前後に四分アキを含む)。 */
	MIDDLE_DOT,

	/** 対象外。 */
	OTHER;

	private static final String CL01 = "‘“（〔［｛〈《「『【⦅〖«〝";

	private static final String CL02 = "’”）〕］｝〉》」』】⦆〙〗»〟";

	private static final String CL0607 = "。．、，";

	/** 中点・全角コロン・全角セミコロン(JLREQ附属書A.5のUCSの全角形)。 */
	private static final String CL05 = "・：；";

	/** code pointを分類します。 */
	public static JapaneseSpacingClass of(final int codePoint) {
		if ((codePoint < 0x2000 && codePoint != 0x00AB && codePoint != 0x00BB) || codePoint > 0xFFFF) {
			// 対象はBMPの記号領域+ギュメ«»(JLREQ附属書A cl-01/02。
			// 旧ガードはU+2000未満を一律弾き、CL01/CL02表の«»が死んでいた)
			return OTHER;
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
		if (CL05.indexOf(c) >= 0) {
			return MIDDLE_DOT;
		}
		return OTHER;
	}
}
