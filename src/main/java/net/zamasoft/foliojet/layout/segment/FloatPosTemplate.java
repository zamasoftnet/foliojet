package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.box.params.PageMarginNotePos;
import net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams;

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
 * 不変recordへ置換)。{@code shapeOutside}({@code shape-outside}、
 * 2026-08-29)は全フィールドfinalの不変値なので参照をそのまま持つ——
 * ここで運ばないとセグメント再生後の浮動体から形状が黙って消える。
 * </p>
 */

public record FloatPosTemplate(NormalFlowPosFields common, FloatSide floating, Kind kind,
		ShapeOutsideParams shapeOutside) {
	public enum Kind {
		NORMAL, FOOTNOTE, PAGE_TOP, PAGE_BOTTOM, PAGE_NOTE_START, PAGE_NOTE_END
	}

	public static FloatPosTemplate freeze(final FloatPos source) {
		final Kind kind = source instanceof FootnotePos ? Kind.FOOTNOTE
				: source instanceof PageMarginNotePos note ? note.start ? Kind.PAGE_NOTE_START : Kind.PAGE_NOTE_END
				: source instanceof PageFloatPos pageFloat ? pageFloat.top ? Kind.PAGE_TOP : Kind.PAGE_BOTTOM
						: Kind.NORMAL;
		return new FloatPosTemplate(NormalFlowPosFields.freeze(source), source.floating, kind, source.shapeOutside);
	}

	/** 呼び出しごとに新品の{@code FloatPos}を返す(複数回呼んでも互いに影響しない)。 */
	public FloatPos materialize() {
		final FloatPos pos = switch (this.kind) {
		case NORMAL -> new FloatPos();
		case FOOTNOTE -> new FootnotePos();
		case PAGE_TOP -> new PageFloatPos(true);
		case PAGE_BOTTOM -> new PageFloatPos(false);
		case PAGE_NOTE_START -> new PageMarginNotePos(true);
		case PAGE_NOTE_END -> new PageMarginNotePos(false);
		};
		this.common.materializeInto(pos);
		pos.floating = this.floating;
		pos.shapeOutside = this.shapeOutside;
		return pos;
	}
}
