package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;

/**
 * {@link BlockParams}(M6d-Aの{@link BoxKind#FLOW}等が使う代表的な
 * {@code Params}実装)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code BlockParams}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A3b Stage1——`SegmentEvent`/`BoxRecipe`(A3a)への実際の
 * 配線はまだ行わない、`Params`のfreeze/materialize機構そのものの
 * 実装・検証が今回の対象)。
 *
 * <p>
 * フィールドは{@link BlockParamsFields}(`TableParams`
 * (`BlockParams`を直接継承)のテンプレートとも共有する)が担う。
 * </p>
 */
public final class BlockParamsTemplate {
	private final BlockParamsFields fields;

	private BlockParamsTemplate(final BlockParamsFields fields) {
		this.fields = fields;
	}

	public static BlockParamsTemplate freeze(final BlockParams source) {
		return new BlockParamsTemplate(BlockParamsFields.freeze(source));
	}

	/** 呼び出しごとに新品の{@code BlockParams}を返す(複数回呼んでも互いに影響しない)。 */
	public BlockParams materialize() {
		final BlockParams p = new BlockParams();
		this.fields.materializeInto(p);
		return p;
	}
}
