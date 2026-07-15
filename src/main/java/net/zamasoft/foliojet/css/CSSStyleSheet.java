package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SimpleSelector;

/**
 * スタイルシートは、与えられた要素に対して適用される宣言を返します。
 * <p>
 * このクラスは、SAXイベントとして送られた文書に対して段階的にスタイルを適用することを意図しています。
 * 要素は必ずしもSAXイベントの要素とは一致せず、CSSの一部の擬似クラスにあるような文書の構成要素にも対応させることができます。
 * startElementに対応するendElementは必ず矛盾なく呼ばれる必要があります。
 * </p>
 * <p>
 * 規則は右端の単純セレクタ(ID・クラス・要素名・擬似要素)で索引化され、
 * 要素ごとの照合は候補バケットに対してのみ行われます。
 * 構築(addRule/addPage)後は不変であり、複数スレッドから共有できます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSStyleSheet {
	/** 全規則(文書順)。 */
	final List<Rule> rules = new ArrayList<Rule>();

	/*
	 * 右端単純セレクタによる索引。各規則はいずれか1つのバケットにだけ入る。
	 * 索引は「マッチし得る規則を漏らさない」保守的なスーパーセットであり、
	 * 実際のマッチ判定はStyleContextが行う。
	 */
	private final Map<String, List<Rule>> idToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> classToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> nameToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> pseudoElementToRules = new HashMap<String, List<Rule>>();
	private final List<Rule> universalRules = new ArrayList<Rule>();

	/** ページの宣言。 */
	Declaration page, firstPage, leftPage, rightPage;

	/** ページごとに生成される内容 */
	final List<PageContent> pageContents = new ArrayList<PageContent>();

	public static class PageContent {
		public final String name, pseudoPage;
		public final Declaration decleration;

		PageContent(String name, String pseudoPage, Declaration decleration) {
			this.name = name;
			this.pseudoPage = pseudoPage;
			this.decleration = decleration;
		}
	}

	/**
	 * ルールを追加します。
	 *
	 * @param selectors
	 * @param declaration
	 */
	public void addRule(List<Selector> selectors, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		for (Selector selector : selectors) {// ループすることに注意！
			Rule rule = new Rule(selector, declaration, this.rules.size());
			this.rules.add(rule);
			this.index(rule);
		}
	}

	private void index(Rule rule) {
		SimpleSelector simple = rule.getSelector().getSimpleSelector();
		if (simple.getSelectorType() == SelectorType.PSEUDO_ELEMENT_SELECTOR) {
			String name = ((PseudoElementSelector) simple).getLocalName();
			this.bucket(this.pseudoElementToRules, name).add(rule);
			return;
		}
		ElementSelector element = (ElementSelector) simple;
		// ID > クラス > 要素名 > 全称 の順で、最も選択的なバケットに入れる
		for (Condition condition : element.getConditions()) {
			if (condition.getConditionType() == Condition.ConditionType.ID_CONDITION) {
				this.bucket(this.idToRules, condition.getValue()).add(rule);
				return;
			}
		}
		for (Condition condition : element.getConditions()) {
			if (condition.getConditionType() == Condition.ConditionType.CLASS_CONDITION) {
				this.bucket(this.classToRules, condition.getValue()).add(rule);
				return;
			}
		}
		if (element.getLocalName() != null) {
			this.bucket(this.nameToRules, element.getLocalName()).add(rule);
			return;
		}
		this.universalRules.add(rule);
	}

	private List<Rule> bucket(Map<String, List<Rule>> map, String key) {
		// ID・クラス・要素名の照合は大文字小文字を無視するため、キーは小文字に正規化する
		key = key.toLowerCase(Locale.ROOT);
		List<Rule> list = map.get(key);
		if (list == null) {
			list = new ArrayList<Rule>();
			map.put(key, list);
		}
		return list;
	}

	private List<Rule> lookup(Map<String, List<Rule>> map, String key) {
		if (key == null || map.isEmpty()) {
			return null;
		}
		return map.get(key.toLowerCase(Locale.ROOT));
	}

	/**
	 * 右端の単純セレクタが要素にマッチし得る規則のバケット群を返します。
	 * 返される規則は候補(スーパーセット)であり、実際のマッチ判定は呼び出し側が行います。
	 *
	 * @param ce 照合対象の要素(要素スタックの先頭)
	 * @return 候補規則のリストの集まり
	 */
	List<List<Rule>> candidateBuckets(CSSElement ce) {
		List<List<Rule>> buckets = new ArrayList<List<Rule>>(4);
		if (ce.isPseudoElement()) {
			// 擬似要素には擬似要素セレクタの規則しかマッチしない
			List<Rule> list = this.lookup(this.pseudoElementToRules, ce.lName);
			if (list != null) {
				buckets.add(list);
			}
			return buckets;
		}
		List<Rule> list = this.lookup(this.idToRules, ce.id);
		if (list != null) {
			buckets.add(list);
		}
		if (ce.styleClasses != null) {
			for (String styleClass : ce.styleClasses) {
				list = this.lookup(this.classToRules, styleClass);
				if (list != null) {
					buckets.add(list);
				}
			}
		}
		list = this.lookup(this.nameToRules, ce.lName);
		if (list != null) {
			buckets.add(list);
		}
		if (!this.universalRules.isEmpty()) {
			buckets.add(this.universalRules);
		}
		return buckets;
	}

	/**
	 * 全規則(文書順・変更不可)を返します。
	 *
	 * @return
	 */
	public List<Rule> getRules() {
		return Collections.unmodifiableList(this.rules);
	}

	/**
	 * ページ宣言を追加します。
	 *
	 * @param pseudoPage
	 * @param declaration
	 */
	public void addPage(String pseudoPage, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		if (pseudoPage == null) {
			if (this.page == null) {
				this.page = new Declaration();
			}
			this.page.merge(declaration);
		} else if (pseudoPage.equals("first")) {
			if (this.firstPage == null) {
				this.firstPage = new Declaration();
			}
			this.firstPage.merge(declaration);
		} else if (pseudoPage.equals("left")) {
			if (this.leftPage == null) {
				this.leftPage = new Declaration();
			}
			this.leftPage.merge(declaration);
		} else if (pseudoPage.equals("right")) {
			if (this.rightPage == null) {
				this.rightPage = new Declaration();
			}
			this.rightPage.merge(declaration);
		}
	}

	/**
	 * ページ宣言を追加します。
	 *
	 * @param pseudoPage
	 * @param declaration
	 */
	public void addPageContent(String name, String pseudoPage, Declaration declaration) {
		this.pageContents.add(new PageContent(name, pseudoPage, declaration));
	}
}
