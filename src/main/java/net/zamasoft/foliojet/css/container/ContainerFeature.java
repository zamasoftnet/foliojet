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
		EXACT, MIN, MAX,
		/** Strictly less or greater than the length: the range syntax's {@code <} and {@code >} (2026-10-11). */
		LT, GT
	}

	final Kind kind;

	/** Length to compare against (pt), or its number of em or rem (2026-10-11). */
	final double length, em, rem;

	ContainerFeature(Kind kind, double length, double em, double rem) {
		this.kind = kind;
		this.length = length;
		this.em = em;
		this.rem = rem;
	}

	/**
	 * Compares the container's inline size with the length; em and rem stand for the container's and the root's font
	 * size (pt).
	 */
	boolean matches(double inlineSize, double emSize, double remSize) {
		final double length = this.length + this.em * emSize + this.rem * remSize;
		switch (this.kind) {
		case EXACT:
			return inlineSize == length;
		case MIN:
			return inlineSize >= length;
		case MAX:
			return inlineSize <= length;
		case LT:
			return inlineSize < length;
		case GT:
			return inlineSize > length;
		default:
			throw new IllegalStateException();
		}
	}
}
