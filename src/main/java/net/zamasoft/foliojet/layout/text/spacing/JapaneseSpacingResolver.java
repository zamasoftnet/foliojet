package net.zamasoft.foliojet.layout.text.spacing;

/**
 * 和文スペーシングの解決器です(和文詰めS0、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。boxにもフォントにも
 * 依存しない純粋計算。S1でOpenTypeFont.getKerningの約物詰めと
 * TextBuilderの縦書き天付きがここへ移管される(出力不変が受入条件)。
 *
 * <p>
 * 量の単位は<b>em比</b>(呼び出し側がfont-sizeを乗じる)。「wide」は
 * 移管元の「フォント単位幅&gt;750/1000」判定(全角相当の約物か)を
 * 呼び出し側で評価して渡す。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class JapaneseSpacingResolver {

	private JapaneseSpacingResolver() {
		// static
	}

	/** 連続約物の詰め量(em比)。 */
	public static final double PAIR_TRIM = 0.5;

	/**
	 * 連続する2文字の間の詰め量(em比。0=詰めない)です。移管元
	 * (OpenTypeFont.getKerning)と同一の表:
	 * <ul>
	 * <li>開き+開き(両方wide): 0.5</li>
	 * <li>閉じ+{開き|閉じ|句読点}(両方wide): 0.5</li>
	 * <li>句読点(wide)+開き: 0.5——<b>後続開き括弧のwide判定なし</b>
	 * (移管元の演算子優先順位の癖{@code a||b&&c}をそのまま保存。
	 * 修正するときは意図的差分として扱う)</li>
	 * <li>句読点(wide)+閉じ(wide): 0.5</li>
	 * </ul>
	 * GPOSカーニングが非0の組には適用しない(呼び出し側の契約——
	 * 移管元はGPOS優先)。
	 */
	public static double pairTrim(final int prevCodePoint, final boolean prevWide, final int codePoint,
			final boolean wide) {
		final JapaneseSpacingClass prev = JapaneseSpacingClass.of(prevCodePoint);
		if (prev == JapaneseSpacingClass.OTHER || !prevWide) {
			return 0;
		}
		final JapaneseSpacingClass next = JapaneseSpacingClass.of(codePoint);
		switch (prev) {
		case OPENING:
			return next == JapaneseSpacingClass.OPENING && wide ? PAIR_TRIM : 0;
		case CLOSING:
			return next != JapaneseSpacingClass.OTHER && wide ? PAIR_TRIM : 0;
		case PUNCTUATION:
			if (next == JapaneseSpacingClass.OPENING) {
				return PAIR_TRIM; // 移管元の癖: wide判定なし
			}
			return next == JapaneseSpacingClass.CLOSING && wide ? PAIR_TRIM : 0;
		default:
			return 0;
		}
	}

	/**
	 * 縦書き行頭の天付きインデント(em比、負値)です。行頭の最初の
	 * 可視テキストが始め括弧類で始まるとき-0.5em(移管元:
	 * TextBuilderの縦書き限定処理)。それ以外は0。
	 */
	public static double verticalHeadIndent(final int firstCodePoint) {
		return JapaneseSpacingClass.of(firstCodePoint) == JapaneseSpacingClass.OPENING ? -PAIR_TRIM : 0;
	}
}
