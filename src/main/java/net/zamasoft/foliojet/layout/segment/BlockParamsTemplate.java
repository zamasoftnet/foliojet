package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link BlockParams}(M6d-Aの{@link BoxKind#FLOW}等が使う代表的な
 * {@code Params}実装)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code BlockParams}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A3b——2026-07-22中にStage2で不変recordへ置換)。
 *
 * <p>
 * フィールドは{@link BlockParamsFields}(`TableParams`
 * (`BlockParams`を直接継承)のテンプレートとも共有する)が担う。
 * </p>
 */
public record BlockParamsTemplate(BlockParamsFields fields) {
	public static BlockParamsTemplate freeze(final BlockParams source) {
		return new BlockParamsTemplate(BlockParamsFields.freeze(source));
	}

	/**
	 * 凍結済みの書字方向を返します(E-6増分3b-4——
	 * {@code LayoutSource.containsMixedFlow}が凍結済みStartから読む)。
	 */
	public WritingMode flow() {
		return this.fields.common().text().flow();
	}

	/** 呼び出しごとに新品の{@code BlockParams}を返す(複数回呼んでも互いに影響しない)。 */
	public BlockParams materialize() {
		final BlockParams p = new BlockParams();
		this.fields.materializeInto(p);
		return p;
	}
}
