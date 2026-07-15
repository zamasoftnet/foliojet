package net.zamasoft.foliojet.css.selector;

import java.util.Collections;
import java.util.List;

/**
 * 要素セレクタ(条件つき)。localName が null の場合は全称セレクタです。
 */
public final class ElementSelector implements SimpleSelector {
	private static final long serialVersionUID = 0;

	private final String localName;

	private final List<Condition> conditions;

	public ElementSelector(String localName, List<Condition> conditions) {
		this.localName = localName;
		this.conditions = conditions == null || conditions.isEmpty() ? Collections.emptyList()
				: Collections.unmodifiableList(conditions);
	}

	public String getLocalName() {
		return this.localName;
	}

	public List<Condition> getConditions() {
		return this.conditions;
	}

	public SelectorType getSelectorType() {
		return SelectorType.ELEMENT_NODE_SELECTOR;
	}

	public SimpleSelector getSimpleSelector() {
		return this;
	}

	public Specificity getSpecificity() {
		Specificity s = new Specificity(0, 0, this.localName == null ? 0 : 1);
		for (Condition condition : this.conditions) {
			s = s.add(condition.getSpecificity());
		}
		return s;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append(this.localName == null ? "*" : this.localName);
		for (Condition condition : this.conditions) {
			buff.append(condition);
		}
		return buff.toString();
	}
}
