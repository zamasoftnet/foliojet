package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * {@link IBox#draw}の反復化(2026-07-20、ARCHITECTURE.md不変条件6。
 * finishLayoutと同じ理由でStackOverflowErrorを起こしていた)用の
 * ワークリスト単位です。
 *
 * <p>
 * draw系はfinishLayoutと違い、「局所処理→子へ委譲」という単純な二分割
 * ではなく、局所描画(テキストラン等)と子の描画が同一ループ内で
 * 交互に現れる箇所がある({@link AbstractTextBox#pushDrawSteps}等)。
 * このため各ボックス型は、自身が生成すべき手順(局所描画のクロージャ・
 * 子ボックスの手順)を元の実行順のまま組み立て、それを**逆順**で
 * {@code worklist}へpushする({@link IBox#pushDrawSteps}参照)。
 * </p>
 */
@FunctionalInterface
public interface DrawStep {
	void run(Deque<DrawStep> worklist);
}
