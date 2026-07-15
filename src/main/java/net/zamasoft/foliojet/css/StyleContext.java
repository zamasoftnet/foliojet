package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSStyleSheet.PageContent;
import net.zamasoft.foliojet.css.selector.AttributeCondition;
import net.zamasoft.foliojet.css.selector.CombinatorSelector;
import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SelectorListCondition;
import net.zamasoft.foliojet.css.selector.SimpleSelector;
import net.zamasoft.foliojet.css.selector.Specificity;
import net.zamasoft.foliojet.xml.xhtml.XHTML;

public class StyleContext {

	private static final Logger LOG = Logger.getLogger(StyleContext.class.getName());

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
		StringBuilder buff = new StringBuilder();
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
				case CHILD_SELECTOR: {
					CombinatorSelector combinator = (CombinatorSelector) selector;
					SimpleSelector simpleSelector = combinator.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = combinator.getAncestorSelector();
						child = true;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;

				// 子孫セレクタ
				case DESCENDANT_SELECTOR: {
					CombinatorSelector combinator = (CombinatorSelector) selector;
					SimpleSelector simpleSelector = combinator.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = combinator.getAncestorSelector();
						child = simpleSelector.getSelectorType() == SelectorType.PSEUDO_ELEMENT_SELECTOR;
					} else if (first || (!ce.isPseudoElement() && child)) {
						break NEXT_RULE;
					}
				}
					break;

				// 隣接セレクタ
				case DIRECT_ADJACENT_SELECTOR: {
					CombinatorSelector combinator = (CombinatorSelector) selector;
					SimpleSelector simpleSelector = combinator.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = combinator.getAncestorSelector();
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

				// 一般兄弟セレクタ
				case GENERAL_ADJACENT_SELECTOR: {
					CombinatorSelector combinator = (CombinatorSelector) selector;
					SimpleSelector simpleSelector = combinator.getSimpleSelector();
					if (evaluateSimpleSelector(simpleSelector, ce)) {
						selector = combinator.getAncestorSelector();
						child = true;
						// 先行する兄弟のいずれかが左側セレクタの右端にマッチする位置まで戻る
						ce = ce.precedingElement;
						while (ce != null && !evaluateSimpleSelector(selector.getSimpleSelector(), ce)) {
							ce = ce.precedingElement;
						}
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
					SimpleSelector simpleSelector = selector.getSimpleSelector();
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
		case ELEMENT_NODE_SELECTOR: {
			ElementSelector elementSelector = (ElementSelector) selector;
			if (ce.isPseudoElement()) {
				return false;
			}

			String name = elementSelector.getLocalName();
			if (name != null) {
				if (ce.uri != null && ce.uri.equals(XHTML.URI)) {
					name = name.toLowerCase();
				}
				if (!name.equals(ce.lName)) {
					return false;
				}
			}
			for (Condition condition : elementSelector.getConditions()) {
				if (!evaluateCondition(condition, ce)) {
					return false;
				}
			}
			return true;
		}

		// 擬似要素セレクタ
		case PSEUDO_ELEMENT_SELECTOR: {
			if (!ce.isPseudoElement()) {
				return false;
			}
			PseudoElementSelector elementSelector = (PseudoElementSelector) selector;
			String name = elementSelector.getLocalName();
			return name.equals(ce.lName);
		}

		// 未対応のセレクタは変換を止めず不一致として扱う
		default:
			LOG.warning("未対応のセレクタです: " + selector.getSelectorType() + " " + selector);
			return false;
		}
	}

	private static boolean evaluateCondition(Condition condition, CSSElement ce) {
		switch (condition.getConditionType()) {
		// クラス条件
		case CLASS_CONDITION: {
			String styleClass = condition.getValue();
			return ce.isStyleClass(styleClass);
		}

		// 擬似クラス条件
		case PSEUDO_CLASS_CONDITION: {
			String pseudoClass = condition.getValue();
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
		case ID_CONDITION: {
			String id = condition.getValue();
			return id.equalsIgnoreCase(ce.id);
		}

		// 属性条件
		case ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String name = attrCondition.getLocalName();
			if (attrCondition.getValue() != null) {
				String value = attrCondition.getValue();
				return value.equalsIgnoreCase(ce.atts.getValue(name));
			}
			return ce.atts.getValue(name) != null;
		}

		// スペース区切り属性値条件
		case ONE_OF_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String name = attrCondition.getLocalName();
			String value = attrCondition.getValue();
			String values = ce.atts.getValue(name);
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
		case BEGIN_HYPHEN_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String name = attrCondition.getLocalName();
			String value = attrCondition.getValue();
			String lang = ce.atts.getValue(name);
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
		case LANG_CONDITION: {
			String value = condition.getValue();
			if (ce.lang == null) {
				return false;
			}
			String lang = ce.lang.getLanguage();
			return lang.equalsIgnoreCase(value);
		}

		// 前方一致・後方一致・部分一致属性値条件
		case PREFIX_ATTRIBUTE_CONDITION:
		case SUFFIX_ATTRIBUTE_CONDITION:
		case SUBSTRING_ATTRIBUTE_CONDITION: {
			if (ce.atts == null) {
				return false;
			}
			AttributeCondition attrCondition = (AttributeCondition) condition;
			String value = attrCondition.getValue();
			String attr = ce.atts.getValue(attrCondition.getLocalName());
			if (attr == null || value == null || value.isEmpty()) {
				return false;
			}
			attr = attr.toLowerCase();
			value = value.toLowerCase();
			switch (condition.getConditionType()) {
			case PREFIX_ATTRIBUTE_CONDITION:
				return attr.startsWith(value);
			case SUFFIX_ATTRIBUTE_CONDITION:
				return attr.endsWith(value);
			default:
				return attr.contains(value);
			}
		}

		// :not擬似クラス条件
		case NOT_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (selector instanceof SimpleSelector) {
					if (evaluateSimpleSelector((SimpleSelector) selector, ce)) {
						return false;
					}
				} else {
					LOG.warning(":not内の複合セレクタは未対応です: " + condition);
					return false;
				}
			}
			return true;
		}

		// :is/:where擬似クラス条件
		case IS_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (selector instanceof SimpleSelector) {
					if (evaluateSimpleSelector((SimpleSelector) selector, ce)) {
						return true;
					}
				} else {
					LOG.warning(":is/:where内の複合セレクタは未対応です: " + condition);
				}
			}
			return false;
		}

		// 未対応の条件は変換を止めず不一致として扱う
		default:
			LOG.warning("未対応のセレクタ条件です: " + condition.getConditionType() + " " + condition);
			return false;
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
		Specificity a = rule1.getSpecificity();
		Specificity b = rule2.getSpecificity();
		return a.compareTo(b);
	}

}
