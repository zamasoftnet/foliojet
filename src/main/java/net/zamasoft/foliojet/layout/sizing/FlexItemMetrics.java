package net.zamasoft.foliojet.layout.sizing;

/**
 * Flex itemの主軸計測値です(Flex F1b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q3)。§9.7の伸縮解決
 * (FlexLengthResolver)と行分割(FlexLineBreaker)の入力になる、
 * 1 itemぶんの数値スナップショット。全てcontent-box内寸(inner)の
 * pt値で、box-sizingの正規化は{@link FlexItemMetricsResolver}が済ませる。
 *
 * <p>
 * {@code outerMainExtra}(margin+border+padding)が別建てなのは§9.7の
 * 規定による——free spaceとfactor選択はouter size、scaled shrink factorは
 * inner flex base sizeを使うため、innerだけでは不足する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record FlexItemMetrics(int sourceIndex, double flexBaseMain, double hypotheticalMain, double minMain,
		double maxMain, double outerMainExtra, double grow, double shrink) {

	/** outer hypothetical main size(行分割とfree space計算の単位——§9.3)。 */
	public double outerHypotheticalMain() {
		return this.hypotheticalMain + this.outerMainExtra;
	}

	/** outer flex base size(initial free space計算の単位——§9.7.4)。 */
	public double outerBaseMain() {
		return this.flexBaseMain + this.outerMainExtra;
	}
}
