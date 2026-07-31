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
 * (FF01-)は和字幅のため対象外。gapは0.125ic——全角フォントでは
 * ic=emのため、和字側runのfont-size×0.125で近似する(答申の
 * 「水」advance実測は将来最適化)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextAutospaceClasses {

	/** 和欧文間スペース量(em比=0.125ic近似)。 */
	public static final double GAP = 0.125;

	private TextAutospaceClasses() {
		// static
	}

	/** 文字クラスです。 */
	public enum Kind {
		IDEOGRAPH, ALPHA, NUMERIC, OTHER
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
		return Kind.OTHER;
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
		if (flags == 0) {
			return 0;
		}
		final Kind prev = of(prevCp);
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

	/** pairの和字側が前(prev)ならtrue(font-size選択用)。 */
	public static boolean ideographFirst(final int prevCp) {
		return of(prevCp) == Kind.IDEOGRAPH;
	}
}
