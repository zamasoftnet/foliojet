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
	 * {@code !important}によるレイヤー優先順位の反転(仕様上は
	 * importantな宣言は先に現れたレイヤーが後のレイヤーに優先する)は
	 * 未対応——本エンジンの現在の適用モデル(cascade順に規則を1回だけ
	 * 適用し、important指定済みプロパティへの以後のnormal上書きだけを
	 * 禁止する単純な仕組み)ではこの反転を素直に表現できないため、既知の
	 * 簡略化として記録する(CSS仕様全体準拠は目的ではない、ChatGPT/
	 * 過去の方針判断と同じ扱い)。
	 */
	private final int layer;

	private transient Specificity specificity = null;

	public Rule(Selector selector, Declaration declaration, int order, Origin origin) {
		this(selector, declaration, order, origin, NO_LAYER);
	}

	public Rule(Selector selector, Declaration declaration, int order, Origin origin, int layer) {
		this.selector = selector;
		this.declaration = declaration;
		this.order = order;
		this.origin = origin;
		this.layer = layer;
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
