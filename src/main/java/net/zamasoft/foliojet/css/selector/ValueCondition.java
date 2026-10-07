package net.zamasoft.foliojet.css.selector;

/**
 * A condition that holds only a value (class, ID, pseudo-class, language).
 */
public final class ValueCondition implements Condition {
	private final ConditionType type;

	private final String value;

	public ValueCondition(ConditionType type, String value) {
		this.type = type;
		this.value = value;
	}

	public ConditionType getConditionType() {
		return this.type;
	}

	public String getValue() {
		return this.value;
	}

	public String getLocalName() {
		return null;
	}

	public Specificity getSpecificity() {
		return this.type == ConditionType.ID_CONDITION ? new Specificity(1, 0, 0) : new Specificity(0, 1, 0);
	}

	public String toString() {
		switch (this.type) {
		case CLASS_CONDITION:
			return "." + this.value;
		case ID_CONDITION:
			return "#" + this.value;
		case LANG_CONDITION:
			return ":lang(" + this.value + ")";
		default:
			return ":" + this.value;
		}
	}
}
