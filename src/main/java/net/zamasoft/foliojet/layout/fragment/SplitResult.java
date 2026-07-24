package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;

/**
 * ページ方向の切断結果です(柱2cの型付きプロトコル)。
 * 旧 splitPageAxis の三義的返値(null/this/新オブジェクト)を置き換えます。
 * 内部切断の継続情報の運搬は2系統あります: {@code BreakPlan} が選択した
 * チェーンメンバーは {@link Frame} で {@code ContinuationFrame} を親へ
 * 伝播し(残余コンテナへボックスは加えない、C1d-C)、それ以外
 * ({@link Split}——表分割などの隔離領域)は従来どおり remainder
 * ボックス自身が継続状態を保持します。
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface SplitResult {
	/** 全体を前のフラグメンテナ(ページ/段)に残します(旧 null)。 */
	SplitResult KEEP = new Keep();

	/** 全体を次のフラグメンテナへ送ります(旧 this)。 */
	SplitResult MOVE = new Move();

	record Keep() implements SplitResult {
	}

	record Move() implements SplitResult {
	}

	/**
	 * 内部で切断しました。切断元は前ページ分のみを保持し、
	 * remainder が次のフラグメンテナで再開される継続です。
	 *
	 * @param remainder 次のフラグメンテナへ送る残余
	 */
	record Split(IPageBreakableBox remainder) implements SplitResult {
	}

	/**
	 * 内部で切断し、継続断片がボックスではなく ContinuationFrame として
	 * 返されました(C1d-C。BreakPlan が選択したチェーンメンバーのみ)。
	 * 呼び出し側は残余コンテナへボックスを加えず、フレームを返り値で
	 * 親へ伝播します。
	 */
	record Frame(Continuation.ContinuationFrame frame) implements SplitResult {
	}
}
