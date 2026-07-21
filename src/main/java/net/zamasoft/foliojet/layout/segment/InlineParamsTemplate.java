package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.RectFrame;

/**
 * {@link InlineParams}({@link BoxKind#INLINE}が使う{@code Params}
 * 実装、{@code AbstractTextParams}を直接継承しline固有フィールドを
 * 持たない)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code InlineParams}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A3b Stage1)。
 *
 * <p>
 * 祖先({@code Params}/{@code AbstractTextParams})のフィールドは
 * {@link TextParamsFields}が担う。{@code frame}(既存{@code RectFrame}
 * 実装はfinalフィールドのみで実質不変)はコピー不要。
 * </p>
 */
public final class InlineParamsTemplate {
	private final TextParamsFields common;
	private final RectFrame frame;

	private InlineParamsTemplate(final TextParamsFields common, final RectFrame frame) {
		this.common = common;
		this.frame = frame;
	}

	public static InlineParamsTemplate freeze(final InlineParams source) {
		return new InlineParamsTemplate(TextParamsFields.freeze(source), source.frame);
	}

	/** 呼び出しごとに新品の{@code InlineParams}を返す(複数回呼んでも互いに影響しない)。 */
	public InlineParams materialize() {
		final InlineParams p = new InlineParams();
		this.common.materializeInto(p);
		p.frame = this.frame;
		return p;
	}
}
