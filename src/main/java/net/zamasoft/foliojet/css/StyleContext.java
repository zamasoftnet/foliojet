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

	/** List of ancestor elements. */
	private final List<CSSElement> elementStack = new ArrayList<CSSElement>();

	public final CSSStyleSheet styleSheet;

	/**
	 * Results collected by the STRUCTURE_SCAN pass for pseudo-classes whose results
	 * are not final until an element ends (:has() and :last-child variants). See
	 * "two-pass control mode" in the development plan.
	 */
	private final SelectorFacts selectorFacts;

	/**
	 * Element facts for {@code @container} queries (2026-08-15 stage 4).
	 * Like {@link #selectorFacts}, reset at the start of STRUCTURE_SCAN
	 * and accumulated across passes (see {@link ContainerFacts}).
	 */
	private final ContainerFacts containerFacts;

	public StyleContext(CSSStyleSheet styleSheet, SelectorFacts selectorFacts, ContainerFacts containerFacts) {
		this.styleSheet = styleSheet;
		this.selectorFacts = selectorFacts;
		this.containerFacts = containerFacts;
	}

	/**
	 * Notifies the start of an element.
	 *
	 * @param ce
	 */
	public void startElement(CSSElement ce) {
		this.elementStack.add(ce);
	}

	/**
	 * Notifies the end of an element.
	 */
	public void endElement() {
		CSSElement ce = (CSSElement) this.elementStack.remove(this.elementStack.size() - 1);
	}

	/**
	 * Returns the corresponding style declarations at the start of a page (named
	 * pages N1a; merge in ascending specificity (f,g,h), then source order, with later declarations winning).
	 *
	 * @param page     page pseudo-element (first/left/right pseudo-classes)
	 * @param pageName current page name (null=unnamed)
	 */
	public Declaration nextPage(CSSElement page, String pageName) {
		final Declaration result = new Declaration();
		for (final PageRule rule : this.matchingPageRules(page, pageName, false)) {
			result.merge(rule.declaration);
		}
		return result;
	}

	/**
	 * Returns margin box declarations that apply to the page
	 * (the merge order matches {@link #nextPage(CSSElement, String)}).
	 */
	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page) {
		return this.pageMarginBoxes(page, null);
	}

	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page, String pageName) {
		return this.pageMarginBoxes(page, pageName, false);
	}

	/**
	 * @param blank whether this is a page with no content created by a forced page break ({@code @page :blank},
	 *                           2026-10-04). Known only at page rendering time, so only this method
	 *                           (margin boxes) receives it
	 */
	public Map<MarginBoxName, Declaration> pageMarginBoxes(CSSElement page, String pageName, boolean blank) {
		final Map<MarginBoxName, Declaration> result = new EnumMap<MarginBoxName, Declaration>(MarginBoxName.class);
		for (final PageRule rule : this.matchingPageRules(page, pageName, blank)) {
			for (Map.Entry<MarginBoxName, Declaration> e : rule.marginBoxes.entrySet()) {
				result.computeIfAbsent(e.getKey(), k -> new Declaration()).merge(e.getValue());
			}
		}
		return result;
	}

	/** Returns matching rules in ascending specificity (source order for ties). */
	private List<PageRule> matchingPageRules(CSSElement page, String pageName, boolean blank) {
		byte pseudo = blank ? PageRule.PSEUDO_BLANK : 0;
		if (page.isPseudoClass(CSSElement.PC_FIRST)) {
			pseudo |= PageRule.PSEUDO_FIRST;
		}
		if (page.isPseudoClass(CSSElement.PC_LEFT)) {
			pseudo |= PageRule.PSEUDO_LEFT;
		}
		if (page.isPseudoClass(CSSElement.PC_RIGHT)) {
			pseudo |= PageRule.PSEUDO_RIGHT;
		}
		if (page == CSSElement.PAGE_SINGLE_FIRST || page == CSSElement.PAGE_SINGLE) {
			pseudo |= PageRule.PSEUDO_SINGLE;
		}
		final List<PageRule> matched = new ArrayList<PageRule>();
		for (final PageRule rule : this.styleSheet.pageRules) {
			if (rule.matches(pageName, pseudo)) {
				matched.add(rule);
			}
		}
		// Stable sort: preserve source order for equal specificity.
		matched.sort(java.util.Comparator.comparingInt(PageRule::specificity));
		return matched;
	}

	/**
	 * Merges the style declarations for the current element with the given declarations.
	 *
	 * @return
	 */
	public Declaration merge(Declaration declaration) {
		return this.merge(declaration, null, null);
	}

	/**
	 * @param importantOut if non-null and layered rules exist, receives at index 0
	 *                                         the important declarations merged in reverse order
	 */
	public Declaration merge(Declaration declaration, Declaration[] userAgentOut, Declaration[] importantOut) {

		if (this.elementStack.isEmpty()) {
			return declaration;
		}
		// Match only candidate rules from the rightmost selector index.
		final CSSElement top = (CSSElement) this.elementStack.get(this.elementStack.size() - 1);

		// Accumulate :has() results (truth values are not final until an element ends,
		// so record them in SelectorFacts across passes; see "two-pass control mode"
		// in the development plan).
		if (this.selectorFacts != null) {
			recordHasFacts(this.styleSheet.getHasConditions(), this.elementStack, this.selectorFacts);
		}

		final List<List<Rule>> buckets = this.styleSheet.candidateBuckets(top);

		// Results that are final
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

		// Sort by specificity, then source order in the document (SPEC CSS2 6.4.1).
		// Candidates arrive unordered across buckets, so explicitly compare source order (Rule.order).
		Collections.sort(result, RuleComparator.INSTANCE);

		// Merge
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
		// **Combining @layer and !important**: layer order reverses among important declarations
		// (CSS Cascade 5). Only when two or more rules use layers, prepare
		// the material for applying important declarations again in reverse order (2026-08-03).
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

	/** Whether any rule belongs to a layer (whether a reverse merge is worthwhile). */
	private static boolean usesLayers(final List<Rule> rules) {
		for (int i = 0; i < rules.size(); ++i) {
			if (rules.get(i).getLayer() != Rule.NO_LAYER) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines whether selector (which may contain a combinator chain) matches
	 * starting from the last element in path. Shared by top-level rule evaluation
	 * in {@link #merge} and argument evaluation for :is()/:where()/:not(),
	 * whose arguments may contain descendant (space), child (&gt;), adjacent sibling (+),
	 * or general sibling (~) combinators (2026-07-19: unified duplicated logic). Non-recursive:
	 * iteratively traverse selector from right to left and path from its end (target element)
	 * to its beginning (most distant ancestor), without recursion over either tree.
	 *
	 * @param selector selector to evaluate
	 * @param path     ancestor chain ending at the target element (most distant ancestor first);
	 *                                 even if an adjacent or general sibling combinator moves the target
	 *                                 to a sibling outside this path, the remaining prefix still serves
	 *                                 as its ancestors because siblings share the same parent
	 * @param facts    results collected by STRUCTURE_SCAN for pseudo-classes requiring lookahead
	 *                                 (:has() and :last-child variants); may be null if unavailable
	 */
	private static boolean matchesFromPath(Selector selector, List<CSSElement> path, SelectorFacts facts) {
		boolean first = true;// For the first selector, a matching element must appear immediately.
		boolean child = false;// For a child selector, a matching element must appear immediately, excluding pseudo-elements.
		boolean sibling = false;// For an adjacent selector, move to the neighboring element without ascending path.
		CSSElement ce = null;
		List<CSSElement> ceView = null;// Ancestor chain ending at ce (for nested evaluation of :is()/:where()/:not())
		NEXT: for (int j = path.size() - 1; j >= 0; --j) {
			if (sibling) {
				sibling = false;
				ceView = withLast(path.subList(0, j), ce);
			} else {
				ce = path.get(j);
				ceView = path.subList(0, j + 1);
			}
			switch (selector.getSelectorType()) {
			// Child selector
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

			// Descendant selector
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

			// Adjacent selector
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

			// General sibling selector
			case GENERAL_ADJACENT_SELECTOR: {
				CombinatorSelector combinator = (CombinatorSelector) selector;
				SimpleSelector simpleSelector = combinator.getSimpleSelector();
				if (evaluateSimpleSelector(simpleSelector, ceView, facts)) {
					selector = combinator.getAncestorSelector();
					child = true;
					// Walk back until a preceding sibling matches the rightmost part of the left-hand selector.
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

			// Simple selector
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
	 * Accumulates :has() results. Use the current element (the end of path) as a new candidate,
	 * check each of its ancestors (every path element except the last), and record
	 * true results in facts. Do not recheck ancestors already known to be true:
	 * :has() tests existence within a subtree, so a true result
	 * never changes. Calling this throughout the document incrementally accumulates
	 * :has() truth values that become final when elements end. A single pass cannot finish this,
	 * so callers must invoke it across passes; see "two-pass control mode" in the development plan.
	 *
	 * @param hasConditions all :has() conditions in the document (in document order)
	 * @param path          ancestor chain (current element last)
	 * @param facts         destination for recorded results
	 */
	private static void recordHasFacts(List<Condition> hasConditions, List<CSSElement> path, SelectorFacts facts) {
		if (hasConditions.isEmpty() || path.size() < 2) {
			// Without an ancestor, there can be no subject for :has().
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
	 * Matches {@code @container} queries (2026-08-15 stage 4;
	 * development record §2/§6).
	 * Always matches if {@code query} is {@code null} (the rule is not inside
	 * {@code @container}). Otherwise, traverse the <b>ancestors</b> of the last path element
	 * (the current element) from nearest to farthest. Exclude the current element itself:
	 * an element cannot be its own container. Use only the first query container
	 * with a matching name (per the specification; do not combine multiple ancestors).
	 * If there is no such container, the query does not match.
	 *
	 * <p>
	 * Measured dimensions are values recorded by {@link ContainerFacts} through the previous pass
	 * ({@code NaN} means undetermined, hence always non-matching; design §2, "all queries are false in pass 1").
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
		// Element selector
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

		// Pseudo-element selector
		case PSEUDO_ELEMENT_SELECTOR: {
			if (!ce.isPseudoElement()) {
				return false;
			}
			PseudoElementSelector elementSelector = (PseudoElementSelector) selector;
			String name = elementSelector.getLocalName();
			return name.equals(ce.lName);
		}

		// Treat unsupported selectors as non-matching without stopping conversion.
		default:
			LOG.warning("未対応のセレクタです: " + selector.getSelectorType() + " " + selector);
			return false;
		}
	}

	private static boolean evaluateCondition(Condition condition, List<CSSElement> path, SelectorFacts facts) {
		CSSElement ce = path.get(path.size() - 1);
		switch (condition.getConditionType()) {
		// Class condition
		case CLASS_CONDITION: {
			String styleClass = condition.getValue();
			return ce.isStyleClass(styleClass);
		}

		// Pseudo-class condition
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
					// 2026-07-21: @scope is unsupported, so always treat :scope
					// as equivalent to :root (a simplification of CSS Selectors 4:
					// the root element is the default scope root when no other
					// scope root is specified in the stylesheet). When implementing
					// @scope, replace this simplification with scope root tracking
					// on elementStack.
					pc = CSSElement.PC_ROOT;
				}
				break;
			}
			return ce.isPseudoClass(pc);
		}

		// ID condition
		case ID_CONDITION: {
			String id = condition.getValue();
			return id.equalsIgnoreCase(ce.id);
		}

		// Attribute condition
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

		// Space-separated attribute value condition
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

		// Hyphen-separated attribute value condition
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

		// Language condition
		case LANG_CONDITION: {
			String value = condition.getValue();
			if (ce.lang == null) {
				return false;
			}
			String lang = ce.lang.getLanguage();
			return lang.equalsIgnoreCase(value);
		}

		// Directionality condition (:dir())
		case DIR_CONDITION: {
			String value = condition.getValue();
			return ce.dir != null && ce.dir.equalsIgnoreCase(value);
		}

		// An+B condition (:nth-child() / :nth-of-type())
		case NTH_CHILD_CONDITION: {
			NthCondition nth = (NthCondition) condition;
			return nth.matches(siblingPosition(ce, false));
		}
		case NTH_OF_TYPE_CONDITION: {
			NthCondition nth = (NthCondition) condition;
			return nth.matches(siblingPosition(ce, true));
		}

		// Pseudo-classes counted from the end (consult SelectorFacts collected by STRUCTURE_SCAN).
		// If facts itself or the scan result for the element is missing
		// (STRUCTURE_SCAN did not run, i.e., processing.pass-count<2),
		// treat them as non-matching, like unsupported selectors.
		case LAST_CHILD_CONDITION:
			return facts != null && facts.isLastChild(ce.elementKey);
		case ONLY_CHILD_CONDITION:
			// An element satisfying both :first-child (existing PC_FIRST_CHILD,
			// already determined at its start) and :last-child (STRUCTURE_SCAN)
			return ce.isPseudoClass(CSSElement.PC_FIRST_CHILD) && facts != null && facts.isLastChild(ce.elementKey);
		case EMPTY_CONDITION:
			return facts != null && facts.isEmpty(ce.elementKey);
		case LAST_OF_TYPE_CONDITION:
			return facts != null && facts.isLastOfType(ce.elementKey);
		case ONLY_OF_TYPE_CONDITION:
			// An element satisfying both the equivalent of :first-of-type
			// (existing siblingPosition(ce,true)==1) and :last-of-type (STRUCTURE_SCAN)
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

		// :has() (multiple arguments allowed, ORed). Only consult results accumulated
		// across passes by recordHasFacts, called from StyleContext.merge.
		case HAS_CONDITION:
			return facts != null && facts.isHasMatch(ce.elementKey, condition);

		// Prefix, suffix, and substring attribute value conditions
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
			// **Case-sensitive** (fixed on 2026-08-05). In CSS Selectors,
			// attribute selector value comparison is case-sensitive by default, becoming
			// case-insensitive only with the `i` flag. This code unconditionally lowercased both sides,
			// so `li[type^="a"]` (lowercase Roman numerals/letters) and `li[type^="A"]`
			// **both matched the same element, with the later rule winning**. `<li type="a">`
			// rendered as uppercase `H.`, and `<li type="i">` as `X.`.
			//
			// **This difference occupied only 0.027% of the baseline image and was hidden
			// by imageTest's 2% tolerance.** Found by visually comparing old and new images before rebuilding baselines.
			//
			// `=` and `~=` remain case-insensitive to follow HTML's historical list
			// of attributes whose values compare without regard to case
			// (type/align/valign, etc.). That list does not apply to prefix, suffix,
			// or substring matches, so only this code is changed to follow the standard.
			switch (condition.getConditionType()) {
			case PREFIX_ATTRIBUTE_CONDITION:
				return attr.startsWith(value);
			case SUFFIX_ATTRIBUTE_CONDITION:
				return attr.endsWith(value);
			default:
				return attr.contains(value);
			}
		}

		// :not pseudo-class condition (supports descendant, child, adjacent sibling, and general sibling
		// combinators in arguments). Passing path directly to matchesFromPath evaluates upward
		// through ancestors from the element attached to :not() (=the end of path). On 2026-07-19,
		// support expanded from adjacent/general sibling combinators only (evaluateSiblingChain)
		// to descendant/child combinators too (unified in matchesFromPath).
		case NOT_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (matchesFromPath(selector, path, facts)) {
					return false;
				}
			}
			return true;
		}

		// :is pseudo-class condition (specificity is the maximum in the argument list). ConditionType
		// distinguishes it from :where(), but matching is identical, so combine the cases.
		case IS_CONDITION:
		case WHERE_CONDITION: {
			for (Selector selector : ((SelectorListCondition) condition).getSelectors()) {
				if (matchesFromPath(selector, path, facts)) {
					return true;
				}
			}
			return false;
		}

		// Treat unsupported conditions as non-matching without stopping conversion.
		default:
			LOG.warning("未対応のセレクタ条件です: " + condition.getConditionType() + " " + condition);
			return false;
		}
	}

	/**
	 * Returns the sequence number among siblings (one-based). Iteratively follows
	 * the preceding sibling chain (CSSElement.precedingElement) toward the beginning
	 * (1P, without examining following elements); no recursion.
	 *
	 * @param ce           target element
	 * @param sameTypeOnly if true, count only siblings with the same element name
	 *                                         (for :nth-of-type())
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
 * Comparator for sorting rules by specificity.
 *
 * @author MIYABE Tatsuhiko
 */
class RuleComparator implements Comparator<Object> {
	/**
	 * Returns an instance of this class.
	 */
	public static final RuleComparator INSTANCE = new RuleComparator();

	private RuleComparator() {
		// singleton
	}

	/**
	 * Compare first by ascending cascade origin (USER_AGENT &lt; AUTHOR), then
	 * by cascade layer source order (unlayered {@link Rule#NO_LAYER} always has
	 * highest priority; among layers, later ones take precedence),
	 * then by ascending specificity within a layer, and finally by stylesheet
	 * source order for equal specificity (CSS Cascading and Inheritance:
	 * origin/importance → layer → specificity → order。2026-07-21、
	 * The layer step was added for CSS Cascade Layers. Layer priority
	 * reversal for important is unsupported; see {@link Rule#getLayer()}).
	 */
	public int compare(Object o1, Object o2) {
		return compare((Rule) o1, (Rule) o2, false);
	}

	/**
	 * Compares with <b>reversed layer order</b> if {@code important} is true
	 * (added on 2026-08-03).
	 *
	 * <p>
	 * In CSS Cascade 5, precedence among {@code !important} declarations is
	 * <b>the reverse of normal order</b>: unlayered declarations are <b>weakest</b>,
	 * and <b>earlier layers</b> are stronger. Chrome, Firefox, and Safari all follow
	 * the specification (confirmed in an independent consultation on 2026-08-03).
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

	/** Comparator for {@code !important} declarations (reverses layer order). */
	static final Comparator<Object> IMPORTANT = new Comparator<Object>() {
		public int compare(final Object o1, final Object o2) {
			return RuleComparator.compare((Rule) o1, (Rule) o2, true);
		}
	};

}
