package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * {@link IBox#finishLayout}の反復化(2026-07-20、再帰禁止方針
 * ——ARCHITECTURE.md不変条件6——への対応)における、ワークリスト上の
 * 1ステップです。
 *
 * <p>
 * 旧実装は{@code IBox.finishLayout(IFramedBox)}がポリモーフィックに
 * 子を直接再帰呼び出ししており、深いネスト文書(1000段超)で
 * StackOverflowErrorを起こしていた(実文書=法令ページで確認済み)。
 * 本インターフェースは、JVMコールスタックの代わりに明示的な
 * {@link Deque}をワークリストとして使う反復DFSへ置き換えるための
 * 型で、「このステップを実行し、必要なら後続ステップを{@code worklist}
 * へ積む」という契約を持つ。
 * </p>
 *
 * <p>
 * 元の再帰の走査順(深さ優先、兄弟は先頭から)を保つため、複数の子を
 * 積む実装は**逆順**で{@code push}すること(スタックとして使うため、
 * 最後にpushしたものが最初にpopされる)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
@FunctionalInterface
public interface FinishLayoutStep {
	/**
	 * このステップを実行します。後続ステップ(子の処理)があれば
	 * {@code worklist}へ積みます。
	 */
	void run(Deque<FinishLayoutStep> worklist);
}
