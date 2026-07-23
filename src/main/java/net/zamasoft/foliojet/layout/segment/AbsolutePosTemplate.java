package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AutoPosition;
import net.zamasoft.foliojet.layout.box.params.Fiducial;
import net.zamasoft.foliojet.layout.box.params.Insets;

/**
 * {@link AbsolutePos}(絶対配置の配置パラメータ、{@link ReplacedRecipe
 * .Absolute}が使う)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code AbsolutePos}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A Replaced要素対応)。
 *
 * <p>
 * {@code AbsolutePos}は{@code Pos}を直接実装し({@code AbstractStaticPos}
 * を継承しない、{@code offset}を持たない)、フィールドは
 * {@code location}({@code Insets}、finalフィールドのみで実質不変)・
 * {@code autoPosition}/{@code fiducial}(enum)のみ——{@code FlowPos}系と
 * 違い共有できる祖先{@code *Fields}ヘルパーが無いため単独で完結する
 * (2026-07-22 Stage2で不変recordへ置換)。
 * </p>
 */
public record AbsolutePosTemplate(Insets location, AutoPosition autoPosition, Fiducial fiducial) {
	public static AbsolutePosTemplate freeze(final AbsolutePos source) {
		return new AbsolutePosTemplate(source.location, source.autoPosition, source.fiducial);
	}

	/** 呼び出しごとに新品の{@code AbsolutePos}を返す(複数回呼んでも互いに影響しない)。 */
	public AbsolutePos materialize() {
		final AbsolutePos pos = new AbsolutePos();
		pos.location = this.location;
		pos.autoPosition = this.autoPosition;
		pos.fiducial = this.fiducial;
		return pos;
	}
}
