package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * 和文スペーシングの解決器です(和文詰めS0、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。boxやフォントの状態を変更せず
 * 詰め量を計算する。S1でOpenTypeFont.getKerningの約物詰めと
 * TextBuilderの縦書き天付きがここへ移管される(出力不変が受入条件)。
 *
 * <p>
 * 規則の単位は<b>em比</b>(呼び出し側がfont-sizeを乗じる)。字面測定と
 * {@code cappedPairTrim}はfont-size換算済みの絶対量を返す。「wide」は
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

	/** 字面の前端。ペンからの距離をfont-size換算して返す。測定不能はNaN。 */
	public static double inkStart(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style) {
		return inkEdge(metrics, gid, fontSize, style, false);
	}

	/** 字面の後端。はみ出しも符号付きで保持する。 */
	public static double inkEnd(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style) {
		return inkEdge(metrics, gid, fontSize, style, true);
	}

	private static double inkEdge(final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style, final boolean end) {
		if (!(metrics instanceof FontMetricsImpl m) || !(m.getFont() instanceof ShapedFont font)) {
			return Double.NaN;
		}
		final var bounds = font.getGlyphBounds(gid);
		if (bounds == null) {
			return Double.NaN;
		}
		final var source = font.getFontSource();
		// 横書きフォントを縦の行へ横倒しする場合も字形のx軸を使う。
		final boolean vertical = source.getDirection() == FontStyle.Direction.TB;
		final double scale = fontSize / 1000.0;
		final double edge = vertical
				? font.getVerticalOrigin(gid) + (end ? bounds.maxY() : bounds.minY())
				: font.getPlacementAdjustment(gid, style.getFeatures()) + (end ? bounds.maxX() : bounds.minX());
		double expansion = 0;
		if (style.getWeight().w >= 500 && source.getWeight().w < 500 && style.getSynthesisWeight()) {
			// FontUtils.drawTextと同じstroke幅。その半分ずつ字面が膨らむ。
			expansion = fontSize / switch (style.getWeight()) {
				case W_500 -> 28.0;
				case W_600 -> 24.0;
				case W_700 -> 20.0;
				case W_800 -> 16.0;
				case W_900 -> 12.0;
				default -> throw new IllegalStateException();
			} / 2.0;
		}
		if (style.getStyle() != FontStyle.Style.NORMAL && !source.isItalic() && style.getSynthesisStyle()) {
			// 合成斜体はshearの幾何どおりに片側ずつ広げる。横はx'=x−0.25y(y下向き)なので
			// 上端(y<0)が後端を右へ、下端(y>0)が前端を左へ押す。縦はy'=y+0.25xなので
			// 右端(x>0)が後端を下へ、左端(x<0)が前端を上へ押す。両端を一律に広げると
			// autospaceの追い込み容量が不要に減り、行内の配分が動く(2026-09-12)。
			if (vertical) {
				expansion += 0.25 * scale * Math.max(0, end ? bounds.maxX() : -bounds.minX());
			} else {
				expansion += 0.25 * scale * Math.max(0, end ? -bounds.minY() : bounds.maxY());
			}
		}
		return edge * scale + (end ? expansion : -expansion);
	}

	/** 2字のペン間距離に対する字面間隔。側ごとには0で切らない。 */
	public static double inkGap(final FontMetrics prevMetrics, final int prevGid, final double prevSize,
			final FontStyle prevStyle, final FontMetrics metrics, final int gid, final double fontSize,
			final FontStyle style, final double penDistance) {
		return penDistance + inkStart(metrics, gid, fontSize, style)
				- inkEnd(prevMetrics, prevGid, prevSize, prevStyle);
	}

	/**
	 * 字面間隔で上限した連続約物の詰め(絶対量)。追加・分割時の逆適用・
	 * 固有寸法計量が同じ入力で再計算できるよう、字間やxadvanceは含めない。
	 * 同一runでkerningが非0の組を除外するのは呼び出し側の契約。
	 */
	public static double cappedPairTrim(final int prevCp, final FontMetrics prevMetrics, final int prevGid,
			final double prevSize, final FontStyle prevStyle, final int cp, final FontMetrics metrics,
			final int gid, final double fontSize, final FontStyle style) {
		final double nominal = pairTrim(prevCp, isWide(prevMetrics, prevGid, prevSize, prevStyle.getDirection()),
				cp, isWide(metrics, gid, fontSize, style.getDirection())) * fontSize;
		if (nominal == 0) {
			return 0;
		}
		final double gap = inkGap(prevMetrics, prevGid, prevSize, prevStyle, metrics, gid, fontSize, style,
				prevMetrics.getAdvance(prevGid));
		return Double.isNaN(gap) ? nominal : Math.min(nominal, Math.max(0, gap));
	}

	/**
	 * 連続する2文字の間の詰め量(em比。0=詰めない)です。移管元
	 * (OpenTypeFont.getKerning)と同一の表:
	 * <ul>
	 * <li>開き+開き(両方wide): 0.5</li>
	 * <li>閉じ+{開き|閉じ|句読点}(両方wide): 0.5</li>
	 * <li>句読点(wide)+開き(wide): 0.5</li>
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
				// JLREQの二分アキは全角の約物枠を前提とする。fallback等で
				// 後続がproportionalなら固定0.5emを引かない。
				return wide ? PAIR_TRIM : 0;
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
	 * 行頭の天付きインデント(em比、負値)です。書字方向によらず、行頭の最初の
	 * 可視テキストが全角相当の始め括弧類で始まるとき-0.5em(移管元:
	 * TextBuilderの縦書き限定処理)。CSS Text 4の
	 * {@code text-spacing-trim: trim-start}でだけ天付きにし、{@code normal}と
	 * {@code space-all}ではJLREQが選択肢として挙げる行頭二分アキを残す。
	 * プロポーショナル約物とそれ以外も0。
	 */
	public static double lineHeadIndent(final int firstCodePoint, final boolean wide, final boolean trimStart) {
		return trimStart && wide && JapaneseSpacingClass.of(firstCodePoint) == JapaneseSpacingClass.OPENING ? -PAIR_TRIM
				: 0;
	}

	/**
	 * {@code hanging-punctuation:first}で最初の整形行の先頭から行外へ出す量。
	 * text-spacingで既に半角化した全角始め括弧は0.5em、それ以外の対象字形は
	 * 実advance全体をぶら下げる。
	 */
	public static double firstHang(final int codePoint, final boolean wide, final double advance,
			final double fontSize, final boolean trimmedStart) {
		if (codePoint == 0x3000) {
			return -advance;
		}
		final int type = Character.getType(codePoint);
		final boolean quoteOrBracket = type == Character.START_PUNCTUATION
				|| type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
				|| codePoint == 0x27 || codePoint == 0x22;
		if (!quoteOrBracket) {
			return 0;
		}
		if (trimmedStart && wide && JapaneseSpacingClass.of(codePoint) == JapaneseSpacingClass.OPENING) {
			return -PAIR_TRIM * fontSize;
		}
		return -advance;
	}

	/**
	 * 均等割りで直後を伸長してよい文字かを返します。
	 *
	 * <p>JLREQ 3.1.5の中点類(cl-05)は、字形が持つ前後四分のうち
	 * 後ろをベタに保つのが原則で、行調整の無差別な伸長点にはしない。
	 * 行頭禁則だけでは「中点の前」は守れても「中点の後」は守れないため、
	 * justifyのcount/apply双方がこの判定を使う。</p>
	 */
	public static boolean allowsJustificationAfter(final int codePoint) {
		return JapaneseSpacingClass.of(codePoint) != JapaneseSpacingClass.MIDDLE_DOT;
	}

	/** JLREQ cl-07（読点類）。cl-06（句点類）と追込み優先度を分けるために使う。 */
	public static boolean isComma(final int codePoint) {
		return codePoint == 0x3001 || codePoint == 0xFF0C;
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
		final JapaneseSpacingClass cls = JapaneseSpacingClass.of(codePoint);
		// (1) 行末trim: 半角化で収まるなら詰める(中点はJIS X 4051の
		// 「行末中点は前四分・後ろベタ」に従い四分=0.25emのみ)
		if (!trimOff) {
			final double trim = endTrim(codePoint, wide, fontSize);
			if (overflow <= trim) {
				return trim;
			}
		}
		// (2) ぶら下げ: 句読点のみ・そのglyphの全advance
		if (wide && hangEnd && cls == JapaneseSpacingClass.PUNCTUATION && overflow <= advance) {
			return advance;
		}
		return 0;
	}

	/**
	 * 全角の行末約物を半角化する量です。閉じ括弧・句読点は二分、
	 * 中点類はJLREQの行末配置に従い四分を詰める。
	 */
	public static double endTrim(final int codePoint, final boolean wide, final double fontSize) {
		if (!wide) {
			return 0;
		}
		final JapaneseSpacingClass cls = JapaneseSpacingClass.of(codePoint);
		if (cls == JapaneseSpacingClass.CLOSING || cls == JapaneseSpacingClass.PUNCTUATION) {
			return PAIR_TRIM * fontSize;
		}
		if (cls == JapaneseSpacingClass.MIDDLE_DOT) {
			return PAIR_TRIM / 2 * fontSize;
		}
		return 0;
	}

	/** {@code force-end}で常にぶら下げるJLREQ句読点のadvanceです。 */
	public static double forceEndHang(final int codePoint, final double advance) {
		return JapaneseSpacingClass.of(codePoint) == JapaneseSpacingClass.PUNCTUATION ? advance : 0;
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
		final FontStyle style = text.getFontStyle();
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
				final double trim = cappedPairTrim(prevCp, metrics, gids[i - 1], fontSize, style,
						cp, metrics, gids[i], fontSize, style);
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
