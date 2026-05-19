package net.zamasoft.foliojet.css;

import java.io.Serializable;

import org.htmlunit.cssparser.parser.selector.Selector;
import org.htmlunit.cssparser.parser.selector.SelectorSpecificity;

/**
 * CSS規則です。 規則は、選択子とそれに対応するスタイル宣言のペアです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Rule.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Rule implements Cloneable, Serializable {
	private static final long serialVersionUID = 0;

	private final Selector selector;

	private final Declaration declaration;

	private SelectorSpecificity specificity = null;

	public Rule(Selector selector, Declaration declaration) {
		this.selector = selector;
		this.declaration = declaration;
	}

	public Object clone() {
		return new Rule(this.selector, (Declaration) this.declaration.clone());
	}

	/**
	 * 選択子を返します。
	 * 
	 * @return
	 */
	public Selector getSelector() {
		return this.selector;
	}

	/**
	 * スタイル宣言を返します。
	 * 
	 * @return
	 */
	public Declaration getDeclaration() {
		return this.declaration;
	}

	/**
	 * 選択子の固有性を返します。
	 * 
	 * @return
	 */
	public SelectorSpecificity getSpecificity() {
		if (this.specificity == null) {
			this.specificity = this.selector.getSelectorSpecificity();
		}
		return this.specificity;
	}

	public String toString() {
		return this.selector + " { \n" + this.declaration + "}";
	}
}
