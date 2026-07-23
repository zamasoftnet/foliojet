package net.zamasoft.foliojet.layout.constraint;

/**
 * ある軸(line方向またはpage方向)上の区間です(2026-07-23新設、
 * 排除域のConstraintSpace入力化——`docs/consultations/consult
 * -exclusion-zone-codex.txt`の設計に基づく)。
 *
 * <p>
 * 物理x/yではなく、当該formatting contextの論理line/page軸の値を
 * そのまま保持する({@code net.zamasoft.foliojet.layout.builder
 * .LayoutContext.Floating}の{@code lineStart}/{@code lineEnd}・
 * {@code pageStart}/{@code pageEnd}と同じ座標系)。
 * </p>
 *
 * <p>
 * 意図的に{@code start <= end}を検証しない——既存の排除域計算
 * (`BlockBuilder.startFlowBlock`のmulticol回避等)は浮動体の重なりが
 * 大きい場合に負のline sizeを許容したまま下流へ渡す既存挙動を持つため、
 * この値型もその既存挙動を忠実に再現できる必要がある(挙動変更は
 * このP0段階のスコープ外)。
 * </p>
 */
public record AxisSpan(double start, double end) {
	public double extent() {
		return this.end - this.start;
	}
}
