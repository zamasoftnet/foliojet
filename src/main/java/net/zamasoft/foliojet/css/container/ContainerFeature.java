package net.zamasoft.foliojet.css.container;

/**
 * {@code @container}条件内の単一特性式です(2026-08-15段3——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 *
 * <p>
 * 第1段階では{@code container-type: inline-size}だけを対象とするため、
 * {@code width}系と{@code inline-size}系の特性名は同じ軸(インライン寸法)
 * として扱い、区別しない。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class ContainerFeature {
	enum Kind {
		EXACT, MIN, MAX
	}

	final Kind kind;

	/** 比較対象の長さ(pt)。 */
	final double length;

	ContainerFeature(Kind kind, double length) {
		this.kind = kind;
		this.length = length;
	}

	boolean matches(double inlineSize) {
		switch (this.kind) {
		case EXACT:
			return inlineSize == this.length;
		case MIN:
			return inlineSize >= this.length;
		case MAX:
			return inlineSize <= this.length;
		default:
			throw new IllegalStateException();
		}
	}
}
