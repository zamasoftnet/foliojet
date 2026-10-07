package net.zamasoft.foliojet.css.container;

/**
 * A single feature expression in a {@code @container} condition (2026-08-15 stage 3;
 * development record §5).
 *
 * <p>
 * Phase 1 supports only {@code container-type: inline-size},
 * so feature names in the {@code width} and {@code inline-size} families
 * refer to the same axis (inline dimension) and are not distinguished.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class ContainerFeature {
	enum Kind {
		EXACT, MIN, MAX
	}

	final Kind kind;

	/** Length to compare against (pt). */
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
