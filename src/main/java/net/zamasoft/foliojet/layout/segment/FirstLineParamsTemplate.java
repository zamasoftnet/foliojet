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
 * {@link LineParamsFields}(共通祖先フィールド)+{@code background}
 * (既存{@code Background}実装はfinalフィールドのみで実質不変、
 * コピー不要)を保持する(2026-07-22 Stage2、不変recordへ置換)。
 * </p>
 */
public record FirstLineParamsTemplate(LineParamsFields common, Background background) {
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
