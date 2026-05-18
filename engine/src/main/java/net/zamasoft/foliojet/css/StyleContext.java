package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import net.zamasoft.foliojet.css.CSSStyleSheet.PageContent;
import net.zamasoft.foliojet.xml.xhtml.XHTML;
import net.zamasoft.sac.css.AttributeCondition;
import net.zamasoft.sac.css.CombinatorCondition;
import net.zamasoft.sac.css.Condition;
import net.zamasoft.sac.css.ConditionalSelector;
import net.zamasoft.sac.css.DescendantSelector;
import net.zamasoft.sac.css.ElementSelector;
import net.zamasoft.sac.css.LangCondition;
import net.zamasoft.sac.css.Selector;
import net.zamasoft.sac.css.SiblingSelector;
import net.zamasoft.sac.css.SimpleSelector;

public class StyleContext {

	private static final boolean DEBUG = false;

	/** 上位の要素のリスト。 */
	private final List<CSSElement> elementStack = new ArrayList<CSSElement>();

	public final CSSStyleSheet styleSheet;

	public StyleContext(CSSStyleSheet styleSheet) {
		this.styleSheet = styleSheet;
	}

	public StyleContext copy(int up) {
		StyleContext styleContext = new StyleContext(this.styleSheet);
		for (int i = 0; i < this.elementStack.size() - up; ++i) {
			styleContext.elementStack.add(this.elementStack.get(i));
		}
		return styleContext;
	}

	private static String elementStr(CSSElement ce) {
		StringBuffer buff = new StringBuffer();
		if (ce.lName != null) {
			buff.append(ce.lName);
		} else {
			buff.append(ce.pseudoClasses);
		}
		buff.append('/');
		return buff.toString();
	}

	/**
	 * 要素の開始を通知します。
	 * 
	 * @param ce
	 */
	public void startElement(CSSElement ce) {
		if (DEBUG) {
			System.out.println(elementStr(ce));
		}
		this.elementStack.add(ce);
	}

	/**
	 * 要素の終了を通知します。
	 */
	public void endElement() {
		CSSElement ce = (CSSElement) this.elementStack.remove(this.elementStack.size() - 1);
		if (DEBUG) {
			System.out.println("/" + elementStr(ce));
		}
	}

	/**
	 * ページの開始に対して、対応するスタイル宣言を返します。
	 * 
	 * @param page
	 * @return
	 */
	public Declaration nextPage(CSSElement page) {
		Declaration result = new Declaration();
		result.merge(this.styleSheet.page);
		if (page.isPseudoClass(CSSElement.PC_LEFT)) {
			result.merge(this.styleSheet.leftPage);
		}
		if (page.isPseudoClass(CSSElement.PC_RIGHT)) {
			result.merge(this.styleSheet.rightPage);
		}
		if (page.isPseudoClass(CSSElement.PC_FIRST)) {
			result.merge(this.styleSheet.firstPage);
		}
		return result;
	}

	public List<PageContent> getPageContents() {
		return this.styleSheet.pageContents;
	}

