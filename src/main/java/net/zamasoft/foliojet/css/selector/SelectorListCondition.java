package net.zamasoft.foliojet.css.selector;

import java.util.Collections;
import java.util.List;

/**
 * セレクタのリストを引数に取る条件(:not / :is)。
 */
public final class SelectorListCondition implements Condition {
	private final ConditionType type;

	private final List<Selector> selectors;

	public SelectorListCondition(ConditionType type, List<Selector> selectors) {
		assert type == ConditionType.NOT_CONDITION || type == ConditionType.IS_CONDITION;
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
		return (this.type == ConditionType.NOT_CONDITION ? ":not(" : ":is(") + this.getValue() + ")";
	}
}
