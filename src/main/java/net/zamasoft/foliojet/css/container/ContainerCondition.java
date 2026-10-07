package net.zamasoft.foliojet.css.container;

import java.util.Collections;
import java.util.List;

/**
 * A {@code @container} condition (2026-08-15 stage 3;
 * development record §5).
 *
 * <p>
 * Grammatically, {@code not} applies only to the entire condition (one parenthesized term),
 * and multiple parenthesized terms can join only with {@code and} (the specification
 * prohibits mixing {@code and} and {@code or} at the same level). {@code or} is outside phase 1 (§5).
 * Conservatively treat unsupported syntax, features, and units as {@link #never()} (always non-matching),
 * following the existing policy for unsupported {@code @media} features
 * ({@link net.zamasoft.foliojet.css.CSSStyleSheetBuilder}
 * in {@code evaluateMediaExpression}).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerCondition {
	private static final ContainerCondition NEVER = new ContainerCondition(false, false, Collections.emptyList());

	private final boolean valid;

	private final boolean negate;

	private final List<ContainerFeature> features;

	private ContainerCondition(boolean valid, boolean negate, List<ContainerFeature> features) {
		this.valid = valid;
		this.negate = negate;
		this.features = features;
	}

	/** An always non-matching condition (unsupported syntax, feature, or unit, or a parse failure). */
	static ContainerCondition never() {
		return NEVER;
	}

	/** {@code (a) and (b) and ...}: joins one or more feature expressions with AND. */
	static ContainerCondition and(List<ContainerFeature> features) {
		return new ContainerCondition(true, false, List.copyOf(features));
	}

	/** {@code not (a)}: negates one feature expression. */
	static ContainerCondition not(ContainerFeature feature) {
		return new ContainerCondition(true, true, List.of(feature));
	}

	/**
	 * Evaluates the condition against the container's used inline-size (pt).
	 * Conditions that could not be parsed always return {@code false}.
	 */
	public boolean evaluate(double inlineSize) {
		if (!this.valid) {
			return false;
		}
		boolean allMatch = true;
		for (final ContainerFeature feature : this.features) {
			if (!feature.matches(inlineSize)) {
				allMatch = false;
				break;
			}
		}
		return this.negate ? !allMatch : allMatch;
	}

	/** Whether parsing succeeded (not {@link #never()} due to unsupported syntax). */
	public boolean isValid() {
		return this.valid;
	}
}
