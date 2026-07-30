package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link TableParams}({@code BlockParams}を直接継承、{@link BoxKind#TABLE}
 * が使う)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code TableParams}をmaterializeするテンプレートです(2026-07-22新設、
 * M6d-A3b。G-1調査後の2026-07-25に一旦撤去され、表セット実装の
 * ユーザー承認——G-1裁定の更新——を受けて2026-07-30に復活)。
 *
 * <p>
 * 記録条件は「内側blockBoxが素の{@code FlowBlockBox}+素の{@code FlowPos}
 * かつparams alias成立」({@code RecordingLayoutSink.boxKind}のTableBox
 * 分岐)。記録側は<b>内側blockBoxのpos</b>を渡す契約——外側
 * {@code TableBox.getPos()}は常に{@code TablePos}で配置種別を持たない。
 * </p>
 *
 * <p>
 * 祖先(`Params`/`AbstractTextParams`/`AbstractLineParams`/
 * `BlockParams`)のフィールドは{@link BlockParamsFields}
 * (`BlockParamsTemplate`と共有)が担う。{@code borderSpacingH}/
 * {@code borderSpacingV}(double)・{@code borderCollapse}/
 * {@code layout}(byte)は全てプリミティブのためそのまま保持する
 * (2026-07-22 Stage2で不変recordへ置換)。
 * </p>
 */
public record TableParamsTemplate(BlockParamsFields common, double borderSpacingH, double borderSpacingV,
		byte borderCollapse, byte layout) {
	public static TableParamsTemplate freeze(final TableParams source) {
		return new TableParamsTemplate(BlockParamsFields.freeze(source), source.borderSpacingH,
				source.borderSpacingV, source.borderCollapse, source.layout);
	}

	/**
	 * 凍結済みの書字方向を返します(E-6増分3b-4——
	 * {@code LayoutSource.containsMixedFlow}が凍結済みStartから読む)。
	 */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** 呼び出しごとに新品の{@code TableParams}を返す(複数回呼んでも互いに影響しない)。 */
	public TableParams materialize() {
		final TableParams p = new TableParams();
		this.common.materializeInto(p);
		p.borderSpacingH = this.borderSpacingH;
		p.borderSpacingV = this.borderSpacingV;
		p.borderCollapse = this.borderCollapse;
		p.layout = this.layout;
		return p;
	}
}
