package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * {@link IBox#getText}の反復化(2026-07-20、drawと同じ理由)用の
 * ワークリスト単位です。テキスト抽出は文書順を保つ必要があるため、
 * 局所的なテキスト追記と子ボックスへの委譲が同一ループ内で交互に
 * 現れる箇所({@link AbstractTextBox#pushGetTextSteps})では、drawと
 * 同じ規約(元の実行順のまま組み立てて**逆順**でpush)に従います。
 */
@FunctionalInterface
public interface GetTextStep {
	void run(Deque<GetTextStep> worklist);
}
