package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link FlexParams}({@code BlockParams}を直接継承、{@code BoxKind#FLEX}が
 * 使う)の内容をfreezeし、呼び出しごとに独立した新品の{@code FlexParams}を
 * materializeするテンプレートです(Flex F0c、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt F0c。{@code GridParamsTemplate}と同型)。
 *
 * <p>
 * direction/wrapはenumのためそのまま保持できる。F2c(gap)・F3a(整列)の
 * フィールド追加時はここへ追随する。
 * </p>
 */
public record FlexParamsTemplate(BlockParamsFields common, FlexDirection flexDirection, FlexWrap flexWrap,
		double rowGap, double columnGap) {
	public static FlexParamsTemplate freeze(final FlexParams source) {
		return new FlexParamsTemplate(BlockParamsFields.freeze(source), source.flexDirection, source.flexWrap,
				source.rowGap, source.columnGap);
	}

	/** 凍結済みの書字方向を返します({@code containsMixedFlow}用)。 */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** 呼び出しごとに新品の{@code FlexParams}を返す。 */
	public FlexParams materialize() {
		final FlexParams p = new FlexParams();
		this.common.materializeInto(p);
		p.flexDirection = this.flexDirection;
		p.flexWrap = this.flexWrap;
		p.rowGap = this.rowGap;
		p.columnGap = this.columnGap;
		return p;
	}
}
