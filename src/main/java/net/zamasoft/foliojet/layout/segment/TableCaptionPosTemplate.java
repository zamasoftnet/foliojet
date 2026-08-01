package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;

/**
 * {@link TableCaptionPos}(表キャプションの配置パラメータ、
 * {@link BoxKind#CAPTION}が使う)の内容をfreezeし、呼び出しごとに
 * 独立した新品の{@code TableCaptionPos}をmaterializeするテンプレート
 * です(caption recipe化C1、2026-08-01——
 * consult-codex-2026-08-01-caption-recipe.txt)。
 *
 * <p>
 * {@code TableCaptionPos}は{@code FlowPos}+{@code captionSide}のため、
 * {@link FlowPosTemplate}と同じ構成に{@code captionSide}(enum、不変)を
 * 加えるだけでよい。
 * </p>
 */
public record TableCaptionPosTemplate(NormalFlowPosFields common, Align align, byte columnSpan,
		net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItem,
		net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexItem, CaptionSideMode captionSide) {
	public static TableCaptionPosTemplate freeze(final TableCaptionPos source) {
		return new TableCaptionPosTemplate(NormalFlowPosFields.freeze(source), source.align, source.columnSpan,
				source.gridItem, source.flexItem, source.captionSide);
	}

	/** 呼び出しごとに新品の{@code TableCaptionPos}を返す。 */
	public TableCaptionPos materialize() {
		final TableCaptionPos pos = new TableCaptionPos();
		this.common.materializeInto(pos);
		pos.align = this.align;
		pos.columnSpan = this.columnSpan;
		pos.gridItem = this.gridItem;
		pos.flexItem = this.flexItem;
		pos.captionSide = this.captionSide;
		return pos;
	}
}
