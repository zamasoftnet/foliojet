package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link TableParams}({@code BlockParams}を直接継承、
 * {@link BoxKind#TABLE}——ただし{@code box.getPos() instanceof
 * FlowPos}の場合のみ記録可能、既存コード({@code StyleBuilder}の
 * kind判定)確認済み——が使う)の内容をfreezeし、呼び出しごとに独立した
 * 新品の{@code TableParams}をmaterializeするテンプレートです
 * (2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * <b>2026-07-25(F-4)の実測による訂正</b>: その記録条件
 * {@code box.getPos() instanceof FlowPos}は{@code TableBox}に対して
 * <b>恒偽</b>({@code TableBox.getPos()}は常に{@code TablePos}を返し、
 * {@code TablePos}は{@code FlowPos}を継承しない)。よって
 * {@link BoxKind#TABLE}は現在一度も記録されず、このテンプレートも
 * production経路では使われない(単体テストのみ)。記録条件を内側の
 * {@code TableBox.getBlockBox().getPos()}へ正す増分で有効化される。
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
