package net.zamasoft.foliojet.css.selector;


/**
 * Internal selector model, converted from the parser's (ph-css) syntax tree.
 */
public interface Selector {
	public enum SelectorType {
		ELEMENT_NODE_SELECTOR, PSEUDO_ELEMENT_SELECTOR, CHILD_SELECTOR, DESCENDANT_SELECTOR,
		DIRECT_ADJACENT_SELECTOR, GENERAL_ADJACENT_SELECTOR
	}

	public SelectorType getSelectorType();

	/**
	 * Returns this selector's rightmost simple selector. A simple selector returns itself.
	 */
	public SimpleSelector getSimpleSelector();

	public Specificity getSpecificity();
}
