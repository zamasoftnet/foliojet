package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

/**
 * {@link InnerTableParams}({@code Params}を直接継承(``AbstractTextParams``
 * を経由しない)、{@link BoxKind#TABLE_ROW_GROUP}/
 * {@link BoxKind#TABLE_ROW}/{@link BoxKind#TABLE_COLUMN_GROUP}/
 * {@link BoxKind#TABLE_COLUMN}が使う)の内容をfreezeし、呼び出しごとに
 * 独立した新品の{@code InnerTableParams}をmaterializeするテンプレート
 * です(2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * 祖先({@code Params})のフィールドは{@link ParamsFields}
 * (`TextParamsFields`とも共有する)が担う。{@code background}/
 * {@code border}/{@code size}系/{@code pageBreakInside}は既存実装が
 * finalフィールドのみで実質不変と確認済みのため、参照をそのまま保持する
 * (コピー不要、2026-07-22 Stage2で不変recordへ置換)。
 * </p>
 */
public record InnerTableParamsTemplate(ParamsFields common, Background background, RectBorder border, Length size,
		Length minSize, Length maxSize, PageBreakMode pageBreakInside) {
	public static InnerTableParamsTemplate freeze(final InnerTableParams source) {
		return new InnerTableParamsTemplate(ParamsFields.freeze(source), source.background, source.border,
				source.size, source.minSize, source.maxSize, source.pageBreakInside);
	}

	/** 呼び出しごとに新品の{@code InnerTableParams}を返す(複数回呼んでも互いに影響しない)。 */
	public InnerTableParams materialize() {
		final InnerTableParams p = new InnerTableParams();
		this.common.materializeInto(p);
		p.background = this.background;
		p.border = this.border;
		p.size = this.size;
		p.minSize = this.minSize;
		p.maxSize = this.maxSize;
		p.pageBreakInside = this.pageBreakInside;
		return p;
	}
}
