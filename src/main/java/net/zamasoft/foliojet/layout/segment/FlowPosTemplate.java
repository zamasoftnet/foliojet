package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * {@link FlowPos}(通常フローの配置パラメータ、{@link BoxKind#FLOW}/
 * {@link BoxKind#MULTICOL}が使う)の内容をfreezeし、呼び出しごとに
 * 独立した新品の{@code FlowPos}をmaterializeするテンプレートです
 * (2026-07-22新設、M6d-A3b Stage1)。
 *
 * <p>
 * 祖先({@code AbstractNormalFlowPos}/{@code AbstractBlockLevelPos}/
 * {@code AbstractStaticPos})のフィールドは{@link NormalFlowPosFields}
 * (`FloatPosTemplate`と共有)が担う。{@code align}/{@code columnSpan}
 * (`FlowPos`固有)は既存実装が値クラス・プリミティブで実質不変のため
 * そのまま保持する——{@code Params}系の{@code AffineTransform}/配列の
 * ような真にmutableなフィールドが{@code Pos}階層には存在しないため
 * 防御的コピーは不要(2026-07-22 Stage2で不変recordへ置換)。
 * </p>
 */
public record FlowPosTemplate(NormalFlowPosFields common, Align align, byte columnSpan,
		net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItem) {
	public static FlowPosTemplate freeze(final FlowPos source) {
		return new FlowPosTemplate(NormalFlowPosFields.freeze(source), source.align, source.columnSpan,
				source.gridItem);
	}

	/** 呼び出しごとに新品の{@code FlowPos}を返す(複数回呼んでも互いに影響しない)。 */
	public FlowPos materialize() {
		final FlowPos pos = new FlowPos();
		this.common.materializeInto(pos);
		pos.align = this.align;
		pos.columnSpan = this.columnSpan;
		pos.gridItem = this.gridItem;
		return pos;
	}
}
