package net.zamasoft.foliojet.layout.segment;

/**
 * 閉じたボックス部分木の構造索引です(2026-07-22新設、M6d-A3a、
 * 型契約のみ・未配線)。
 *
 * <p>
 * {@link BoxRecipe}(箱の生成方法)と子コンテンツの範囲(構造)を
 * 意図的に分離する——recipeに子参照を混ぜないことで、recipe単体を
 * 使い回したり比較したりする際に構造の詳細を気にしなくてよくなる
 * (codex設計相談で確認)。{@code children}が指す範囲内の先頭イベントが
 * この{@code ContainerNode}自身の{@link SegmentEvent.BeginBox}に
 * 対応する({@code BeginBox}自体は{@code children}には含めない
 * ——{@code children}は開始イベントの「次」から対応する終了イベントの
 * 「前」までを指す、confirmedな閉部分木の内側のみ)。
 * </p>
 *
 * <p>
 * 対応する{@code Start}がまだ閉じていない(部分木が未確定)場合は、
 * この型では表現しない——{@link SegmentEvent.Barrier}
 * ({@link BarrierReason#UNCLOSED_SUBTREE})で表す。
 * </p>
 *
 * @param recipe   このノードのボックス生成方法
 * @param children 子コンテンツを指す範囲(同一{@link SegmentId}内)
 */
public record ContainerNode(BoxRecipe recipe, SegmentRange children) {
}
