package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.foliojet.css.selector.CombinatorSelector;
import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.Condition.ConditionType;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SelectorListCondition;
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

	/**
	 * 文書中に現れる全ての{@code :has()}条件(文書順)。要素の終了時点まで
	 * 真偽が確定しないため、{@code StyleContext}が要素ごとに祖先チェーンを
	 * 遡って判定を積み上げるのに使う(docs/PLAN.md「2パス制御モード」参照)。
	 */
	private final List<Condition> hasConditions = new ArrayList<Condition>();

	/**
	 * 構造化された{@code @page}規則の列です(名前付きページN1a、
	 * 2026-07-31——旧4バケット(無名/first/left/right)を置き換え。
	 * 適用はStyleContextが特異性(f,g,h)昇順→出現順のmergeで行う)。
	 */
	final List<PageRule> pageRules = new ArrayList<PageRule>();

	private record PageContent(long order, net.zamasoft.foliojet.css.style.running.RunningTemplate template) {
	}

	/** 同名の旧スナップショットを解放し、最後の指定順で現行候補だけを保持します。 */
	private final Map<String, PageContent> pageContents = new LinkedHashMap<>();
	private long pageContentOrder;

	public void addPageContent(final net.zamasoft.foliojet.css.style.running.RunningTemplate template) {
		this.pageContents.remove(template.name());
		this.pageContents.put(template.name(), new PageContent(++this.pageContentOrder, template));
	}

	public List<net.zamasoft.foliojet.css.style.running.RunningTemplate> getPageContents() {
		return this.pageContents.values().stream().map(PageContent::template).toList();
	}

	/** 読み手ごとの登録位置から増分を渡します。共有stylesheetを消費しません。 */
	public long installPageContents(final long after,
			final java.util.function.Consumer<net.zamasoft.foliojet.css.style.running.RunningTemplate> install) {
		if (after != this.pageContentOrder) {
			for (final PageContent content : this.pageContents.values()) {
				if (content.order() > after) {
					install.accept(content.template());
				}
			}
		}
		return this.pageContentOrder;
	}

	/**
	 * cascadeレイヤーの出現順を登録する台帳です(2026-07-21新設、CSS
	 * Cascade Layers)。名前つきレイヤーは同じ名前が再度現れても最初の
	 * 出現順を保つ(spec: 同名レイヤーへの追記であり、順位は変わらない)。
	 */
	private final Map<String, Integer> namedLayerOrder = new HashMap<String, Integer>();
	private int nextLayerOrder = 0;

	/**
	 * 名前つきレイヤーを登録し、その優先順位番号を返します(初出時に
	 * 確定、以後同名で呼ばれても同じ番号を返す)。ネストしたレイヤー
	 * (例: {@code @layer a { @layer b { ... } }})は呼び出し側が
	 * ドット結合した完全名(例: {@code "a.b"})を渡すことで、独立した
	 * 名前として扱う。
	 */
	public int registerNamedLayer(String fullName) {
		Integer existing = this.namedLayerOrder.get(fullName);
		if (existing != null) {
			return existing;
		}
		int order = this.nextLayerOrder++;
		this.namedLayerOrder.put(fullName, order);
		return order;
	}

	/**
	 * 匿名レイヤー({@code @layer { ... }}、名前なし)用に、呼ばれるたびに
	 * 新しい優先順位番号を発行します(spec: 匿名レイヤーは常に一意)。
	 */
	public int registerAnonymousLayer() {
		return this.nextLayerOrder++;
	}

	/**
	 * ルールを追加します(cascade originはAUTHOR、レイヤーに属さない)。
	 *
	 * @param selectors
	 * @param declaration
	 */
	public void addRule(List<Selector> selectors, Declaration declaration) {
		this.addRule(selectors, declaration, Origin.AUTHOR, Rule.NO_LAYER);
	}

	/**
	 * ルールを追加します(レイヤーに属さない)。
	 *
	 * @param selectors
	 * @param declaration
	 * @param origin cascade origin
	 */
	public void addRule(List<Selector> selectors, Declaration declaration, Origin origin) {
		this.addRule(selectors, declaration, origin, Rule.NO_LAYER);
	}

	/**
	 * ルールを追加します。
	 *
	 * @param selectors
	 * @param declaration
	 * @param origin cascade origin
	 * @param layer {@link #registerNamedLayer}/{@link #registerAnonymousLayer}
	 *              が返した優先順位番号(レイヤーに属さないなら
	 *              {@link Rule#NO_LAYER})
	 */
	public void addRule(List<Selector> selectors, Declaration declaration, Origin origin, int layer) {
		this.addRule(selectors, declaration, origin, layer, null);
	}

	/**
	 * ルールを追加します({@code @container}の内側の規則、2026-08-15段4)。
	 *
	 * @param containerQuery この規則を包む{@code @container}(無ければnull)
	 */
	public void addRule(List<Selector> selectors, Declaration declaration, Origin origin, int layer,
			net.zamasoft.foliojet.css.container.ContainerQuery containerQuery) {
		if (declaration == null) {
			return;
		}
		for (Selector selector : selectors) {// ループすることに注意！
			Rule rule = new Rule(selector, declaration, this.rules.size(), origin, layer, containerQuery);
			this.rules.add(rule);
			this.index(rule);
			collectHasConditions(selector, this.hasConditions);
		}
	}

	/**
	 * selectorが持つ全ての{@code :has()}条件を(結合子チェーン・
	 * {@code :not()}/{@code :is()}/{@code :where()}内にネストしたものも
	 * 含めて)outへ集めます。セレクタのAST(構文由来・有限)を辿るだけの
	 * 非有界でない反復深さのため、通常の再帰で実装する(要素木を辿る
	 * ものではなく、HTML入力由来の非有界な深さとは性質が異なる——
	 * Tokens.fromExpression等、既存コードの同種の判断と同じ)。
	 */
	private static void collectHasConditions(Selector selector, List<Condition> out) {
		SimpleSelector simple = selector.getSimpleSelector();
		if (simple.getSelectorType() == SelectorType.ELEMENT_NODE_SELECTOR) {
			for (Condition condition : ((ElementSelector) simple).getConditions()) {
				collectHasConditionsFromCondition(condition, out);
			}
		}
		if (selector instanceof CombinatorSelector combinator) {
			collectHasConditions(combinator.getAncestorSelector(), out);
		}
	}

	private static void collectHasConditionsFromCondition(Condition condition, List<Condition> out) {
		switch (condition.getConditionType()) {
		case HAS_CONDITION:
			out.add(condition);
			break;
		case NOT_CONDITION:
		case IS_CONDITION:
		case WHERE_CONDITION:
			for (Selector nested : ((SelectorListCondition) condition).getSelectors()) {
				collectHasConditions(nested, out);
			}
			break;
		default:
			break;
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
	 * 文書中に現れる全ての{@code :has()}条件(文書順・変更不可)を返します。
	 * ネストした{@code :has()}(:has()自身の引数内)は含めない(初期実装の
	 * 制限、docs/PLAN.md参照)。
	 */
	public List<Condition> getHasConditions() {
		return Collections.unmodifiableList(this.hasConditions);
	}

	/**
	 * 構造化された{@code @page}規則を追加します(名前付きページN1a)。
	 * 規則ごとに1件——宣言とマージンボックスを同じ特異性・出現順で持つ。
	 *
	 * @param name        ページ名(null=無名)
	 * @param pseudoMask  要求する擬似ページ({@link PageRule#PSEUDO_FIRST}等)
	 * @param declaration 通常宣言(null可)
	 * @return 追加した規則(呼び出し側がマージンボックスを詰める)
	 */
	public PageRule addPageRule(String name, byte pseudoMask, Declaration declaration) {
		final PageRule rule = new PageRule(name, pseudoMask, declaration, this.pageRules.size());
		this.pageRules.add(rule);
		return rule;
	}

	/** 規則へマージンボックス宣言を追加します。 */
	public void addPageRuleMarginBox(PageRule rule, MarginBoxName box, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		rule.marginBoxes.computeIfAbsent(box, k -> new Declaration()).merge(declaration);
	}

}
