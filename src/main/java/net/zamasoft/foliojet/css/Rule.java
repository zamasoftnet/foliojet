package net.zamasoft.foliojet.css;


import net.zamasoft.foliojet.css.container.ContainerQuery;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Specificity;

/**
 * A CSS rule: a pair consisting of a selector and its corresponding style declaration.
 * Immutable after stylesheet construction, and shareable across threads.
 *
 * @author MIYABE Tatsuhiko
 */
public class Rule {
	/**
	 * The {@link #layer} value for rules outside cascade layers (added on 2026-07-21,
	 * CSS Cascade Layers). Takes precedence over every layer, including
	 * the latest one. Using {@code Integer.MAX_VALUE} makes normal ascending comparison
	 * give unlayered rules the highest priority.
	 */
	public static final int NO_LAYER = Integer.MAX_VALUE;

	private final Selector selector;

	private final Declaration declaration;

	/** Source order within the stylesheet. Determines precedence for rules with equal specificity. */
	private final int order;

	/** Cascade origin. Precedes specificity and source order (USER_AGENT always ranks below AUTHOR). */
	private final Origin origin;

	/**
	 * The priority number of the cascade layer (added on 2026-07-21, CSS Cascade
	 * Layers)。{@link CSSStyleSheet#registerNamedLayer}/
	 * {@link CSSStyleSheet#registerAnonymousLayer}: the sequence number
	 * of the layer's first occurrence in the stylesheet. {@link #NO_LAYER}
	 * (a rule outside all layers) always takes highest priority. Compared immediately after
	 * origin, before specificity and source order (CSS Cascading and
	 * Inheritance: origin/importance → layer → specificity → order)。
	 * Layer priority reversal for {@code !important} (important declarations in
	 * <b>earlier layers</b> outrank later layers, with unlayered declarations weakest)
	 * <b>was supported on 2026-08-03</b>. After applying the cascade once in normal order,
	 * apply only important declarations again in reverse order
	 * ({@link Declaration#applyImportantProperties}、
	 * {@code RuleComparator.IMPORTANT}). Later important declarations win,
	 * so the strongest is applied last. Documents with no layered rules
	 * skip the reverse merge entirely (zero cost).
	 * Origin reversal, where <b>UA important declarations outrank author important declarations</b>,
	 * remains unsupported because it has no effect in print use cases.
	 */
	private final int layer;

	private transient Specificity specificity = null;

	/**
	 * The name and condition of the enclosing {@code @container} (2026-08-15 stage 4;
	 * development record). Set to {@code null} when not inside {@code @container}
	 * (regular rules have no container condition).
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

	/** The enclosing {@code @container} for this rule (null if none). */
	public ContainerQuery getContainerQuery() {
		return this.containerQuery;
	}

	/**
	 * Returns the selector.
	 *
	 * @return
	 */
	public Selector getSelector() {
		return this.selector;
	}

	/**
	 * Returns the style declaration.
	 *
	 * @return
	 */
	public Declaration getDeclaration() {
		return this.declaration;
	}

	/**
	 * Returns the source order within the stylesheet.
	 *
	 * @return
	 */
	public int getOrder() {
		return this.order;
	}

	/**
	 * Returns the cascade origin.
	 *
	 * @return
	 */
	public Origin getOrigin() {
		return this.origin;
	}

	/**
	 * Returns the cascade layer priority number ({@link #NO_LAYER} means
	 * the rule belongs to no layer).
	 *
	 * @return
	 */
	public int getLayer() {
		return this.layer;
	}

	/**
	 * Returns the selector's specificity.
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
