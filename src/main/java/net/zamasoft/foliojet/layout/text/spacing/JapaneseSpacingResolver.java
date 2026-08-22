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
		case MIDDLE_DOT:
			// JLREQ 3.1.5: 中点類の後ろに始め括弧類——中点の後ろを四分アキ
			// (字形内の四分+括弧の二分=3/4を-0.5emで四分へ。終わり括弧+中点は
			// CLOSING側のnext!=OTHERで既に対象)
			return next == JapaneseSpacingClass.OPENING && wide ? PAIR_TRIM : 0;
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

	/**
	 * 行末の追い込み(T2)/ぶら下げ(H1)の許容量です(和文詰め——
	 * consult-codex-2026-07-31-text-spacing.txt T2/H1の純関数)。
	 * 行末glyphが対象約物のとき、行に収まる方を優先順(trim→hang)で
	 * 返す。対象外・どちらでも収まらないときは0(従来の追い出しへ)。
	 *
	 * @param codePoint 行末のcode point
	 * @param wide      全角相当か({@link #isWide})
	 * @param trimOff   text-spacing-trim: space-all
	 * @param hangEnd   hanging-punctuation: allow-end
	 * @param advance   行末glyphのadvance(hang量)
	 * @param fontSize  行末runのfont-size(trim量=0.5em)
	 * @param overflow  行幅超過量(lineAxis-maxLineAxis。正のとき呼ぶ)
	 */
	public static double endAllowance(final int codePoint, final boolean wide, final boolean trimOff,
			final boolean hangEnd, final double advance, final double fontSize, final double overflow) {
		if (!wide) {
			return 0;
		}
		final JapaneseSpacingClass cls = JapaneseSpacingClass.of(codePoint);
		if (cls != JapaneseSpacingClass.CLOSING && cls != JapaneseSpacingClass.PUNCTUATION
				&& cls != JapaneseSpacingClass.MIDDLE_DOT) {
			return 0;
		}
		// (1) 行末trim: 半角化で収まるなら詰める(中点はJIS X 4051の
		// 「行末中点は前四分・後ろベタ」に従い四分=0.25emのみ)
		if (!trimOff) {
			final double trim = (cls == JapaneseSpacingClass.MIDDLE_DOT ? PAIR_TRIM / 2 : PAIR_TRIM) * fontSize;
			if (overflow <= trim) {
				return trim;
			}
		}
		// (2) ぶら下げ: 句読点のみ・そのglyphの全advance
		if (hangEnd && cls == JapaneseSpacingClass.PUNCTUATION && overflow <= advance) {
			return advance;
		}
		return 0;
	}

	/** wide判定(metrics換算: font単位750/1000 ⇔ 0.75×font-size)。 */
	public static boolean isWide(final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid,
			final double fontSize) {
		return metrics.getWidth(gid) > fontSize * 0.75;
	}

	/**
	 * 組方向のinline advanceに基づくwide判定です。横組は従来どおり
	 * horizontal width、縦組はGSUB vert後glyphのvertical advanceを使う。
	 *
	 * @param direction runの組方向
	 */
	public static boolean isWide(final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid,
			final double fontSize, final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction) {
		if (direction == net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB) {
			return metrics.getAdvance(gid) > fontSize * 0.75;
		}
		return isWide(metrics, gid, fontSize);
	}

	/**
	 * 組み立て済みrun内の全隣接pairへ約物詰めをxadvanceで適用します
	 * (T1a——font層から撤去した詰めの、独自appendGlyphループ経路
	 * (RubyUnitBox・FootnoteLabelImage等)用の代替。GPOSカーニングが
	 * 非0のpairはスキップ=移管元と同じ優先)。
	 */
	public static void applyRunTrims(final net.zamasoft.pdfg2d.gc.text.TextImpl text) {
		final int glyphCount = text.getGlyphCount();
		if (glyphCount < 2) {
			return;
		}
		final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction = text.getFontStyle().getDirection();
		final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics = text.getFontMetrics();
		final double fontSize = text.getFontStyle().getSize();
		final char[] chars = text.getChars();
		final byte[] clusterLengths = text.getClusterLengths();
		final int[] gids = text.getGlyphIds();
		int charIndex = clusterLengths[0];
		int prevCp = Character.codePointBefore(chars, charIndex);
		for (int i = 1; i < glyphCount; ++i) {
			final int cp = Character.codePointAt(chars, charIndex);
			if (metrics.getKerning(gids[i - 1], gids[i]) == 0) {
				final double trim = pairTrim(prevCp, isWide(metrics, gids[i - 1], fontSize, direction), cp,
						isWide(metrics, gids[i], fontSize, direction)) * fontSize;
				if (trim > 0) {
					// xadvance[i]=glyph iの手前のアキ(負=詰め)
					text.addXAdvance(i, -trim);
				}
			}
			charIndex += clusterLengths[i];
			prevCp = Character.codePointBefore(chars, charIndex);
		}
	}
}
