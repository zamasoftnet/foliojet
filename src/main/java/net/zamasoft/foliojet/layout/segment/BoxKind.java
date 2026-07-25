package net.zamasoft.foliojet.layout.segment;

/**
 * 正規Segmentモデルにおけるボックス種別です(2026-07-22新設、
 * M6d-A3a。2026-07-25時点で{@link BoxRecipe}が保持し、
 * {@code SourceReplayer}が消費している)。
 *
 * <p>
 * {@code net.zamasoft.foliojet.layout.fragment.LayoutSource.BoxKind}と
 * 値の並びは同じだが、意図的に別の型として定義する——M6d-Aの正規
 * モデル({@code layout.segment}パッケージ)は、統合対象の旧3表現
 * ({@code LayoutSource}を含む)への依存を持たせない(既存
 * `css.style.Segment`と最終`Segment`を混同しないのと同じ理由)。
 * 変換は一方向のアダプタ(M6d-A3c、{@code LayoutSource}→
 * {@code SegmentEvent})が担う。
 * </p>
 */
public enum BoxKind {
	FLOW, MULTICOL, INLINE, MARKER, FLOAT_BLOCK, INLINE_BLOCK, INSIDE_MARKER, TABLE_ROW_GROUP, TABLE_ROW, TABLE_CELL,
	TABLE_COLUMN_GROUP, TABLE_COLUMN,
	/** 絶対配置ブロック(AbsoluteBlockBox)。E-6増分4e(2026-07-24)で追加。 */
	ABSOLUTE;
}
