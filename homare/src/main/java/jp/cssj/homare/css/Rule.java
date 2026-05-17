package jp.cssj.homare.css;

import java.io.Serializable;

import net.zamasoft.pdfg2d.sac.css.CombinatorCondition;
import net.zamasoft.pdfg2d.sac.css.Condition;
import net.zamasoft.pdfg2d.sac.css.ConditionalSelector;
import net.zamasoft.pdfg2d.sac.css.DescendantSelector;
import net.zamasoft.pdfg2d.sac.css.ElementSelector;
import net.zamasoft.pdfg2d.sac.css.NegativeCondition;
import net.zamasoft.pdfg2d.sac.css.NegativeSelector;
import net.zamasoft.pdfg2d.sac.css.Selector;
import net.zamasoft.pdfg2d.sac.css.SiblingSelector;
import net.zamasoft.pdfg2d.sac.css.SimpleSelector;

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

	private int specificity = -1;

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
	public int getSpecificity() {
		if (this.specificity == -1) {
			int[] triplet = new int[3];
			Selector selector = this.selector;
			while (selector != null) {
				SimpleSelector simpleSelector = null;
				switch (selector.getSelectorType()) {
				case Selector.SAC_CHILD_SELECTOR:
				case Selector.SAC_DESCENDANT_SELECTOR:
					DescendantSelector descendantSelector = (DescendantSelector) selector;
					selector = descendantSelector.getAncestorSelector();
					simpleSelector = descendantSelector.getSimpleSelector();
					break;

				case Selector.SAC_DIRECT_ADJACENT_SELECTOR:
					SiblingSelector siblingSelector = (SiblingSelector) selector;
					selector = siblingSelector.getSelector();
					simpleSelector = siblingSelector.getSiblingSelector();
					break;

				default:
					simpleSelector = (SimpleSelector) selector;
					selector = null;
					break;
				}
				while (simpleSelector != null) {
					switch (simpleSelector.getSelectorType()) {
					case Selector.SAC_NEGATIVE_SELECTOR:
						NegativeSelector negativeSelector = (NegativeSelector) simpleSelector;
						simpleSelector = negativeSelector.getSimpleSelector();
						break;

					case Selector.SAC_ANY_NODE_SELECTOR:
					case Selector.SAC_ROOT_NODE_SELECTOR:
						simpleSelector = null;
						break;

					case Selector.SAC_ELEMENT_NODE_SELECTOR:
						ElementSelector elementSelector = (ElementSelector) simpleSelector;
						String localName = elementSelector.getLocalName();
						if (localName != null) {
							++triplet[2];
						}
						simpleSelector = null;
						break;

					case Selector.SAC_CONDITIONAL_SELECTOR:
						ConditionalSelector conditionalSelector = (ConditionalSelector) simpleSelector;
						Condition condition = conditionalSelector.getCondition();
						computeCondition(condition, triplet);
						simpleSelector = conditionalSelector.getSimpleSelector();
						break;

					case Selector.SAC_CDATA_SECTION_NODE_SELECTOR:
					case Selector.SAC_COMMENT_NODE_SELECTOR:
					case Selector.SAC_PSEUDO_ELEMENT_SELECTOR:
					case Selector.SAC_TEXT_NODE_SELECTOR:
						++triplet[1];
						simpleSelector = null;
						break;

					default:
						throw new IllegalStateException();
					}
				}
			}
			this.specificity = (triplet[0] * 0x10000) + (triplet[1] * 0x100) + (triplet[2]);
		}
		return this.specificity;
	}

	private static void computeCondition(Condition condition, int[] triplet) {
		switch (condition.getConditionType()) {
		case Condition.SAC_ID_CONDITION:
			++triplet[0];
			break;

		case Condition.SAC_AND_CONDITION:
		case Condition.SAC_OR_CONDITION:
			CombinatorCondition combinatorCondition = (CombinatorCondition) condition;
			computeCondition(combinatorCondition.getFirstCondition(), triplet);
			computeCondition(combinatorCondition.getSecondCondition(), triplet);
			break;

		case Condition.SAC_NEGATIVE_CONDITION:
			NegativeCondition negativeCondition = (NegativeCondition) condition;
			computeCondition(negativeCondition.getCondition(), triplet);
			break;

		default:
			++triplet[1];
			break;
		}
	}

	public String toString() {
		return this.selector + " { \n" + this.declaration + "}";
	}
}
