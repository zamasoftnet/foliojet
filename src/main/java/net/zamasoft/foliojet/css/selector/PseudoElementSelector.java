package net.zamasoft.foliojet.css.selector;

/**
 * 擬似要素セレクタ(::before 等)。
 */
public final class PseudoElementSelector implements SimpleSelector {
	private final String localName;

	public PseudoElementSelector(String localName) {
		this.localName = localName;
	}

	public String getLocalName() {
		return this.localName;
	}

	public SelectorType getSelectorType() {
		return SelectorType.PSEUDO_ELEMENT_SELECTOR;
	}

	public SimpleSelector getSimpleSelector() {
		return this;
	}

	public Specificity getSpecificity() {
		return new Specificity(0, 0, 1);
	}

	public String toString() {
		return "::" + this.localName;
	}
}
