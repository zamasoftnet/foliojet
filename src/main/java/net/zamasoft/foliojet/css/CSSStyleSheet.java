package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * A stylesheet returns the declarations that apply to a given element.
 * <p>
 * This class is intended to apply styles incrementally to a document supplied as SAX events.
 * Elements need not correspond to SAX event elements; they may represent document components such as CSS pseudo-classes.
 * Each startElement must have a corresponding, consistently ordered endElement call.
 * </p>
 * <p>
 * Rules are indexed by the rightmost simple selector (ID, class, element name, or pseudo-element),
 * and matching for each element examines only candidate buckets.
 * Immutable after construction (addRule/addPage), and shareable across threads.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSStyleSheet {
	/** All rules (in document order). */
	final List<Rule> rules = new ArrayList<Rule>();

	/*
	 * Index by rightmost simple selector. Each rule belongs to exactly one bucket.
	 * The index is a conservative superset that never omits a potentially matching rule;
	 * StyleContext performs the actual match.
	 */
	private final Map<String, List<Rule>> idToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> classToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> nameToRules = new HashMap<String, List<Rule>>();
	private final Map<String, List<Rule>> pseudoElementToRules = new HashMap<String, List<Rule>>();
	private final List<Rule> universalRules = new ArrayList<Rule>();

	/**
	 * All {@code :has()} conditions in the document (in document order). Their truth values
	 * are not final until an element ends, so {@code StyleContext} uses them to accumulate
	 * results up the ancestor chain for each element (see "two-pass control mode" in the development plan).
	 */
	private final List<Condition> hasConditions = new ArrayList<Condition>();

	/**
	 * The sequence of structured {@code @page} rules (named pages N1a,
	 * 2026-07-31; replaced the old four buckets: unnamed/first/left/right).
	 * StyleContext applies them by merging in ascending specificity (f,g,h), then source order.
	 */
	final List<PageRule> pageRules = new ArrayList<PageRule>();

	/**
	 * A cascade layer (CSS Cascade 5 §6.4, added on 2026-07-21, a tree since 2026-10-09). {@link #rank} is the layer's
	 * place in the layer order, which {@link Rule#getLayer()} returns: the sublayers of a layer come before the layer's
	 * own rules (they form "an implicit final sublayer"), siblings follow their first appearance, and a layer's
	 * whole subtree sits at its place among its siblings. Until 2026-10-09 every dotted name was an independent layer
	 * numbered at its first appearance, so {@code @layer a { ... @layer b { ... } }} let a.b beat a's own rules, and a
	 * sublayer declared late ({@code @layer v.k} after {@code @layer w}) beat the later siblings of its parent.
	 */
	static final class Layer {
		final Layer parent;

		/** The name, null for an anonymous layer and the root. */
		final String name;

		final List<Layer> children = new ArrayList<Layer>();

		/** The id that the registering methods return (the index in {@code layersById}). */
		int id = Rule.NO_LAYER;

		/** The place in the layer order; recomputed when the tree grows. */
		int rank = Rule.NO_LAYER;

		Layer(final Layer parent, final String name) {
			this.parent = parent;
			this.name = name;
		}
	}

	/** The root of the layer tree: its children are the top-level layers; it stands for the unlayered rules. */
	private final Layer layerRoot = new Layer(null, null);

	/** Layers by the ids that {@link #registerNamedLayer} and {@link #registerAnonymousLayer} return. */
	private final List<Layer> layersById = new ArrayList<Layer>();

	/**
	 * Registers a named layer, with its ancestors, under a parent layer and returns its id. A name already
	 * registered keeps its place (spec: appending to a layer does not change its order).
	 *
	 * @param parent the id of the enclosing layer, {@link Rule#NO_LAYER} at the top level
	 * @param path   the names, outermost first ({@code @layer a.b} gives {@code [a, b]})
	 */
	public int registerNamedLayer(final int parent, final List<String> path) {
		Layer node = this.layer(parent);
		boolean grown = false;
		for (final String name : path) {
			Layer child = null;
			for (final Layer c : node.children) {
				if (name.equals(c.name)) {
					child = c;
					break;
				}
			}
			if (child == null) {
				child = this.newLayer(node, name);
				grown = true;
			}
			node = child;
		}
		if (grown) {
			this.rankLayers();
		}
		return node.id;
	}

	/**
	 * Registers a new anonymous layer ({@code @layer { ... }}; spec: anonymous layers are always unique) under a
	 * parent layer and returns its id.
	 *
	 * @param parent the id of the enclosing layer, {@link Rule#NO_LAYER} at the top level
	 */
	public int registerAnonymousLayer(final int parent) {
		final Layer layer = this.newLayer(this.layer(parent), null);
		this.rankLayers();
		return layer.id;
	}

	private Layer newLayer(final Layer parent, final String name) {
		final Layer layer = new Layer(parent, name);
		parent.children.add(layer);
		layer.id = this.layersById.size();
		this.layersById.add(layer);
		return layer;
	}

	private Layer layer(final int id) {
		return id == Rule.NO_LAYER ? this.layerRoot : this.layersById.get(id);
	}

	/** Numbers the layers in layer order: each layer after all of its sublayers (post-order). */
	private void rankLayers() {
		final java.util.ArrayDeque<Object[]> stack = new java.util.ArrayDeque<Object[]>();
		int next = 0;
		stack.push(new Object[] { this.layerRoot, 0 });
		while (!stack.isEmpty()) {
			final Object[] top = stack.peek();
			final Layer node = (Layer) top[0];
			final int i = (Integer) top[1];
			if (i < node.children.size()) {
				top[1] = i + 1;
				stack.push(new Object[] { node.children.get(i), 0 });
			} else {
				stack.pop();
				if (node != this.layerRoot) {
					node.rank = next++;
				}
			}
		}
	}

	/**
	 * Adds a rule (inside {@code @container}, 2026-08-15 stage 4).
	 *
	 * @param containerQuery the enclosing {@code @container} for this rule (null if none)
	 */
	public void addRule(List<Selector> selectors, Declaration declaration, Origin origin, int layer,
			net.zamasoft.foliojet.css.container.ContainerQuery containerQuery) {
		if (declaration == null) {
			return;
		}
		final Layer cascadeLayer = layer == Rule.NO_LAYER ? null : this.layersById.get(layer);
		for (Selector selector : selectors) {// Note the loop!
			Rule rule = new Rule(selector, declaration, this.rules.size(), origin, cascadeLayer, containerQuery);
			this.rules.add(rule);
			this.index(rule);
			collectHasConditions(selector, this.hasConditions);
		}
	}

	/**
	 * Collects all {@code :has()} conditions in selector into out, including those
	 * nested in combinator chains or {@code :not()}/{@code :is()}/{@code :where()}.
	 * Uses ordinary recursion because it only traverses the selector AST
	 * (finite and derived from syntax), so the traversal depth is not unbounded.
	 * It does not traverse the element tree and differs from the unbounded depth of HTML input;
	 * this follows the same reasoning as existing code such as Tokens.fromExpression.
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
		// Use the most selective bucket, in order: ID > class > element name > universal.
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
		// Normalize keys to lowercase because ID, class, and element name matching is case-insensitive.
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
	 * Returns buckets of rules whose rightmost simple selector may match the element.
	 * The returned rules are candidates (a superset); the caller performs the actual match.
	 *
	 * @param ce the element to match (the top of the element stack)
	 * @return a collection of candidate rule lists
	 */
	List<List<Rule>> candidateBuckets(CSSElement ce) {
		List<List<Rule>> buckets = new ArrayList<List<Rule>>(4);
		if (ce.isPseudoElement()) {
			// Only rules with pseudo-element selectors match pseudo-elements.
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
	 * Returns all {@code :has()} conditions in the document (in document order, immutable).
	 * Excludes nested {@code :has()} (inside :has() arguments); this is a limitation
	 * of the initial implementation (see the development plan).
	 */
	public List<Condition> getHasConditions() {
		return Collections.unmodifiableList(this.hasConditions);
	}

	/**
	 * Adds a structured {@code @page} rule (named pages N1a).
	 * One entry per rule; declarations and margin boxes share the same specificity and source order.
	 *
	 * @param name        page name (null=unnamed)
	 * @param pseudoMask  required pseudo-pages ({@link PageRule#PSEUDO_FIRST}, etc.)
	 * @param declaration regular declarations (may be null)
	 * @return the added rule (the caller populates its margin boxes)
	 */
	public PageRule addPageRule(String name, byte pseudoMask, Declaration declaration) {
		final PageRule rule = new PageRule(name, pseudoMask, declaration, this.pageRules.size());
		this.pageRules.add(rule);
		return rule;
	}

	/** Adds margin box declarations to a rule. */
	public void addPageRuleMarginBox(PageRule rule, MarginBoxName box, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		rule.marginBoxes.computeIfAbsent(box, k -> new Declaration()).merge(declaration);
	}

}
