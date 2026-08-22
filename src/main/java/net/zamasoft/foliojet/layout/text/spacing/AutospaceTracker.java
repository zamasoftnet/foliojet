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

	private int prevCodePoint = -1;

	private double prevFontSize;

	private int prevGid = -1;

	private boolean trimOff;

	/** 実効フラグを設定します(インライン境界でのparams切替に追従)。 */
	public void setFlags(final byte flags) {
		this.flags = flags;
	}

	/** 約物詰めの無効化(text-spacing-trim: space-all——T1b)を設定します。 */
	public void setTrimOff(final boolean trimOff) {
		this.trimOff = trimOff;
	}

	/** 現在の実効フラグです(分割点の逆適用の再計算用)。 */
	public byte getFlags() {
		return this.flags;
	}

	/** 約物詰めが無効か(分割点の逆適用の再計算用)。 */
	public boolean isTrimOff() {
		return this.trimOff;
	}

	/**
	 * 同一run内の直前glyphとの約物詰め(正値。0=なし)です(T1a——
	 * font層から移管。GPOSが非0のpairはスキップ、run境界は対象外=
	 * 移管元と同じ適用範囲)。縦書きrunもclusterのUnicode code pointで
	 * 分類し、GSUB vert後glyphのvertical advanceでwide判定する。
	 * xadvanceは論理inline advanceなので縦組では下向きの送りへ効く。
	 * 縦中横の横書きrunは従来どおりhorizontal widthを使う。
	 *
	 * @param currentText 現在追記中のrun({@code null}=新run)
	 */
	public double trimBefore(final char[] ch, final int coff, final int gid, final TextImpl currentText,
			final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics, final double fontSize) {
		if (this.trimOff || this.prevCodePoint < 0 || this.prevGid < 0 || currentText == null
				|| this.prevText != currentText) {
			return 0;
		}
		if (metrics.getKerning(this.prevGid, gid) != 0) {
			return 0;
		}
		final int cp = Character.codePointAt(ch, coff);
		final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction = currentText.getFontStyle().getDirection();
		return JapaneseSpacingResolver.pairTrim(this.prevCodePoint,
				JapaneseSpacingResolver.isWide(metrics, this.prevGid, fontSize, direction), cp,
				JapaneseSpacingResolver.isWide(metrics, gid, fontSize, direction)) * fontSize;
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
	 * cluster処理後の状態更新です。適用側の規約: 調整(gap−trim)は
	 * <b>現在glyphのxadvance</b>(=そのglyphの手前のアキ——
	 * CIDKeyedFont/ルビdistributeと同じ)へ載せる。
	 */
	public void glyphAdded(final TextImpl text, final double fontSize, final char[] ch, final int coff,
			final byte clen, final int gid) {
		this.prevText = text;
		this.prevCodePoint = Character.codePointBefore(ch, coff + clen);
		this.prevFontSize = fontSize;
		this.prevGid = gid;
	}

	/** pair状態を破棄します(行分割・TextControl)。 */
	public void reset() {
		this.prevText = null;
		this.prevCodePoint = -1;
		this.prevGid = -1;
	}
}
