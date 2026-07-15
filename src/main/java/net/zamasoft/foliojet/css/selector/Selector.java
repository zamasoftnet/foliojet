package net.zamasoft.foliojet.css.selector;

import java.io.Serializable;

/**
 * 内部セレクタモデル。パーサー(ph-css)の構文木から変換されます。
 */
public interface Selector extends Serializable {
	public enum SelectorType {
		ELEMENT_NODE_SELECTOR, PSEUDO_ELEMENT_SELECTOR, CHILD_SELECTOR, DESCENDANT_SELECTOR,
		DIRECT_ADJACENT_SELECTOR, GENERAL_ADJACENT_SELECTOR
	}

	public SelectorType getSelectorType();

	/**
	 * このセレクタの右端の単純セレクタを返します。単純セレクタ自身はそれ自体を返します。
	 */
	public SimpleSelector getSimpleSelector();

	public Specificity getSpecificity();
}
