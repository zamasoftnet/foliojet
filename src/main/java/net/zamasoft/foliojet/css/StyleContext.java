package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.selector.AttributeCondition;
import net.zamasoft.foliojet.css.selector.CombinatorSelector;
import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.NthCondition;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SelectorListCondition;
import net.zamasoft.foliojet.css.selector.SimpleSelector;
import net.zamasoft.foliojet.xml.vocab.XHTML;

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

	/**
	 * ページに対して適用されるマージンボックスの宣言を返します
	 * (擬似ページの合成順は {@link #nextPage(CSSElement)} と同一)。
	 *
	 * @param page ページ擬似要素
	 * @return ボックス名→宣言(宣言のないボックスは含まれない)
	 */
	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page) {
		final Map<MarginBoxName, Declaration> result = new EnumMap<MarginBoxName, Declaration>(MarginBoxName.class);
		mergeMarginBoxes(result, this.styleSheet.pageMarginBoxes);
		if (page.isPseudoClass(CSSElement.PC_LEFT)) {
			mergeMarginBoxes(result, this.styleSheet.leftPageMarginBoxes);
		}
		if (page.isPseudoClass(CSSElement.PC_RIGHT)) {
			mergeMarginBoxes(result, this.styleSheet.rightPageMarginBoxes);
		}
		if (page.isPseudoClass(CSSElement.PC_FIRST)) {
			mergeMarginBoxes(result, this.styleSheet.firstPageMarginBoxes);
		}
		return result;
	}

	private static void mergeMarginBoxes(Map<MarginBoxName, Declaration> result,
			Map<MarginBoxName, Declaration> boxes) {
		for (Map.Entry<MarginBoxName, Declaration> e : boxes.entrySet()) {
			result.computeIfAbsent(e.getKey(), k -> new Declaration()).merge(e.getValue());
		}
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

		if (this.elementStack.isEmpty()) {
			return declaration;
		}
		// 右端セレクタの索引から候補規則だけを照合する
		final CSSElement top = (CSSElement) this.elementStack.get(this.elementStack.size() - 1);
		final List<List<Rule>> buckets = this.styleSheet.candidateBuckets(top);

		// 結果が確定したもの
		List<Rule> result = null;
		for (List<Rule> bucket : buckets) {
		for (Rule rule : bucket) {
			if (matchesFromPath(rule.getSelector(), this.elementStack)) {
				if (result == null) {
					result = new ArrayList<Rule>();
				}
				result.add(rule);
			}
		}
		}

		if (result == null) {
			return declaration;
		}
		if (declaration == null) {
			declaration = new Declaration();
		}

		// 固有性→文書内の出現順で整列(SPEC CSS2 6.4.1)。
		// 候補はバケット横断で順不同に集まるため、出現順(Rule.order)を明示的に比較する。
		Collections.sort(result, RuleComparator.INSTANCE);

		// 合成
		for (int i = 0; i < result.size(); ++i) {
			Rule rule = (Rule) result.get(i);
			Declaration tempDecl = rule.getDeclaration();
			declaration.merge(tempDecl);
		}
		return declaration;
	}

	/**
	 * pathの末尾要素を起点として、selector(結合子チェーンを含みうる)が
	 * 一致するかを判定します。{@link #merge}のトップレベル規則評価と、
	 * :is()/:where()/:not()の引数(子孫 半角スペース・子 &gt;・隣接兄弟 +・
	 * 一般兄弟 ~ のいずれの結合子も含みうる)の評価の両方から使う共通実装
	 * です(2026-07-19、両者で重複していたロジックを統合)。非再帰:
	 * selectorは右端から左へ、pathは末尾(対象要素)から先頭(最も遠い祖先)
	 * へ反復的に辿るだけです(要素木・選択木いずれも再帰しない)。
	 *
	 * @param selector 評価するセレクタ
	 * @param path     対象要素を末尾とする祖先チェーン(先頭が最も遠い祖先)。
	 *                 隣接・一般兄弟結合子で対象要素がこのpathの外の兄弟へ
	 *                 移っても、兄弟は同じ親を共有するためpathの残りの
	 *                 接頭辞をそのまま祖先として使い続けられる
	 */
	private static boolean matchesFromPath(Selector selector, List<CSSElement> path) {
		boolean first = true;// 最初のセレクタのため、該当する要素が直ちにあらわれなければならない。
		boolean child = false;// 子セレクタのため、擬似要素をのぞいて該当する要素が直ちにあらわれなければならない。
		boolean sibling = false;// 隣接セレクタのため、pathをあがらずに隣の要素に移る
		CSSElement ce = null;
		List<CSSElement> ceView = null;// ceを末尾とする祖先チェーン(:is()/:where()/:not()のネスト評価用)
		NEXT: for (int j = path.size() - 1; j >= 0; --j) {
			if (sibling) {
				sibling = false;
				ceView = withLast(path.subList(0, j), ce);
			} else {
				ce = path.get(j);
				ceView = path.subList(0, j + 1);
			}
			switch (selector.getSelectorType()) {
			// 子セレクタ
			case CHILD_SELECTOR: {
				CombinatorSelector combinator = (CombinatorSelector) selector;
				SimpleSelector simpleSelector = combinator.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView)) {
					selector = combinator.getAncestorSelector();
					child = true;
				} else if (first || (!ce.isPseudoElement() && child)) {
					break NEXT;
				}
			}
				break;

			// 子孫セレクタ
			case DESCENDANT_SELECTOR: {
				CombinatorSelector combinator = (CombinatorSelector) selector;
				SimpleSelector simpleSelector = combinator.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView)) {
					selector = combinator.getAncestorSelector();
					child = simpleSelector.getSelectorType() == SelectorType.PSEUDO_ELEMENT_SELECTOR;
				} else if (first || (!ce.isPseudoElement() && child)) {
					break NEXT;
				}
			}
				break;

			// 隣接セレクタ
			case DIRECT_ADJACENT_SELECTOR: {
				CombinatorSelector combinator = (CombinatorSelector) selector;
				SimpleSelector simpleSelector = combinator.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView)) {
					selector = combinator.getAncestorSelector();
					child = true;
					ce = ce.precedingElement;
					if (ce == null) {
						break NEXT;
					}
					++j;
					sibling = true;
				} else if (first || (!ce.isPseudoElement() && child)) {
					break NEXT;
				}
			}
				break;

			// 一般兄弟セレクタ
			case GENERAL_ADJACENT_SELECTOR: {
				CombinatorSelector combinator = (CombinatorSelector) selector;
				SimpleSelector simpleSelector = combinator.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView)) {
					selector = combinator.getAncestorSelector();
					child = true;
					// 先行する兄弟のいずれかが左側セレクタの右端にマッチする位置まで戻る
					CSSElement sib = ce.precedingElement;
					List<CSSElement> ancestors = path.subList(0, j);
					while (sib != null && !evaluateSimpleSelector(selector.getSimpleSelector(), withLast(ancestors, sib))) {
						sib = sib.precedingElement;
					}
					if (sib == null) {
						break NEXT;
					}
					ce = sib;
					++j;
					sibling = true;
				} else if (first || (!ce.isPseudoElement() && child)) {
					break NEXT;
				}
			}
				break;

			// 単純セレクタ
			default: {
				SimpleSelector simpleSelector = selector.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView)) {
					return true;
				} else if (first || (!ce.isPseudoElement() && child)) {
					break NEXT;
				}
			}
				break;
			}
			first = false;
		}
		return false;
	}

	private static List<CSSElement> withLast(List<CSSElement> ancestors, CSSElement last) {
		List<CSSElement> result = new ArrayList<CSSElement>(ancestors.size() + 1);
		result.addAll(ancestors);
		result.add(last);
		return result;
	}

	private static boolean evaluateSimpleSelector(SimpleSelector selector, List<CSSElement> path) {
		CSSElement ce = path.get(path.size() - 1);
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
				if (!evaluateCondition(condition, path)) {
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

	private static boolean evaluateCondition(Condition condition, List<CSSElement> path) {
		CSSElement ce = path.get(path.size() - 1);
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

		// 方向性条件(:dir())
		case DIR_CONDITION: {
			String value = condition.getValue();
			return ce.dir != null && ce.dir.equalsIgnoreCase(value);
		}

		// An+B条件(:nth-child() / :nth-of-type())
		case NTH_CHILD_CONDITION: {
			NthCondition nth = (NthCondition) condition;
			return nth.matches(siblingPosition(ce, false));
		}
		case NTH_OF_TYPE_CONDITION: {
			NthCondition nth = (NthCondition) condition;
			return nth.matches(siblingPosition(ce, true));
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

		// :not擬似クラス条件(引数は子孫・子・隣接兄弟・一般兄弟いずれの結合子も
		// 対応。pathをそのままmatchesFromPathへ渡すことで、:not()に付随した
		// 要素(=pathの末尾)を起点に祖先方向へ遡って評価できる。2026-07-19、
		// 隣接・一般兄弟結合子のみ対応(evaluateSiblingChain)だった状態から
		// 子孫・子結合子にも対応(matchesFromPathへ統合))
		case NOT_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (matchesFromPath(selector, path)) {
					return false;
				}
			}
			return true;
		}

		// :is擬似クラス条件(詳細度は引数リスト中最大。:where()とはConditionTypeで
		// 区別するが、マッチング判定自体は同一のためcaseをまとめる)
		case IS_CONDITION:
		case WHERE_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (matchesFromPath(selector, path)) {
					return true;
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

	/**
	 * 兄弟内での通し番号(1始まり)を返します。先行兄弟チェーン
	 * (CSSElement.precedingElement)を先頭側へ反復的に辿るだけで求まり
	 * (1P、後続要素は見ない)、再帰は使いません。
	 *
	 * @param ce           対象要素
	 * @param sameTypeOnly true なら同じ要素名の兄弟だけを数える
	 *                     (:nth-of-type() 用)
	 */
	private static int siblingPosition(CSSElement ce, boolean sameTypeOnly) {
		int position = 1;
		for (CSSElement sibling = ce.precedingElement; sibling != null; sibling = sibling.precedingElement) {
			if (!sameTypeOnly || sameElementType(sibling, ce)) {
				++position;
			}
		}
		return position;
	}

	private static boolean sameElementType(CSSElement a, CSSElement b) {
		if (!java.util.Objects.equals(a.lName, b.lName)) {
			return false;
		}
		return java.util.Objects.equals(a.uri, b.uri);
	}
}

/**
 * 規則を固有性の順に整列するための比較子です。
 *
 * @author MIYABE Tatsuhiko
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
	 * cascade origin(USER_AGENT &lt; AUTHOR)の昇順を最優先し、同じoriginの中では
	 * 固有性の昇順、固有性が等しい場合はスタイルシート内の出現順で比較します
	 * (CSS Cascading and Inheritance: origin → specificity → order)。
	 */
	public int compare(Object o1, Object o2) {
		Rule rule1 = (Rule) o1;
		Rule rule2 = (Rule) o2;
		int origin = rule1.getOrigin().compareTo(rule2.getOrigin());
		if (origin != 0) {
			return origin;
		}
		int specificity = rule1.getSpecificity().compareTo(rule2.getSpecificity());
		if (specificity != 0) {
			return specificity;
		}
		return Integer.compare(rule1.getOrder(), rule2.getOrder());
	}

}
