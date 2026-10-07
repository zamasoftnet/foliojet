package net.zamasoft.foliojet.css.selector;

import java.util.Collections;
import java.util.List;

/**
 * A condition taking a list of selectors as its argument (:not / :is / :where).
 */
public final class SelectorListCondition implements Condition {
	private final ConditionType type;

	private final List<Selector> selectors;

	public SelectorListCondition(ConditionType type, List<Selector> selectors) {
		assert type == ConditionType.NOT_CONDITION || type == ConditionType.IS_CONDITION
				|| type == ConditionType.WHERE_CONDITION || type == ConditionType.HAS_CONDITION;
		this.type = type;
		this.selectors = Collections.unmodifiableList(selectors);
	}

	public ConditionType getConditionType() {
		return this.type;
	}

	public List<Selector> getSelectors() {
		return this.selectors;
	}

	public String getValue() {
		StringBuilder buff = new StringBuilder();
		for (Selector selector : this.selectors) {
			if (buff.length() > 0) {
				buff.append(", ");
			}
			buff.append(selector);
		}
		return buff.toString();
	}

	public String getLocalName() {
		return null;
	}

	public Specificity getSpecificity() {
		// :where() always has zero specificity, regardless of its arguments (CSS Selectors4 specification).
		// :not()/:is()/:has() take the greatest specificity in the argument list.
		if (this.type == ConditionType.WHERE_CONDITION) {
			return new Specificity(0, 0, 0);
		}
		Specificity max = new Specificity(0, 0, 0);
		for (Selector selector : this.selectors) {
			Specificity s = selector.getSpecificity();
			if (s.compareTo(max) > 0) {
				max = s;
			}
		}
		return max;
	}

	public String toString() {
		String name = switch (this.type) {
		case NOT_CONDITION -> ":not(";
		case WHERE_CONDITION -> ":where(";
		case HAS_CONDITION -> ":has(";
		default -> ":is(";
		};
		return name + this.getValue() + ")";
	}
}
