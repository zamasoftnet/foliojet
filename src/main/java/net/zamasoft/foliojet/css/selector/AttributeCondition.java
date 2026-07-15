package net.zamasoft.foliojet.css.selector;

/**
 * 属性条件([attr]、[attr=v]、[attr~=v]、[attr|=v]、[attr^=v]、[attr$=v]、[attr*=v])。
 */
public final class AttributeCondition implements Condition {
	private static final long serialVersionUID = 0;

	private final ConditionType type;

	private final String localName;

	private final String value;

	public AttributeCondition(ConditionType type, String localName, String value) {
		this.type = type;
		this.localName = localName;
		this.value = value;
	}

	public ConditionType getConditionType() {
		return this.type;
	}

	public String getValue() {
		return this.value;
	}

	public String getLocalName() {
		return this.localName;
	}

	public Specificity getSpecificity() {
		return new Specificity(0, 1, 0);
	}

	public String toString() {
		if (this.value == null) {
			return "[" + this.localName + "]";
		}
		final String op;
		switch (this.type) {
		case ONE_OF_ATTRIBUTE_CONDITION:
			op = "~=";
			break;
		case BEGIN_HYPHEN_ATTRIBUTE_CONDITION:
			op = "|=";
			break;
		case PREFIX_ATTRIBUTE_CONDITION:
			op = "^=";
			break;
		case SUFFIX_ATTRIBUTE_CONDITION:
			op = "$=";
			break;
		case SUBSTRING_ATTRIBUTE_CONDITION:
			op = "*=";
			break;
		default:
			op = "=";
			break;
		}
		return "[" + this.localName + op + "\"" + this.value + "\"]";
	}
}
