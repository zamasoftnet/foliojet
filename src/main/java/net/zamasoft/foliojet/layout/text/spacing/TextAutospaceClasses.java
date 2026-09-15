package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.foliojet.css.value.TextAutospaceValue;

/**
 * {@code text-autospace}の文字分類とgap計算です(和文詰めA2、
 * 2026-07-31——consult-codex-2026-07-31-text-spacing.txt)。
 * boxに依存しない純粋計算。分類はcode point基準。
 *
 * <p>
 * サブセット(CSS Text 4の趣旨、逸脱は記録): 和字=漢字(基本・拡張A・
 * 互換・追加面)・仮名(拡張含む)・々〆〇。欧文字=ASCII/Latin-1/
 * Latin拡張A/ギリシア/キリルの字母。数字=ASCII数字のみ。全角英数
	 * (FF01-)は和字幅のため対象外。gapはJLREQ既定の0.25ic——全角フォントでは
	 * ic=emのため、和字側runのfont-size×0.25で近似する(答申の
 * 「水」advance実測は将来最適化)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextAutospaceClasses {

	/** 和欧文間スペース量(em比)。JLREQ 3.2.8の四分アキ。 */
	public static final double GAP = 0.25;

	private TextAutospaceClasses() {
		// static
	}

	/** 文字クラスです。 */
	public enum Kind {
		IDEOGRAPH, ALPHA, NUMERIC,
		/** JLREQ cl-06/cl-07 の句読点。比例幅のときだけ和字と同じく欧文・数字との間に四分アキを入れる。 */
		PUNCTUATION, OTHER
	}

	/** code pointを分類します。 */
	public static Kind of(final int cp) {
		// 和字: 仮名・漢字・々〆〇・仮名拡張
		if (cp >= 0x3040 && cp <= 0x30FF || cp >= 0x31F0 && cp <= 0x31FF || cp >= 0x3005 && cp <= 0x3007
				|| cp >= 0x3400 && cp <= 0x4DBF || cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0xF900 && cp <= 0xFAFF
				|| cp >= 0x20000 && cp <= 0x3FFFF) {
			return Kind.IDEOGRAPH;
		}
		// 数字: ASCIIのみ
		if (cp >= '0' && cp <= '9') {
			return Kind.NUMERIC;
		}
		// 欧文字: ASCII/Latin-1/Latin拡張A/ギリシア/キリルの字母
		if (cp >= 'A' && cp <= 'Z' || cp >= 'a' && cp <= 'z') {
			return Kind.ALPHA;
		}
		if (cp >= 0x00C0 && cp <= 0x024F && Character.isLetter(cp) || cp >= 0x0370 && cp <= 0x03FF
				&& Character.isLetter(cp) || cp >= 0x0400 && cp <= 0x04FF) {
			return Kind.ALPHA;
		}
		if (JapaneseSpacingClass.of(cp) == JapaneseSpacingClass.PUNCTUATION) {
			return Kind.PUNCTUATION;
		}
		return Kind.OTHER;
	}

	/**
	 * 前の字が比例幅の句読点か(和欧間アキの対象にするか)です。JLREQ 3.2.8 の和欧間アキは
	 * 漢字等・仮名と欧文用文字の間に入れるもので、句読点は対象外——全角の句読点は字形が
	 * 自身の後ろに二分の空きを持つから困らない。IPA P 系や {@code palt} 指定のように
	 * 句読点が比例幅だとその空きが無く、欧文・数字が字面に寄る(0.2em 台)ので、
	 * 比例幅(送りが 0.75em 以下、{@link JapaneseSpacingResolver#isWide})の句読点に限り
	 * 和字と同じ扱いにする(2026-09-14、利用者報告「palt 指定時、約物と欧文の間に
	 * 和欧間アキが入らない」)。
	 *
	 * @param metrics 前の字の FontMetrics({@code null} なら不明=対象外)
	 */
	public static boolean proportionalPunctuation(final int prevCp,
			final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final int gid, final double fontSize,
			final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction) {
		return metrics != null && gid >= 0 && of(prevCp) == Kind.PUNCTUATION
				&& !JapaneseSpacingResolver.isWide(metrics, gid, fontSize, direction);
	}

	/**
	 * 隣接pairの間のgap(em比。0=なし)です。空白等を挟むpairには
	 * 適用しないこと(呼び出し側がcontrolでリセットする)。
	 *
	 * @param prevCp 前の文字
	 * @param cp     次の文字
	 * @param flags  実効フラグ({@code TextAutospaceValue.ALPHA}|{@code NUMERIC})
	 */
	public static double gapEm(final int prevCp, final int cp, final byte flags) {
		return gapEm(prevCp, cp, flags, false);
	}

	/**
	 * {@link #gapEm(int, int, byte)} に、前の字が比例幅の句読点なら和字と同じ扱いにする
	 * 判定({@link #proportionalPunctuation})を加えたものです。
	 */
	public static double gapEm(final int prevCp, final int cp, final byte flags,
			final boolean prevProportionalPunctuation) {
		if (flags == 0) {
			return 0;
		}
		Kind prev = of(prevCp);
		if (prev == Kind.PUNCTUATION && prevProportionalPunctuation) {
			prev = Kind.IDEOGRAPH;
		}
		final Kind next = of(cp);
		if (prev == next) {
			return 0;
		}
		final Kind latin = prev == Kind.IDEOGRAPH ? next : next == Kind.IDEOGRAPH ? prev : Kind.OTHER;
		if (latin == Kind.ALPHA && (flags & TextAutospaceValue.ALPHA) != 0) {
			return GAP;
		}
		if (latin == Kind.NUMERIC && (flags & TextAutospaceValue.NUMERIC) != 0) {
			return GAP;
		}
		return 0;
	}

	/** pairの和字側(比例幅の句読点を含む)が前(prev)ならtrue(font-size選択用)。 */
	public static boolean ideographFirst(final int prevCp) {
		final Kind kind = of(prevCp);
		return kind == Kind.IDEOGRAPH || kind == Kind.PUNCTUATION;
	}
}
