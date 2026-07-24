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
 * <p>
 * <b>切断結果型ファミリの語彙対応表(2026-07-24 E-4で明文化)</b>——
 * 名前の揺れではなく粒度の違いを接尾辞で表す規約: 無印
 * {@code Keep}/{@code Move}は「その型が扱う単位1個」(box・float・
 * column)、{@code All}接尾辞は「float台帳全体」、{@code Owner}接尾辞は
 * 「ownerコンテナ自身」。
 * </p>
 * <table>
 * <caption>層別の対応</caption>
 * <tr><th>型(粒度)</th><th>前に残す</th><th>次へ送る</th><th>内部切断</th></tr>
 * <tr><td>{@code SplitResult}(box)</td><td>Keep</td><td>Move</td>
 * <td>Split(残余box) / Frame(継続フレーム)</td></tr>
 * <tr><td>{@link FloatFragmentSplit}(float box)</td><td>Keep</td><td>Move</td>
 * <td>Prepared(遅延材料)</td></tr>
 * <tr><td>{@link ColumnCutResult}(column)</td><td>Keep</td><td>Move</td>
 * <td>Cut(commit前の準備物)</td></tr>
 * <tr><td>{@code ContainerCut}(コンテナ)</td><td>Plain(null)</td>
 * <td>Plain(自身)</td><td>Plain(他) / WithFrame</td></tr>
 * <tr><td>{@code FloatSplitPlan.FloatItemPlan}(float 1個の計画)</td>
 * <td>Keep</td><td>Move</td><td>SplitOnCommit(結果を予言しない印)</td></tr>
 * <tr><td>{@code FloatSplitResult}(float台帳全体)</td><td>KeepAll</td>
 * <td>MoveAll</td><td>Partition(残余台帳)</td></tr>
 * <tr><td>{@code FloatTransferResult}(ownerコンテナ)</td><td>KeepOwner</td>
 * <td>MoveOwner</td><td>Remainder(移動台帳装着済みコンテナ)</td></tr>
 * </table>
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