	/**
	 * 現在の要素に対応するスタイル宣言と与えられたスタイル宣言をマージします。
	 * 
	 * @return
	 */
	public Declaration merge(Declaration declaration) {
		if (DEBUG) {
			for (int i = 0; i < this.elementStack.size(); ++i) {
				CSSElement ce = (CSSElement) this.elementStack.get(i);
				System.out.print(elementStr(ce));
			}
			System.out.println();
		}

		// 結果が確定したもの
		List<Rule> result = null;
		for (Iterator<?> i = this.styleSheet.selectorToRule.values().iterator(); i.hasNext();) {
			Rule rule = (Rule) i.next();
			Selector selector = rule.getSelector();
			boolean first = true;// 最初のセレクタのため、該当する要素が直ちにあらわれなければならない。
			boolean child = false;// 子セレクタのため、擬似要素をのぞいて該当する要素が直ちにあらわれなければならない。
			boolean sibling = false;// 隣接セレクタのため、スタックをあがらずに隣の要素に移る
			CSSElement ce = null;
			NEXT_RULE: for (int j = this.elementStack.size() - 1; j >= 0; --j) {
				if (sibling) {
					sibling = false;
				} else {
					ce = (CSSElement) this.elementStack.get(j);
				}
				switch (selector.getSelectorType()) {
				// 子セレクタ
				case Selector.SAC_CHILD_SELECTOR: {
					DescendantSelector descendantSelector = (DescendantSelector) selector;
					SimpleSelector simpleSelector = descendantSelector.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = descendantSelector.getAncestorSelector();
						child = true;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;

				// 子孫セレクタ
				case Selector.SAC_DESCENDANT_SELECTOR: {
					DescendantSelector descendantSelector = (DescendantSelector) selector;
					SimpleSelector simpleSelector = descendantSelector.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = descendantSelector.getAncestorSelector();
						child = false;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;

				// 隣接セレクタ
				case Selector.SAC_DIRECT_ADJACENT_SELECTOR: {
					SiblingSelector siblingSelector = (SiblingSelector) selector;
					SimpleSelector simpleSelector = siblingSelector.getSiblingSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = siblingSelector.getSelector();
						child = true;
						ce = ce.precedingElement;
						if (ce == null) {
							break NEXT_RULE;
						}
						++j;
						sibling = true;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;

				// 単純セレクタ
				default: {
					SimpleSelector simpleSelector = (SimpleSelector) selector;
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						if (result == null) {
							result = new ArrayList<Rule>();
						}
						result.add(rule);
						break NEXT_RULE;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;
				}
				first = false;
			}
		}

		if (result == null) {
			return declaration;
		}
		if (declaration == null) {
			declaration = new Declaration();
		}

		// 固有性の順に整列
		// このソートは安定なので(Javadocより)文書中の順序(SPEC CSS2 6.4.1)に影響はありません。
		Collections.sort(result, RuleComparator.INSTANCE);

		// 合成
		for (int i = 0; i < result.size(); ++i) {
			Rule rule = (Rule) result.get(i);
			Declaration tempDecl = rule.getDeclaration();
			declaration.merge(tempDecl);
		}
		return declaration;
	}

	private static boolean evaluateSimpleSelector(SimpleSelector selector, CSSElement ce) {
		switch (selector.getSelectorType()) {
		// 要素セレクタ
		case Selector.SAC_ELEMENT_NODE_SELECTOR: {
			ElementSelector elementSelector = (ElementSelector) selector;
			if (ce.isPseudoElement()) {
				return false;
			}

			String uri = elementSelector.getNamespaceURI();
			String name = elementSelector.getLocalName();

			if (uri == null) {
				if (name == null) {
					return true;
				}
				if (ce.uri == null) {
					return false;
				}
				if (ce.uri.equals(XHTML.URI)) {
					name = name.toLowerCase();
				}
				return name.equals(ce.lName);
			}

			return ((name == null || name.equals(ce.lName)) && uri.equals(ce.uri));
		}

		// 擬似要素セレクタ
		case Selector.SAC_PSEUDO_ELEMENT_SELECTOR: {
			if (!ce.isPseudoElement()) {
				return false;
			}
			ElementSelector elementSelector = (ElementSelector) selector;
			String name = elementSelector.getLocalName();
			return name.equals(ce.lName);
		}

		// 条件セレクタ
		case Selector.SAC_CONDITIONAL_SELECTOR: {
			ConditionalSelector conditionalSelector = (ConditionalSelector) selector;
			return evaluateCondition(conditionalSelector.getCondition(), ce)
					&& evaluateSimpleSelector(conditionalSelector.getSimpleSelector(), ce);
		}

		// Not Implemented in CSS2.
		// case Selector.SAC_ANY_NODE_SELECTOR:
		// case Selector.SAC_ROOT_NODE_SELECTOR:
		// case Selector.SAC_CDATA_SECTION_NODE_SELECTOR:
		// case Selector.SAC_COMMENT_NODE_SELECTOR:
		// case Selector.SAC_NEGATIVE_SELECTOR:
		// case Selector.SAC_TEXT_NODE_SELECTOR:
		default:
			throw new IllegalStateException(String.valueOf(selector.getSelectorType()));
		}
	}

	private static boolean evaluateCondition(Condition condition, CSSElement ce) {
		switch (condition.getConditionType()) {
		// AND条件
		case Condition.SAC_AND_CONDITION: {
			CombinatorCondition combinatorCondition = (CombinatorCondition) condition;
			return evaluateCondition(combinatorCondition.getFirstCondition(), ce)
					&& evaluateCondition(combinatorCondition.getSecondCondition(), ce);
		}

		// クラス条件
		case Condition.SAC_CLASS_CONDITION: {
			AttributeCondition classCondition = (AttributeCondition) condition;
			String styleClass = classCondition.getValue();
			return ce.isStyleClass(styleClass);
		}

		// 擬似クラス条件
		case Condition.SAC_PSEUDO_CLASS_CONDITION: {
			AttributeCondition classCondition = (AttributeCondition) condition;
			String pseudoClass = classCondition.getValue();
			if (pseudoClass == null || pseudoClass.length() == 0) {
				return false;
			}
			byte pc = 0;
			switch (pseudoClass.charAt(0)) {
			case 'F':
			case 'f':
				if (pseudoClass.equalsIgnoreCase("first")) {
					pc = CSSElement.PC_FIRST;
				} else if (pseudoClass.equalsIgnoreCase("first-child")) {
					pc = CSSElement.PC_FIRST_CHILD;
				}
				break;
			case 'L':
			case 'l':
				if (pseudoClass.equalsIgnoreCase("link")) {
					pc = CSSElement.PC_LINK;
				} else if (pseudoClass.equalsIgnoreCase("left")) {
					pc = CSSElement.PC_LEFT;
				}
				break;
			case 'R':
			case 'r':
				if (pseudoClass.equalsIgnoreCase("right")) {
					pc = CSSElement.PC_RIGHT;
				}
				else if (pseudoClass.equalsIgnoreCase("root")) {
					pc = CSSElement.PC_ROOT;
				}
				break;
			}
			return ce.isPseudoClass(pc);
		}

		// ID条件
		case Condition.SAC_ID_CONDITION: {
			AttributeCondition classCondition = (AttributeCondition) condition;
			String id = classCondition.getValue();
			return id.equalsIgnoreCase(ce.id);
		}

		// 属性条件
		case Condition.SAC_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String uri = attrCondition.getNamespaceURI();
			String name = attrCondition.getLocalName();
			if (attrCondition.getSpecified()) {
				String value = attrCondition.getValue();
				if (uri == null) {
					return value.equalsIgnoreCase(ce.atts.getValue(name));
				}
				return value.equalsIgnoreCase(ce.atts.getValue(uri, name));
			}
			if (uri == null) {
				return ce.atts.getValue(name) != null;
			}
			return ce.atts.getValue(uri, name) != null;
		}

		// スペース区切り属性値条件
		case Condition.SAC_ONE_OF_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String uri = attrCondition.getNamespaceURI();
			String name = attrCondition.getLocalName();
			String value = attrCondition.getValue();
			String values;
			if (uri == null) {
				values = ce.atts.getValue(name);
			} else {
				values = ce.atts.getValue(uri, name);
			}
			if (values == null) {
				return false;
			}
			for (StringTokenizer i = new StringTokenizer(values, " "); i.hasMoreTokens();) {
				if (i.nextToken().equalsIgnoreCase(value)) {
					return true;
				}
			}
		}
			return false;

		// ハイフン区切り属性値条件
		case Condition.SAC_BEGIN_HYPHEN_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String uri = attrCondition.getNamespaceURI();
			String name = attrCondition.getLocalName();
			String value = attrCondition.getValue();
			String lang;
			if (uri == null) {
				lang = ce.atts.getValue(name);
			} else {
				lang = ce.atts.getValue(uri, name);
			}
			if (lang == null) {
				return false;
			}
			lang = lang.toLowerCase();
			value = value.toLowerCase();
			if (lang.startsWith(value)) {
				return (lang.length() <= value.length() || lang.charAt(value.length()) == '-');
			}
			return false;

		}

		// 言語条件
		case Condition.SAC_LANG_CONDITION: {
			LangCondition langCondition = (LangCondition) condition;
			String value = langCondition.getLang();
			if (ce.lang == null) {
				return false;
			}
			String lang = ce.lang.getLanguage();
			return lang.equalsIgnoreCase(value);
		}

		// Not Implemented in CSS2.
		// case Condition.SAC_OR_CONDITION:
		// case Condition.SAC_NEGATIVE_CONDITION:
		// case Condition.SAC_CONTENT_CONDITION:
		// case Condition.SAC_ONLY_CHILD_CONDITION:
		// case Condition.SAC_ONLY_TYPE_CONDITION:
		// case Condition.SAC_POSITIONAL_CONDITION:

		default:
			throw new IllegalStateException(String.valueOf(condition.getConditionType()));
		}
	}
}

/**
 * 規則を固有性の順に整列するための比較子です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: StyleContext.java 1622 2022-05-02 06:22:56Z miyabe $
 */
class RuleComparator implements Comparator<Object> {
	/**
	 * このクラスのインスタンスを返します。
	 */
	public static final RuleComparator INSTANCE = new RuleComparator();

	private RuleComparator() {
		// singleton
	}

	/**
	 * o1の固有性がo2より大きい場合は1、同じなら0、小さい場合は-1を返します。
	 */
	public int compare(Object o1, Object o2) {
		Rule rule1 = (Rule) o1;
		Rule rule2 = (Rule) o2;
		int a = rule1.getSpecificity();
		int b = rule2.getSpecificity();
		return (a == b) ? 0 : (a < b) ? -1 : 1;
	}

}
