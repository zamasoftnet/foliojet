package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;

/**
 * ページ方向の切断結果です(柱2cの型付きプロトコル)。
 * 旧 splitPageAxis の三義的返値(null/this/新オブジェクト)を置き換えます。
 * 継続情報(BreakToken 等)は remainder ボックス自身が保持します。
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

	/** チェーン収集済み(C1b): 断片は Continuation の frames に記録された。 */
	SplitResult COLLECTED = new Collected();

	/**
	 * 内部で切断し、継続断片はボックスとしてではなく Continuation の
	 * frames(ChainCollector)に記録されました(C1b)。呼び出し側は
	 * 残余コンテナへボックスを加えず、切断成立として扱います。
	 */
	record Collected() implements SplitResult {
	}
}
