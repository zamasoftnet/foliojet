package net.zamasoft.foliojet.layout.segment;

/**
 * ボックスの生成方法を表すrecipeです(2026-07-22新設、M6d-A3a、
 * 型契約のみ・未配線)。
 *
 * <p>
 * 現時点では{@link BoxKind}だけを保持する骨格——実際の
 * {@code Params}/{@code Pos}のfreeze(write-once化)はM6d-A3bで設計
 * する(codex設計相談で確認: 第一段階はdeep copyした非公開テンプレート
 * からのmaterialize、第二段階でテンプレート自体を不変recordへ
 * 置換する二段階移行)。この段階で確定させておきたいのは型の輪郭
 * (「recipeは箱の生成方法であり、子コンテンツの構造は含まない」)
 * だけである——子Segment参照は{@link ContainerNode}が別途持つ。
 * </p>
 */
public record BoxRecipe(BoxKind kind) {
}
