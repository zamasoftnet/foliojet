package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * {@link FloatPos}(浮動体の配置パラメータ、{@link BoxKind#FLOAT_BLOCK}
 * が使う)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code FloatPos}をmaterializeするテンプレートです(2026-07-22新設、
 * M6d-A3b)。
 *
 * <p>
 * {@code FloatPos}は{@code FlowPos}と同じ{@code AbstractNormalFlowPos}
 * を継承するため、祖先フィールドは{@link NormalFlowPosFields}
 * (`FlowPosTemplate`と共有)が担う。{@code floating}
 * ({@code FloatSide}、enum)はそのまま保持する。
 * </p>
 */
public final class FloatPosTemplate {
	private final NormalFlowPosFields common;
	private final FloatSide floating;

	private FloatPosTemplate(final NormalFlowPosFields common, final FloatSide floating) {
		this.common = common;
		this.floating = floating;
	}

	public static FloatPosTemplate freeze(final FloatPos source) {
		return new FloatPosTemplate(NormalFlowPosFields.freeze(source), source.floating);
	}

	/** 呼び出しごとに新品の{@code FloatPos}を返す(複数回呼んでも互いに影響しない)。 */
	public FloatPos materialize() {
		final FloatPos pos = new FloatPos();
		this.common.materializeInto(pos);
		pos.floating = this.floating;
		return pos;
	}
}
