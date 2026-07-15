package net.zamasoft.foliojet.css.selector;

/**
 * 結合子(子・子孫・隣接・一般兄弟)によるセレクタ。
 * ancestor が左側の文脈、simple が右側の単純セレクタです。
 */
public final class CombinatorSelector implements Selector {
	private final SelectorType type;

	private final Selector ancestor;

	private final SimpleSelector simple;

	public CombinatorSelector(SelectorType type, Selector ancestor, SimpleSelector simple) {
		assert type == SelectorType.CHILD_SELECTOR || type == SelectorType.DESCENDANT_SELECTOR
				|| type == SelectorType.DIRECT_ADJACENT_SELECTOR
				|| type == SelectorType.GENERAL_ADJACENT_SELECTOR;
		this.type = type;
		this.ancestor = ancestor;
		this.simple = simple;
	}

	public SelectorType getSelectorType() {
		return this.type;
	}

	public Selector getAncestorSelector() {
		return this.ancestor;
	}

	public SimpleSelector getSimpleSelector() {
		return this.simple;
	}

	public Specificity getSpecificity() {
		return this.ancestor.getSpecificity().add(this.simple.getSpecificity());
	}

	public String toString() {
		final String op;
		switch (this.type) {
		case CHILD_SELECTOR:
			op = " > ";
			break;
		case DIRECT_ADJACENT_SELECTOR:
			op = " + ";
			break;
		case GENERAL_ADJACENT_SELECTOR:
			op = " ~ ";
			break;
		default:
			op = this.simple.getSelectorType() == SelectorType.PSEUDO_ELEMENT_SELECTOR ? "" : " ";
			break;
		}
		return this.ancestor.toString() + op + this.simple.toString();
	}
}
