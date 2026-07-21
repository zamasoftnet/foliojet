package net.zamasoft.foliojet.layout.fragment;

/**
 * {@code AbstractContainerBox.prepareColumnCut()}の型付き結果です
 * (2026-07-21新設、M6b Phase B B4)。旧{@code newColumn()}の三義的
 * null/自身/他への返値(呼び出し側がidentity比較で解釈していた)を、
 * 明示的な型へ置き換える。
 */
public sealed interface ColumnCutResult {
	/** 全体を現在のactive columnに残す(旧nullに相当)。 */
	record Keep() implements ColumnCutResult {
	}

	/** 全体を次のcolumnへ送る(旧: 返値がactive columnそのものに相当)。 */
	record Move() implements ColumnCutResult {
	}

	/** 内部で切断した。{@link PreparedColumnCut}はまだownerへcommit前。 */
	record Cut(PreparedColumnCut prepared) implements ColumnCutResult {
	}
}
