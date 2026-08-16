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
import net.zamasoft.foliojet.css.container.ContainerQuery;
import net.zamasoft.foliojet.ua.ContainerFacts;
import net.zamasoft.foliojet.ua.SelectorFacts;
import net.zamasoft.foliojet.xml.vocab.XHTML;

public class StyleContext {

	private static final Logger LOG = Logger.getLogger(StyleContext.class.getName());

	private static final boolean DEBUG = false;

	/** 上位の要素のリスト。 */
	private final List<CSSElement> elementStack = new ArrayList<CSSElement>();

	public final CSSStyleSheet styleSheet;

	/**
	 * STRUCTURE_SCANパスが収集した、要素の終了時点まで確定しない
	 * 疑似クラス(:has()・:last-child系)の判定結果。docs/PLAN.md
	 * 「2パス制御モード」参照。
	 */
	private final SelectorFacts selectorFacts;

	/**
	 * {@code @container}クエリのための要素の事実(2026-08-15段4)。
	 * {@link #selectorFacts}と同じくSTRUCTURE_SCAN開始時にリセットされ、
	 * 複数パスにまたがって積み上げる({@link ContainerFacts}参照)。
	 */
	private final ContainerFacts containerFacts;

	public StyleContext(CSSStyleSheet styleSheet, SelectorFacts selectorFacts, ContainerFacts containerFacts) {
		this.styleSheet = styleSheet;
		this.selectorFacts = selectorFacts;
		this.containerFacts = containerFacts;
	}

