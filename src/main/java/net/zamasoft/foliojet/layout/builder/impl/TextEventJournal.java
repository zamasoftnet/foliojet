package net.zamasoft.foliojet.layout.builder.impl;

/**
 * WordHyphenator → BuilderGlyphHandler 境界の正規化イベント観測です
 * (M3b Phase 0: shadow journal。挙動不変)。
 *
 * <p>
 * 配達されたイベントに一度だけ seq を振り、ソース文字カーソル
 * (グリフに加えて control の消費も含む)を追跡する。既存の
 * deliveredCharEnd はグリフでしか進まないため「正規化イベントの配達
 * 境界」として不完全(BuilderGlyphHandler の注記)— その差を型と
 * テストで固定するのが本クラスの役目。TextReplaySlice(C3)と
 * BreakNode 投影(M3b)はこの seq 空間を共有する。
 * </p>
 */
public final class TextEventJournal {
	private int seq = 0;

	/**
	 * 正規化イベントの配達カーソル(ソース文字終端)。グリフと
	 * ソース位置を持つ control で前進する。
	 */
	private int cursor = 0;

	/** 直近のイベント seq を返します。 */
	public int seq() {
		return this.seq;
	}

	/**
	 * 正規化イベントの配達カーソルを返します。末尾の空白・改行・
	 * SoftHyphen の消費を含むため、deliveredCharEnd(グリフのみ)より
	 * 進んでいることがある。
	 */
	public int cursor() {
		return this.cursor;
	}

	/** テキストランの開始。 */
	public void run(final int charOffset) {
		++this.seq;
	}

	/** グリフの配達。 */
	public void glyph(final int charStart, final int charEnd) {
		++this.seq;
		if (charStart >= 0) {
			this.cursor = Math.max(this.cursor, charEnd);
		}
	}

	/** ソース位置を持つ control(空白・改行・SoftHyphen)の配達。 */
	public void control(final int charOffset) {
		++this.seq;
		if (charOffset >= 0) {
			this.cursor = Math.max(this.cursor, charOffset + 1);
		}
	}

	/** インライン quad の配達(ソース位置なし)。 */
	public void inline() {
		++this.seq;
	}

	/** 行の flush。 */
	public void flush() {
		++this.seq;
	}
}
