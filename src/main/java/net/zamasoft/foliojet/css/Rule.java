package net.zamasoft.foliojet.css;


import net.zamasoft.foliojet.css.container.ContainerQuery;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Specificity;

/**
 * CSS規則です。 規則は、選択子とそれに対応するスタイル宣言のペアです。
 * スタイルシート構築後は不変であり、複数スレッドから共有できます。
 *
 * @author MIYABE Tatsuhiko
 */
public class Rule {
	/**
	 * cascadeレイヤーに属さない規則の{@link #layer}値です(2026-07-21新設、
	 * CSS Cascade Layers)。どのレイヤー(出現順が最も新しいもの含む)にも
	 * 優先する——{@code Integer.MAX_VALUE}を使うことで、通常の昇順比較
	 * (レイヤーなし=最優先)がそのまま成立する。
	 */
	public static final int NO_LAYER = Integer.MAX_VALUE;

	private final Selector selector;

	private final Declaration declaration;

	/** スタイルシート内の出現順。固有性が等しい規則の優先順位を決める。 */
	private final int order;

	/** cascade origin。固有性・出現順に優先する(USER_AGENT は常に AUTHOR に劣後)。 */
	private final Origin origin;

	/**
	 * cascadeレイヤーの優先順位番号です(2026-07-21新設、CSS Cascade
	 * Layers)。{@link CSSStyleSheet#registerNamedLayer}/
	 * {@link CSSStyleSheet#registerAnonymousLayer}が発行する、スタイル
	 * シート内でレイヤーが最初に現れた順の通し番号。{@link #NO_LAYER}
	 * (レイヤーに属さない規則)が常に最優先。origin・specificity・
	 * 出現順の間、origin直後に比較される(CSS Cascading and
	 * Inheritance: origin/importance → layer → specificity → order)。
	 * {@code !important}によるレイヤー優先順位の反転(importantな宣言は
	 * <b>先に現れたレイヤー</b>が後のレイヤーに優先し、レイヤー外が最弱)は
	 * <b>2026-08-03に対応した</b>——通常順で一度カスケードを適用したあと、
	 * important宣言だけを反転順でもう一度重ねる
	 * ({@link Declaration#applyImportantProperties}、
	 * {@code RuleComparator.IMPORTANT})。importantどうしは後勝ちなので、
	 * 最も強いものが最後に載る。レイヤーを使った規則が無い文書では
	 * 反転の合成そのものを行わない(費用ゼロ)。
	 * なお<b>UA由来のimportantが著者のimportantより強い</b>という
	 * origin側の反転は未対応(印刷用途で影響する場面が無いため)。
	 */
	private final int layer;

	private transient Specificity specificity = null;

	/**
	 * この規則を包む{@code @container}の名前・条件です(2026-08-15段4——
	 * docs/history/2026-08-15-container-queries-design.md)。{@code @container}
	 * の内側で無ければ{@code null}(通常の規則はコンテナ条件を持たない)。
	 */
	private final ContainerQuery containerQuery;

	public Rule(Selector selector, Declaration declaration, int order, Origin origin) {
		this(selector, declaration, order, origin, NO_LAYER, null);
	}

	public Rule(Selector selector, Declaration declaration, int order, Origin origin, int layer) {
		this(selector, declaration, order, origin, layer, null);
	}

	public Rule(Selector selector, Declaration declaration, int order, Origin origin, int layer,
			ContainerQuery containerQuery) {
		this.selector = selector;
		this.declaration = declaration;
		this.order = order;
		this.origin = origin;
		this.layer = layer;
		this.containerQuery = containerQuery;
	}

	/** この規則を包む{@code @container}(無ければnull)。 */
	public ContainerQuery getContainerQuery() {
		return this.containerQuery;
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
	 * cascade origin を返します。
	 *
	 * @return
	 */
	public Origin getOrigin() {
		return this.origin;
	}

	/**
	 * cascadeレイヤーの優先順位番号を返します({@link #NO_LAYER}なら
	 * どのレイヤーにも属さない)。
	 *
	 * @return
	 */
	public int getLayer() {
		return this.layer;
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
