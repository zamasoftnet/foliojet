package net.zamasoft.foliojet.css.style;

import java.util.Arrays;

/**
 * 表の格子の桁が上の行の rowspan で占められているかを、HTML の表の置き方で追います(2026-09-29)。
 *
 * <p>
 * 表ビルダーは行ごとのセル列の添字を列として扱い、上の行の rowspan を
 * {@code CellContent.complementRowspan} で「セル列の終わりから連続する位置だけ」引き継ぐ。
 * 行が短く、行の最後のセルと上から続く rowspan の間に空き桁があると、その先の rowspan は
 * 当行にも以降の行にも引き継がれず、後ろの行のセルが格子より左の列へ入って、ぶち抜きのセルに
 * 重なって描かれていた(36M 掃過の分類の副産物、copperpdf4 の triage §13)。
 * </p>
 *
 * <p>
 * 空き桁を<b>空の匿名セル</b>で埋めれば、ビルダーの引き継ぎはそのまま格子と一致する。匿名セルは
 * カスケードを通らない(継承値と初期値だけ)ので枠・余白・背景を持たず、空き桁と同じく何も描かれない。
 * このクラスは空き桁の検出にだけ使い、箱の寸法や配置には関わらない。行グループの境界では、
 * ビルダーと同じく rowspan を引き継がない。
 * </p>
 *
 * <p>
 * <b>限界</b>: 固定レイアウトの表で、1 行目で決まった列数を超えるセルを Incremental ビルダーは捨てる
 * (内容ごと消える既存の制限)。ここはその切り詰めを知らないので、捨てられたセルの rowspan の手前にも
 * 匿名セルを置くことがある。影響は、その行に見えない空のセルが 1 つ増えること(空行なら
 * {@code border-spacing} の分だけ高さが付く)にとどまる(2026-09-29 の codex レビュー)。
 * </p>
 */
final class TableSlotTracker {
	/** 表の要素のスタイル。匿名セルの書字方向を表に揃えるのに使う(行の書字方向を継ぐと直交セルになる)。 */
	final net.zamasoft.foliojet.css.CSSStyle table;

	TableSlotTracker(final net.zamasoft.foliojet.css.CSSStyle table) {
		this.table = table;
	}

	/** 桁ごとに、当行より後の行を上のセルが占め続ける行数。 */
	private int[] carry = new int[8];
	/** 当行で占められた桁(上から続くものと当行のセル)。 */
	private boolean[] occupied = new boolean[8];
	/** 当行の始めに上から続いていた桁の最大+1。 */
	private int carriedEnd = 0;
	/** 当行の次のセルを探し始める桁。 */
	private int cursor = 0;
	private boolean inRow = false;

	/** 行グループの始まり。rowspan はグループを越えない。 */
	void beginRowGroup() {
		Arrays.fill(this.carry, 0);
	}

	void beginRow() {
		this.inRow = true;
		this.cursor = 0;
		this.carriedEnd = 0;
		for (int c = 0; c < this.carry.length; ++c) {
			this.occupied[c] = this.carry[c] > 0;
			if (this.occupied[c]) {
				--this.carry[c];
				this.carriedEnd = c + 1;
			}
		}
	}

	/** 当行のセルを、占められていない最初の桁に置きます。 */
	void placeCell(final int colspan, final int rowspan) {
		if (!this.inRow) {
			return;
		}
		while (this.cursor < this.occupied.length && this.occupied[this.cursor]) {
			++this.cursor;
		}
		this.ensureCapacity(this.cursor + colspan);
		for (int k = 0; k < colspan; ++k) {
			this.occupied[this.cursor + k] = true;
			this.carry[this.cursor + k] = Math.max(this.carry[this.cursor + k], rowspan - 1);
		}
		this.cursor += colspan;
	}

	/**
	 * 当行のセルの後ろ、上から続く rowspan の桁より手前に空き桁があるか。あれば、次に
	 * {@link #placeCell}(1, 1) で置く匿名セルがその空き桁に入る。
	 */
	boolean hasGapBeforeCarried() {
		if (!this.inRow) {
			return false;
		}
		int c = this.cursor;
		while (c < this.carriedEnd && this.occupied[c]) {
			++c;
		}
		return c < this.carriedEnd;
	}

	void endRow() {
		this.inRow = false;
	}

	private void ensureCapacity(final int size) {
		if (size > this.carry.length) {
			final int length = Math.max(size, this.carry.length * 2);
			this.carry = Arrays.copyOf(this.carry, length);
			this.occupied = Arrays.copyOf(this.occupied, length);
		}
	}
}