	public StyleContext copy(int up) {
		StyleContext styleContext = new StyleContext(this.styleSheet, this.selectorFacts, this.containerFacts);
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
	 * ページの開始に対して、対応するスタイル宣言を返します(無名ページ)。
	 */
	public Declaration nextPage(CSSElement page) {
		return this.nextPage(page, null);
	}

	/**
	 * ページの開始に対して、対応するスタイル宣言を返します(名前付き
	 * ページN1a——特異性(f,g,h)昇順→出現順にmergeし後勝ち)。
	 *
	 * @param page     ページ擬似要素(first/left/rightの擬似クラス)
	 * @param pageName 現在のページ名(null=無名)
	 */
	public Declaration nextPage(CSSElement page, String pageName) {
		final Declaration result = new Declaration();
		for (final PageRule rule : this.matchingPageRules(page, pageName)) {
			result.merge(rule.declaration);
		}
		return result;
	}

	/**
	 * ページに対して適用されるマージンボックスの宣言を返します
	 * (合成順は {@link #nextPage(CSSElement, String)} と同一)。
	 */
	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page) {
		return this.pageMarginBoxes(page, null);
	}

	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page, String pageName) {
		final Map<MarginBoxName, Declaration> result = new EnumMap<MarginBoxName, Declaration>(MarginBoxName.class);
		for (final PageRule rule : this.matchingPageRules(page, pageName)) {
			for (Map.Entry<MarginBoxName, Declaration> e : rule.marginBoxes.entrySet()) {
				result.computeIfAbsent(e.getKey(), k -> new Declaration()).merge(e.getValue());
			}
		}
		return result;
	}

	/** 適合規則を特異性昇順(同値は出現順)で返します。 */
	private List<PageRule> matchingPageRules(CSSElement page, String pageName) {
		byte pseudo = 0;
		if (page.isPseudoClass(CSSElement.PC_FIRST)) {
			pseudo |= PageRule.PSEUDO_FIRST;
		}
		if (page.isPseudoClass(CSSElement.PC_LEFT)) {
			pseudo |= PageRule.PSEUDO_LEFT;
		}
		if (page.isPseudoClass(CSSElement.PC_RIGHT)) {
			pseudo |= PageRule.PSEUDO_RIGHT;
		}
		final List<PageRule> matched = new ArrayList<PageRule>();
		for (final PageRule rule : this.styleSheet.pageRules) {
			if (rule.matches(pageName, pseudo)) {
				matched.add(rule);
			}
		}
		// 安定ソート=同特異性は出現順を保つ
		matched.sort(java.util.Comparator.comparingInt(PageRule::specificity));
		return matched;
	}

	/**
	 * 現在の要素に対応するスタイル宣言と与えられたスタイル宣言をマージします。
	 *
	 * @return
	 */
	public Declaration merge(Declaration declaration) {
		return this.merge(declaration, null, null);
	}

	/**
	 * 合致した規則を合成します。
	 *
	 * <p>
	 * {@code userAgentOut} を渡すと、<b>UA既定スタイルシート
	 * ({@code html-ua.css})の規則だけをそこへ分離</b>し、戻り値には著者側
	 * だけを合成します。HTMLの属性由来の既定値(presentational hints)を
	 * 両者の<b>間</b>に挟むためです——HTML仕様では属性由来の指定はUA既定より
	 * 強く、著者スタイルシートより弱い。1つに合成すると{@code html-ua.css}が
	 * {@code cellspacing="0"}のような属性を上書きしてしまう(2026-08-01の
	 * HTMLStyle移行で実際に起きた。表の行が2pxずつ高くなり、基準画像が
	 * 3件食い違った——原因の特定は2026-08-03)。
	 * </p>
	 *
	 * @param userAgentOut 非nullなら、UA出所の規則をその要素0へ分離する。
	 */
	public Declaration merge(Declaration declaration, Declaration[] userAgentOut) {
		return this.merge(declaration, userAgentOut, null);
	}

	/**
	 * @param importantOut 非nullなら、レイヤーを使った規則があるとき
	 *                     important宣言を反転順で合成したものをその要素0へ置く
	 */
	public Declaration merge(Declaration declaration, Declaration[] userAgentOut, Declaration[] importantOut) {
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

		// :has()の判定を積み上げる(要素の終了時点まで真偽が確定しないため、
		// 複数パスにまたがってSelectorFactsへ記録する。docs/PLAN.md
		// 「2パス制御モード」参照)
		if (this.selectorFacts != null) {
			recordHasFacts(this.styleSheet.getHasConditions(), this.elementStack, this.selectorFacts);
		}

		final List<List<Rule>> buckets = this.styleSheet.candidateBuckets(top);

		// 結果が確定したもの
		List<Rule> result = null;
		for (List<Rule> bucket : buckets) {
		for (Rule rule : bucket) {
			if (matchesFromPath(rule.getSelector(), this.elementStack, this.selectorFacts)
					&& containerQueryMatches(rule.getContainerQuery(), this.elementStack, this.containerFacts)) {
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
			if (userAgentOut != null && rule.getOrigin() == Origin.USER_AGENT) {
				if (userAgentOut[0] == null) {
					userAgentOut[0] = new Declaration();
				}
				userAgentOut[0].merge(tempDecl);
				continue;
			}
			declaration.merge(tempDecl);
		}
		// **@layerと!importantの併用**: importantどうしはレイヤー順が反転する
		// (CSS Cascade 5)。レイヤーを使った規則が2つ以上あるときだけ、
		// important宣言を反転順でもう一度重ねる材料を作る(2026-08-03)
		if (importantOut != null && usesLayers(result)) {
			final List<Rule> importantRules = new ArrayList<Rule>(result);
			Collections.sort(importantRules, RuleComparator.IMPORTANT);
			final Declaration importantDecl = new Declaration();
			for (int i = 0; i < importantRules.size(); ++i) {
				importantDecl.merge(importantRules.get(i).getDeclaration());
			}
			importantOut[0] = importantDecl;
		}
		return declaration;
	}

	/** レイヤーに属する規則が含まれるか(反転の合成をする価値があるか)。 */
	private static boolean usesLayers(final List<Rule> rules) {
		for (int i = 0; i < rules.size(); ++i) {
			if (rules.get(i).getLayer() != Rule.NO_LAYER) {
				return true;
			}
		}
		return false;
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
	 * @param facts    STRUCTURE_SCANが収集した先読みが要る疑似クラスの
	 *                 判定結果(:has()・:last-child系)。無ければnull可
	 */
	private static boolean matchesFromPath(Selector selector, List<CSSElement> path, SelectorFacts facts) {
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
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
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
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
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
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
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
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
					selector = combinator.getAncestorSelector();
					child = true;
					// 先行する兄弟のいずれかが左側セレクタの右端にマッチする位置まで戻る
					CSSElement sib = ce.precedingElement;
					List<CSSElement> ancestors = path.subList(0, j);
					while (sib != null
							&& !evaluateSimpleSelector(selector.getSimpleSelector(), withLast(ancestors, sib), facts)) {
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
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
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

	/**
	 * :has()の判定を積み上げます。現在の要素(pathの末尾)を新たな候補として、
	 * その全ての祖先(pathの末尾を除く各要素)についてチェックし、真になった
	 * ものをfactsへ記録します。既に真と分かっている祖先は再チェックしません
	 * (:has()は「部分木内に存在するか」の判定のため、一度真になれば以降
	 * 不変)。文書全体を通じて呼び続けることで、要素の終了時点で確定する
	 * :has()の真偽を段階的に積み上げます(単一パスでは完結しないため、
	 * 複数パスにまたがって呼ぶ前提。docs/PLAN.md「2パス制御モード」参照)。
	 *
	 * @param hasConditions 文書中の全:has()条件(文書順)
	 * @param path          祖先チェーン(末尾が現在の要素)
	 * @param facts         書き込み先
	 */
	private static void recordHasFacts(List<Condition> hasConditions, List<CSSElement> path, SelectorFacts facts) {
		if (hasConditions.isEmpty() || path.size() < 2) {
			// 祖先が無ければ:has()の対象(subject)になり得ない
			return;
		}
		for (Condition hasCondition : hasConditions) {
			for (Selector relativeSelector : ((SelectorListCondition) hasCondition).getSelectors()) {
				for (int sIndex = path.size() - 2; sIndex >= 0; --sIndex) {
					CSSElement subject = path.get(sIndex);
					if (facts.isHasMatch(subject.elementKey, hasCondition)) {
						continue;
					}
					List<CSSElement> subPath = path.subList(sIndex + 1, path.size());
					if (matchesFromPath(relativeSelector, subPath, facts)) {
						facts.setHasMatch(subject.elementKey, hasCondition);
					}
				}
			}
		}
	}

	/**
	 * {@code @container}の一致判定です(2026-08-15段4——
	 * docs/history/2026-08-15-container-queries-design.md §2/§6)。
	 * {@code query}が{@code null}(この規則が{@code @container}の内側に
	 * 無い)なら常に一致。そうでなければ、pathの末尾(現在の要素)の
	 * <b>祖先</b>(末尾自身は対象外——コンテナは自分自身になれない)を
	 * 近い順に辿り、名前が合う最初のクエリコンテナだけを使う
	 * (仕様どおり、複数祖先を跨いだ合成はしない)。該当コンテナが
	 * 無ければ不一致。
	 *
	 * <p>
	 * 実測寸法は{@link ContainerFacts}が前パスまでに記録した値
	 * ({@code NaN}なら未確定=常に不一致、設計§2「パス1は全クエリ偽」)。
	 * </p>
	 */
	private static boolean containerQueryMatches(ContainerQuery query, List<CSSElement> path,
			ContainerFacts facts) {
		if (query == null) {
			return true;
		}
		if (facts == null) {
			return false;
		}
		final String name = query.getName();
		for (int i = path.size() - 2; i >= 0; --i) {
			final CSSElement ancestor = path.get(i);
			if (ancestor.elementKey < 0 || !facts.isInlineSizeContainer(ancestor.elementKey)) {
				continue;
			}
			if (name != null && !containsName(facts.getContainerNames(ancestor.elementKey), name)) {
				continue;
			}
			final double inlineSize = facts.getInlineSize(ancestor.elementKey);
			return !Double.isNaN(inlineSize) && query.getCondition().evaluate(inlineSize);
		}
		return false;
	}

	private static boolean containsName(String[] names, String name) {
		for (final String candidate : names) {
			if (candidate.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static List<CSSElement> withLast(List<CSSElement> ancestors, CSSElement last) {
		List<CSSElement> result = new ArrayList<CSSElement>(ancestors.size() + 1);
		result.addAll(ancestors);
		result.add(last);
		return result;
	}

	private static boolean evaluateSimpleSelector(SimpleSelector selector, List<CSSElement> path, SelectorFacts facts) {
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
				if (!evaluateCondition(condition, path, facts)) {
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

	private static boolean evaluateCondition(Condition condition, List<CSSElement> path, SelectorFacts facts) {
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
			case 'S':
			case 's':
				if (pseudoClass.equalsIgnoreCase("scope")) {
					// 2026-07-21: @scopeは未対応のため、:scopeは常に
					// :root相当として扱う(CSS Selectors 4「スタイル
					// シート内でスコープ根が他に指定されなければ
					// ルート要素がデフォルト」の単純化)。@scopeを
					// 実装する際は、この単純化をelementStack上の
					// スコープ根追跡へ置き換える必要がある。
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

		// 後方基準の疑似クラス(STRUCTURE_SCANが収集したSelectorFactsを
		// 参照。facts自体が無い、またはその要素の走査結果が無い
		// (STRUCTURE_SCANが実行されていない=processing.pass-count<2)
		// 場合は未対応セレクタと同じく不一致として扱う)
		case LAST_CHILD_CONDITION:
			return facts != null && facts.isLastChild(ce.elementKey);
		case ONLY_CHILD_CONDITION:
			// :first-child(既存、開始時点で確定済みのPC_FIRST_CHILD)と
			// :last-child(STRUCTURE_SCAN)の両方を満たす要素
			return ce.isPseudoClass(CSSElement.PC_FIRST_CHILD) && facts != null && facts.isLastChild(ce.elementKey);
		case EMPTY_CONDITION:
			return facts != null && facts.isEmpty(ce.elementKey);
		case LAST_OF_TYPE_CONDITION:
			return facts != null && facts.isLastOfType(ce.elementKey);
		case ONLY_OF_TYPE_CONDITION:
			// :first-of-type相当(既存のsiblingPosition(ce,true)==1)と
			// :last-of-type(STRUCTURE_SCAN)の両方を満たす要素
			return siblingPosition(ce, true) == 1 && facts != null && facts.isLastOfType(ce.elementKey);
		case NTH_LAST_CHILD_CONDITION: {
			if (facts == null) {
				return false;
			}
			int position = facts.getPositionFromEnd(ce.elementKey);
			return position >= 1 && ((NthCondition) condition).matches(position);
		}
		case NTH_LAST_OF_TYPE_CONDITION: {
			if (facts == null) {
				return false;
			}
			int position = facts.getTypePositionFromEnd(ce.elementKey);
			return position >= 1 && ((NthCondition) condition).matches(position);
		}

		// :has()(引数は複数可、OR。StyleContext.mergeが呼ぶrecordHasFactsが
		// 複数パスにまたがって積み上げた結果を参照するだけ)
		case HAS_CONDITION:
			return facts != null && facts.isHasMatch(ce.elementKey, condition);

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
			// **大文字小文字は区別する**(2026-08-05に修正)。CSS Selectors では
			// 属性セレクタの値比較は既定で case-sensitive で、区別しないのは
			// `i` フラグを付けたときだけ。ここは無条件に両辺を小文字化しており、
			// `li[type^="a"]`(小文字ローマ数字/英字)と `li[type^="A"]` が
			// **どちらも同じ要素に当たって後勝ち**していた——`<li type="a">` が
			// `H.`、`<li type="i">` が `X.` と大文字で出る。
			//
			// **この差は基準画像で0.027%しかなく、imageTest の許容2%に
			// 隠れていた。** 基準を作り直す前に新旧を並べて目視して見つけた。
			//
			// なお `=` と `~=` は HTML の歴史的な「値を大文字小文字を無視して
			// 比較する属性」の一覧(type/align/valign等)に合わせて
			// 区別しないまま残している。前方・後方・部分一致にはその一覧が
			// 適用されないので、ここだけが標準どおりになる。
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
				if (matchesFromPath(selector, path, facts)) {
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
				if (matchesFromPath(selector, path, facts)) {
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
	 * cascade origin(USER_AGENT &lt; AUTHOR)の昇順を最優先し、次に
	 * cascadeレイヤーの出現順(レイヤーなし{@link Rule#NO_LAYER}が常に
	 * 最優先、レイヤーどうしでは後から現れたレイヤーが優先)、
	 * 同じレイヤーの中では固有性の昇順、固有性が等しい場合はスタイル
	 * シート内の出現順で比較します(CSS Cascading and Inheritance:
	 * origin/importance → layer → specificity → order。2026-07-21、
	 * CSS Cascade Layers対応でlayerの段を追加。importantによる
	 * layer優先順位反転は未対応、{@link Rule#getLayer()}参照)。
	 */
	public int compare(Object o1, Object o2) {
		return compare((Rule) o1, (Rule) o2, false);
	}

	/**
	 * {@code important}がtrueなら<b>レイヤーの順序を反転</b>して比較します
	 * (2026-08-03新設)。
	 *
	 * <p>
	 * CSS Cascade 5では、{@code !important}の宣言どうしの強さは
	 * <b>通常と逆</b>になる——レイヤーに属さない宣言が<b>最弱</b>で、
	 * <b>先に現れたレイヤー</b>ほど強い。Chrome・Firefox・Safariとも
	 * 仕様どおり(2026-08-03、独立相談で確認)。
	 * </p>
	 */
	static int compare(final Rule rule1, final Rule rule2, final boolean important) {
		int origin = rule1.getOrigin().compareTo(rule2.getOrigin());
		if (origin != 0) {
			return origin;
		}
		int layer = Integer.compare(rule1.getLayer(), rule2.getLayer());
		if (layer != 0) {
			return important ? -layer : layer;
		}
		int specificity = rule1.getSpecificity().compareTo(rule2.getSpecificity());
		if (specificity != 0) {
			return specificity;
		}
		return Integer.compare(rule1.getOrder(), rule2.getOrder());
	}

	/** {@code !important}の宣言どうしの比較子(レイヤー順が反転する)。 */
	static final Comparator<Object> IMPORTANT = new Comparator<Object>() {
		public int compare(final Object o1, final Object o2) {
			return RuleComparator.compare((Rule) o1, (Rule) o2, true);
		}
	};

}
