package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * {@code text-autospace}の隣接pair追跡です(和文詰めA2、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt A2)。glyph消費者
 * (TextBuilder・TwoPassBlockBuilder)がclusterごとに直前clusterとの
 * 境界gapを問い、確定したgapを直前glyphのxadvanceへ焼き込む。
 *
 * <p>
 * リセット規約: 実際の行分割(newLine)と制御(空白・改行・インライン
 * 置換等のTextControl)でリセットする。分割<b>機会</b>(flush)では
 * リセットしない——和欧文境界はまさに分割機会であり、そこでリセット
 * するとgapが一切入らない。行分割でリセットされるため、行を跨ぐpairに
 * gapは入らない(JLREQの行頭・行末処理と整合)。
 * </p>
 */
public final class AutospaceTracker {

	private byte flags;

	private TextImpl prevText;

	private int prevGlyphIndex = -1;

	private int prevCodePoint = -1;

	private double prevFontSize;

	/** 実効フラグを設定します(インライン境界でのparams切替に追従)。 */
	public void setFlags(final byte flags) {
		this.flags = flags;
	}

	/** 現在のcluster先頭と直前clusterの間のgap(絶対量)です。 */
	public double gapBefore(final char[] ch, final int coff, final double fontSize) {
		if (this.flags == 0 || this.prevCodePoint < 0) {
			return 0;
		}
		final int cp = Character.codePointAt(ch, coff);
		final double gapEm = TextAutospaceClasses.gapEm(this.prevCodePoint, cp, this.flags);
		if (gapEm == 0) {
			return 0;
		}
		// 和字側runのfont-size×0.125(ic近似——クラスjavadoc)
		return gapEm * (TextAutospaceClasses.ideographFirst(this.prevCodePoint) ? this.prevFontSize : fontSize);
	}

	/**
	 * gapを直前glyphのxadvanceへ焼き込みます(直前glyphの後=現在glyphの
	 * 前のアキ。live構築のTextBuilder用——TwoPass計測は記録textを変異
	 * させず計測器へだけ渡す: records再生はtoGlyphsでxadvanceを運ばず
	 * 再構築時にtrackerが再適用するため)。
	 */
	public void applyGap(final double gap) {
		if (this.prevText != null) {
			this.prevText.addXAdvance(this.prevGlyphIndex, gap);
		}
	}

	/** cluster処理後の状態更新です。 */
	public void glyphAdded(final TextImpl text, final double fontSize, final char[] ch, final int coff,
			final byte clen) {
		this.prevText = text;
		this.prevGlyphIndex = text == null ? -1 : text.getGlyphCount() - 1;
		this.prevCodePoint = Character.codePointBefore(ch, coff + clen);
		this.prevFontSize = fontSize;
	}

	/** pair状態を破棄します(行分割・TextControl)。 */
	public void reset() {
		this.prevText = null;
		this.prevGlyphIndex = -1;
		this.prevCodePoint = -1;
	}
}
