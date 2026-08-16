package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;

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
 * ({@code FloatSide}、enum)はそのまま保持する(2026-07-22 Stage2で
 * 不変recordへ置換)。
 * </p>
 */

public record FloatPosTemplate(NormalFlowPosFields common, FloatSide floating, Kind kind) {
	public enum Kind {
		NORMAL, FOOTNOTE, PAGE_TOP, PAGE_BOTTOM
	}

	public static FloatPosTemplate freeze(final FloatPos source) {
		final Kind kind = source instanceof FootnotePos ? Kind.FOOTNOTE
				: source instanceof PageFloatPos pageFloat ? pageFloat.top ? Kind.PAGE_TOP : Kind.PAGE_BOTTOM
						: Kind.NORMAL;
		return new FloatPosTemplate(NormalFlowPosFields.freeze(source), source.floating, kind);
	}

	/** 呼び出しごとに新品の{@code FloatPos}を返す(複数回呼んでも互いに影響しない)。 */
	public FloatPos materialize() {
		final FloatPos pos = switch (this.kind) {
		case NORMAL -> new FloatPos();
		case FOOTNOTE -> new FootnotePos();
		case PAGE_TOP -> new PageFloatPos(true);
		case PAGE_BOTTOM -> new PageFloatPos(false);
		};
		this.common.materializeInto(pos);
		pos.floating = this.floating;
		return pos;
	}
}
