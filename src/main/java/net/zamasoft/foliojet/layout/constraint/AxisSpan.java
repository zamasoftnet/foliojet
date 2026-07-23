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
 */
public record AxisSpan(double start, double end) {
	public AxisSpan {
		if (start > end) {
			throw new IllegalArgumentException("start(" + start + ") > end(" + end + ")");
		}
	}

	public double extent() {
		return this.end - this.start;
	}
}
