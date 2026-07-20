package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * {@link IBox#textShape}の反復化(2026-07-20、drawと同じ理由)用の
 * ワークリスト単位です。textShapeは(クリップ用の{@code GeneralPath}へ
 * 幾何を積み上げるだけで描画順に意味がないため)drawほど厳密な
 * push順の管理は不要ですが、他の反復化と実装を揃えるため同じ規約
 * (子は**逆順**でpush)に従います。
 */
@FunctionalInterface
public interface TextShapeStep {
	void run(Deque<TextShapeStep> worklist);
}
