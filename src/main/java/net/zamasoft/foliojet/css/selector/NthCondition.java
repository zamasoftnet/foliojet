package net.zamasoft.foliojet.css.selector;

/**
 * A condition taking An+B syntax as its argument (:nth-child() / :nth-of-type()).
 *
 * <p>
 * Matches simply by iteratively walking the preceding-sibling chain
 * (CSSElement.precedingElement) toward its beginning to count the position
 * (1P, no lookahead), without adding state to the element stack.
 * Iterates over the chain length but does not recurse.
 * </p>
 */
public final class NthCondition implements Condition {
	private final ConditionType type;

	private final int a;

	private final int b;

	private final String raw;

	public NthCondition(ConditionType type, int a, int b, String raw) {
		assert type == ConditionType.NTH_CHILD_CONDITION || type == ConditionType.NTH_OF_TYPE_CONDITION
				|| type == ConditionType.NTH_LAST_CHILD_CONDITION
				|| type == ConditionType.NTH_LAST_OF_TYPE_CONDITION : "対象外の ConditionType: " + type;
		this.type = type;
		this.a = a;
		this.b = b;
		this.raw = raw;
	}

	public ConditionType getConditionType() {
		return this.type;
	}

	public int getA() {
		return this.a;
	}

	public int getB() {
		return this.b;
	}

	public String getValue() {
		return this.raw;
	}

	public String getLocalName() {
		return null;
	}

	public Specificity getSpecificity() {
		// Pseudo-class specificity (equivalent to a class)
		return new Specificity(0, 1, 0);
	}

	/**
	 * Tests whether the An+B expression matches the given position (one-based).
	 *
	 * @param position one-based position
	 */
	public boolean matches(int position) {
		assert position >= 1 : "position は1始まりです: " + position;
		int diff = position - this.b;
		if (this.a == 0) {
			return diff == 0;
		}
		// Whether there is a nonnegative integer k satisfying diff = a*k
		return diff % this.a == 0 && diff / this.a >= 0;
	}

	public String toString() {
		final String fname;
		switch (this.type) {
		case NTH_CHILD_CONDITION:
			fname = ":nth-child(";
			break;
		case NTH_OF_TYPE_CONDITION:
			fname = ":nth-of-type(";
			break;
		case NTH_LAST_CHILD_CONDITION:
			fname = ":nth-last-child(";
			break;
		default:
			fname = ":nth-last-of-type(";
			break;
		}
		return fname + this.raw + ")";
	}
}
