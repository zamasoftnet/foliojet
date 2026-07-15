package net.zamasoft.foliojet.css;


import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Specificity;

/**
 * CSS規則です。 規則は、選択子とそれに対応するスタイル宣言のペアです。
 * スタイルシート構築後は不変であり、複数スレッドから共有できます。
 *
 * @author MIYABE Tatsuhiko
 */
public class Rule {
	private final Selector selector;

	private final Declaration declaration;

	/** スタイルシート内の出現順。固有性が等しい規則の優先順位を決める。 */
	private final int order;

	private transient Specificity specificity = null;

	public Rule(Selector selector, Declaration declaration, int order) {
		this.selector = selector;
		this.declaration = declaration;
		this.order = order;
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
	 * スタイルシート内の出現順を返します。
	 *
	 * @return
	 */
	public int getOrder() {
		return this.order;
	}

	/**
	 * 選択子の固有性を返します。
	 *
	 * @return
	 */
	public Specificity getSpecificity() {
		if (this.specificity == null) {
			this.specificity = this.selector.getSpecificity();
		}
		return this.specificity;
	}

	public String toString() {
		return this.selector + " { \n" + this.declaration + "}";
	}
}
