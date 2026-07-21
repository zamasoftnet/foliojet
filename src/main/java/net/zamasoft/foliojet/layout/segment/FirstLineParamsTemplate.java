package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.FirstLineParams;

/**
 * {@link FirstLineParams}の内容をfreezeし、呼び出しごとに独立した
 * 新品の{@code FirstLineParams}をmaterializeするテンプレートです
 * (2026-07-22新設、M6d-A3b Stage1)。{@code BlockParams.firstLineStyle}
 * が非nullの場合、この型を使って再帰的にfreeze/materializeする。
 *
 * <p>
 * 段階1(この実装): {@link LineParamsFields}(共通祖先フィールド)+
 * {@code background}(既存{@code Background}実装はfinalフィールドのみで
 * 実質不変、コピー不要)を保持する。段階2でこの内部状態自体を不変
 * recordへ置換する予定。
 * </p>
 */
public final class FirstLineParamsTemplate {
	private final LineParamsFields common;
	private final Background background;

	private FirstLineParamsTemplate(final LineParamsFields common, final Background background) {
		this.common = common;
		this.background = background;
	}

	public static FirstLineParamsTemplate freeze(final FirstLineParams source) {
		return new FirstLineParamsTemplate(LineParamsFields.freeze(source), source.background);
	}

	/** 呼び出しごとに新品の{@code FirstLineParams}を返す(複数回呼んでも互いに影響しない)。 */
	public FirstLineParams materialize() {
		final FirstLineParams p = new FirstLineParams();
		this.common.materializeInto(p);
		p.background = this.background;
		return p;
	}
}
