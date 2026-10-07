package net.zamasoft.foliojet.css.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSSelectorAttribute;
import com.helger.css.decl.CSSSelectorMemberFunctionLike;
import com.helger.css.decl.CSSSelectorMemberNot;
import com.helger.css.decl.CSSSelectorMemberPseudoHas;
import com.helger.css.decl.CSSSelectorMemberPseudoIs;
import com.helger.css.decl.CSSSelectorMemberPseudoWhere;
import com.helger.css.decl.CSSSelectorSimpleMember;
import com.helger.css.decl.ECSSSelectorCombinator;
import com.helger.css.decl.ICSSSelectorMember;
import com.helger.css.writer.CSSWriterSettings;

import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.selector.AttributeCondition;
import net.zamasoft.foliojet.css.selector.CombinatorSelector;
import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.Condition.ConditionType;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.NthCondition;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SelectorListCondition;
import net.zamasoft.foliojet.css.selector.SimpleSelector;
import net.zamasoft.foliojet.css.selector.ValueCondition;

/**
 * Converts the ph-css selector syntax tree to the internal selector model.
 */
public final class SelectorConverter {
	private static final Logger LOG = Logger.getLogger(SelectorConverter.class.getName());

	private static final CSSWriterSettings WRITER_SETTINGS = new CSSWriterSettings();

	private SelectorConverter() {
		// utility
	}

	/**
	 * Decodes CSS identifier escapes ({@code \X} and {@code \XXXXXX} forms).
	 *
	 * <p>
	 * <b>ph-css {@code CSSSelectorSimpleMember.getValue()} returns the raw selector
	 * string unchanged and does not resolve identifier escapes</b> (discovered on 2026-08-06
	 * in Tailwind CSS in the full-scale corpus: state/responsive/arbitrary variant prefixes
	 * such as `hover:`, `lg:`, and `[&_svg]:size-4` form class names by backslash-escaping
	 * colons, brackets, ampersands, etc.). In contrast, HTML {@code class} attribute values
	 * have no escapes (they are plain strings). Comparing with backslashes still present
	 * therefore always made {@code ce.isStyleClass()} fail, so none of the declarations
	 * associated with those classes (width/height, visibility, etc.) were applied.
	 * The same reasoning applies to element and pseudo-class names as well as class and ID
	 * names, so decode values at every extraction point.
	 * </p>
	 */
	private static String unescapeCssIdent(String s) {
		return Tokens.unescape(s);
	}

	/**
	 * Registry of valid pseudo-class names (those that do not invalidate a selector list).
	 *
	 * <p>
	 * In CSS, <b>a selector list containing even one invalid selector causes the entire
	 * rule to be discarded</b>. Accepting an unknown pseudo-class as a condition that never
	 * matches would make browser-detection hacks that rely on this rule (Safari-only rules
	 * such as {@code _::-webkit-full-page-media, _:future, :root body .foo}) apply only in
	 * CopperPDF (observed on pc.watch.impress.co.jp, 2026-08-09). Names listed here are
	 * not interpreted but do not invalidate the rule (interactive pseudo-classes, etc.);
	 * unlisted names invalidate the entire rule (following Chrome's choice).
	 * </p>
	 */
	private static final java.util.Set<String> VALID_PSEUDO_CLASSES = java.util.Set.of(
			// Links and user interaction (valid, but do not match in print)
			"link", "visited", "any-link", "local-link", "hover", "active", "focus", "focus-visible",
			"focus-within", "target", "target-within", "scope",
			// Structural pseudo-classes
			"root", "empty", "first-child", "last-child", "only-child", "first-of-type", "last-of-type",
			"only-of-type", "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type",
			// Logical combinators
			"is", "where", "not", "has",
			// Input states
			"enabled", "disabled", "checked", "indeterminate", "default", "placeholder-shown", "autofill",
			"read-only", "read-write", "required", "optional", "valid", "invalid", "user-valid",
			"user-invalid", "in-range", "out-of-range", "blank",
			// Language and direction
			"lang", "dir",
			// Display states
			"defined", "fullscreen", "modal", "picture-in-picture", "popover-open", "open", "closed",
			// Pages (@page context, but do not invalidate rules when used on elements)
			"first", "left", "right",
			// Shadow DOM (unsupported but valid syntax)
			"host", "host-context",
			// -webkit- names accepted by Chrome (common examples on real sites)
			"-webkit-any", "-webkit-autofill", "-webkit-full-screen");

	/**
	 * Registry of valid pseudo-element names (same purpose as {@link #VALID_PSEUDO_CLASSES}).
	 * Pseudo-elements with the {@code -webkit-} prefix are valid regardless of their names
	 * (but do not match): an explicit CSS specification exception for web compatibility,
	 * which Chrome also follows. This is why Safari hacks combine
	 * {@code ::-webkit-full-page-media} (does not invalidate rules in any browser) with
	 * {@code :future} (invalidates the entire rule outside Safari).
	 */
	private static final java.util.Set<String> VALID_PSEUDO_ELEMENTS = java.util.Set.of(
			"before", "after", "first-line", "first-letter", "marker", "selection", "placeholder",
			"backdrop", "cue", "cue-region", "file-selector-button", "details-content", "target-text",
			"spelling-error", "grammar-error", "highlight", "part", "slotted",
			// GCPM footnotes (implemented by CopperPDF)
			"footnote-call", "footnote-marker");

	/**
	 * Throws {@link CSSException} for an unknown pseudo-class/pseudo-element
	 * (the caller's selector-list processing discards the entire rule).
	 */
	private static void requireValidPseudoClass(final String name) throws CSSException {
		final int paren = name.indexOf('(');
		final String bare = (paren >= 0 ? name.substring(0, paren) : name).toLowerCase(Locale.ROOT);
		if (!VALID_PSEUDO_CLASSES.contains(bare)) {
			throw new CSSException("未知の擬似クラスを含むセレクタです: :" + name);
		}
	}

	private static void requireValidPseudoElement(final String name) throws CSSException {
		final String bare = name.toLowerCase(Locale.ROOT);
		if (bare.startsWith("-webkit-")) {
			return;
		}
		if (!VALID_PSEUDO_ELEMENTS.contains(bare)) {
			throw new CSSException("未知の擬似要素を含むセレクタです: ::" + name);
		}
	}

	/**
	 * Pseudo-elements for which CSS2.1 permits single-colon notation.
	 */
	private static boolean isLegacyPseudoElement(String name) {
		switch (name.toLowerCase(Locale.ROOT)) {
		case "before":
		case "after":
		case "first-line":
		case "first-letter":
			return true;
		default:
			return false;
		}
	}

	static Selector convert(CSSSelector selector) throws CSSException {
		Selector chain = null;
		SelectorType pendingCombinator = SelectorType.DESCENDANT_SELECTOR;
		String localName = null;
		List<Condition> conditions = new ArrayList<Condition>();
		String pseudoElement = null;
		boolean inCompound = false;
		// ph-css 8.2.1 correctly parses :has(...) as CSSSelectorMemberPseudoHas,
		// but immediately follows it with as many duplicate raw CSSSelector instances
		// as there are arguments (CSSSelector itself implements ICSSSelectorMember
		// and appears directly in the member sequence). Confirmed by observation on 2026-07-19;
		// probably a remnant of another parsing path inside ph-css. Unless ignored, these
		// hit the unsupported-member branch below, which adds an extra never-matching condition
		// and makes the entire compound containing :has() never match. Therefore, immediately
		// after processing CSSSelectorMemberPseudoHas, skip the following raw CSSSelector
		// members, one per argument.
		int skipDuplicateHasSelectors = 0;

		for (ICSSSelectorMember member : selector.getAllMembers()) {
			if (skipDuplicateHasSelectors > 0 && member instanceof CSSSelector) {
				--skipDuplicateHasSelectors;
				continue;
			}
			if (member instanceof ECSSSelectorCombinator) {
				if (!inCompound) {
					throw new CSSException("結合子の左側にセレクタがありません: " + selector);
				}
				chain = attach(chain, pendingCombinator, localName, conditions, pseudoElement);
				localName = null;
				conditions = new ArrayList<Condition>();
				pseudoElement = null;
				inCompound = false;
				switch ((ECSSSelectorCombinator) member) {
				case GREATER:
					pendingCombinator = SelectorType.CHILD_SELECTOR;
					break;
				case PLUS:
					pendingCombinator = SelectorType.DIRECT_ADJACENT_SELECTOR;
					break;
				case TILDE:
					pendingCombinator = SelectorType.GENERAL_ADJACENT_SELECTOR;
					break;
				default:
					pendingCombinator = SelectorType.DESCENDANT_SELECTOR;
					break;
				}
				continue;
			}
			inCompound = true;
			if (member instanceof CSSSelectorSimpleMember) {
				CSSSelectorSimpleMember simple = (CSSSelectorSimpleMember) member;
				String value = simple.getValue();
				if (simple.isHash()) {
					conditions.add(new ValueCondition(ConditionType.ID_CONDITION, unescapeCssIdent(value.substring(1))));
				} else if (simple.isClass()) {
					conditions.add(new ValueCondition(ConditionType.CLASS_CONDITION, unescapeCssIdent(value.substring(1))));
				} else if (simple.isPseudo()) {
					if (value.startsWith("::")) {
						pseudoElement = unescapeCssIdent(value.substring(2));
						requireValidPseudoElement(pseudoElement);
					} else {
						String name = unescapeCssIdent(value.substring(1));
						// Static typesetting has no visited state, so fold :any-link into the same
						// condition as :link, which matches elements with href.
						if (name.equalsIgnoreCase("any-link")) {
							name = "link";
						}
						if (isLegacyPseudoElement(name)) {
							pseudoElement = name;
						} else if (name.equalsIgnoreCase("first-of-type")) {
							// :first-of-type is equivalent to :nth-of-type(1)
							conditions.add(new NthCondition(ConditionType.NTH_OF_TYPE_CONDITION, 0, 1, "1"));
						} else if (name.equalsIgnoreCase("last-child")) {
							// Resolved in STRUCTURE_SCAN (see the development plan, "2パス制御モード")
							conditions.add(new ValueCondition(ConditionType.LAST_CHILD_CONDITION, name));
						} else if (name.equalsIgnoreCase("only-child")) {
							conditions.add(new ValueCondition(ConditionType.ONLY_CHILD_CONDITION, name));
						} else if (name.equalsIgnoreCase("empty")) {
							conditions.add(new ValueCondition(ConditionType.EMPTY_CONDITION, name));
						} else if (name.equalsIgnoreCase("last-of-type")) {
							conditions.add(new ValueCondition(ConditionType.LAST_OF_TYPE_CONDITION, name));
						} else if (name.equalsIgnoreCase("only-of-type")) {
							conditions.add(new ValueCondition(ConditionType.ONLY_OF_TYPE_CONDITION, name));
						} else {
							// ph-css 8.2.1 passes :nth-child()/:nth-of-type() as a single
							// simple-selector string including parentheses (e.g. "nth-child(odd)"),
							// rather than as CSSSelectorMemberFunctionLike.
							// This is asymmetric with :lang()/:dir(), which become FunctionLike
							// (confirmed by observation on 2026-07-18). Detect the function-call
							// form here.
							Condition functional = tryConvertFunctionalPseudo(name);
							if (functional == null) {
								requireValidPseudoClass(name);
							}
							conditions.add(functional != null ? functional
									: new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION, name));
						}
					}
				} else {
					// Element name (ignore the namespace prefix)
					String name = value;
					int bar = name.lastIndexOf('|');
					if (bar != -1) {
						name = name.substring(bar + 1);
					}
					if (!name.equals("*")) {
						localName = unescapeCssIdent(name);
					}
				}
			} else if (member instanceof CSSSelectorAttribute) {
				conditions.add(convertAttribute((CSSSelectorAttribute) member));
			} else if (member instanceof CSSSelectorMemberNot) {
				conditions.add(new SelectorListCondition(ConditionType.NOT_CONDITION,
						convertList(((CSSSelectorMemberNot) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoIs) {
				// :is() arguments form a forgiving list: discard only the invalid selector
				// argument, without invalidating the rule (CSS Selectors 4).
				conditions.add(new SelectorListCondition(ConditionType.IS_CONDITION,
						convertForgivingList(((CSSSelectorMemberPseudoIs) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoWhere) {
				// :where() matches in the same way as :is(), but its specificity is always zero
				// (CSS Selectors4 specification). Distinguish it with a separate ConditionType
				// (see SelectorListCondition.getSpecificity).
				// Arguments form a forgiving list, as with :is().
				conditions.add(new SelectorListCondition(ConditionType.WHERE_CONDITION,
						convertForgivingList(((CSSSelectorMemberPseudoWhere) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoHas) {
				List<CSSSelector> hasArgs = ((CSSSelectorMemberPseudoHas) member).getAllSelectors();
				List<Selector> relativeSelectors = new ArrayList<Selector>(hasArgs.size());
				for (CSSSelector hasArg : hasArgs) {
					relativeSelectors.add(convert(unwrapHasArgument(hasArg)));
				}
				conditions.add(new SelectorListCondition(ConditionType.HAS_CONDITION, relativeSelectors));
				// Skip the duplicate raw CSSSelector members immediately following it (see the comment above)
				skipDuplicateHasSelectors = hasArgs.size();
			} else if (member instanceof CSSSelectorMemberFunctionLike) {
				CSSSelectorMemberFunctionLike function = (CSSSelectorMemberFunctionLike) member;
				String name = function.getFunctionName();
				// The form has a leading colon and a trailing parenthesis, as in ":lang("
				name = name.substring(name.startsWith("::") ? 2 : 1, name.length() - 1);
				String param = function.getParameterExpression().getAsCSSString(WRITER_SETTINGS, 0);
				if (name.equalsIgnoreCase("lang")) {
					conditions.add(new ValueCondition(ConditionType.LANG_CONDITION, param));
				} else if (name.equalsIgnoreCase("dir")) {
					conditions.add(new ValueCondition(ConditionType.DIR_CONDITION, param.trim()));
				} else {
					// Treat unsupported functional pseudo-classes (nth-last-child, etc.) as non-matching conditions.
					// nth-child/nth-of-type do not reach here (the ph-css implementation handles them
					// on the CSSSelectorSimpleMember side; see the isPseudo() branch above).
					requireValidPseudoClass(name);
					conditions.add(new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION,
							name + "(" + param + ")"));
				}
			} else {
				LOG.fine("未対応のセレクタメンバーです: " + member);
				conditions.add(new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION, member.toString()));
			}
		}
		if (!inCompound) {
			throw new CSSException("空のセレクタです: " + selector);
		}
		return attach(chain, pendingCombinator, localName, conditions, pseudoElement);
	}

	/**
	 * ph-css 8.2.1 returns each :has() NestedSelectors entry with the actual selector
	 * wrapped in another CSSSelector as its sole member (an asymmetry specific to :has(),
	 * absent from :not()/:is()/:where(); confirmed by observation on 2026-07-19).
	 * Unwrap while the sole member is a CSSSelector to reach the actual selector
	 * (iteration follows only the finite depth of the syntax tree; no recursion).
	 */
	private static CSSSelector unwrapHasArgument(CSSSelector selector) {
		CSSSelector current = selector;
		List<ICSSSelectorMember> members = current.getAllMembers();
		while (members.size() == 1 && members.get(0) instanceof CSSSelector) {
			current = (CSSSelector) members.get(0);
			members = current.getAllMembers();
		}
		return current;
	}

	public static List<Selector> convertList(List<CSSSelector> selectors) throws CSSException {
		List<Selector> result = new ArrayList<Selector>(selectors.size());
		for (CSSSelector selector : selectors) {
			result.add(convert(selector));
		}
		return result;
	}

	/**
	 * Converts a forgiving selector list (arguments of {@code :is()}/{@code :where()}).
	 * Discards only entries that cannot be interpreted, without throwing an exception.
	 */
	static List<Selector> convertForgivingList(List<CSSSelector> selectors) {
		List<Selector> result = new ArrayList<Selector>(selectors.size());
		for (CSSSelector selector : selectors) {
			try {
				result.add(convert(selector));
			} catch (CSSException e) {
				// Forgiving: discard only this entry
			}
		}
		return result;
	}

	private static Selector attach(Selector chain, SelectorType combinator, String localName,
			List<Condition> conditions, String pseudoElement) {
		SimpleSelector element = new ElementSelector(localName, conditions);
		Selector result;
		if (chain == null) {
			result = element;
		} else {
			result = new CombinatorSelector(combinator, chain, element);
		}
		if (pseudoElement != null) {
			// Legacy model compatibility: represent pseudo-elements with a descendant combinator
			result = new CombinatorSelector(SelectorType.DESCENDANT_SELECTOR, result,
					new PseudoElementSelector(pseudoElement));
		}
		return result;
	}

	/**
	 * Detects and parses functional pseudo-classes that ph-css passes as raw
	 * CSSSelectorSimpleMember strings (observed: nth-child()/nth-of-type()).
	 * Returns null if the string is not a function call or the function name is unsupported.
	 */
	private static Condition tryConvertFunctionalPseudo(String name) {
		int paren = name.indexOf('(');
		if (paren < 0 || !name.endsWith(")")) {
			return null;
		}
		String fname = name.substring(0, paren);
		String param = name.substring(paren + 1, name.length() - 1);
		if (fname.equalsIgnoreCase("nth-child")) {
			return convertNth(ConditionType.NTH_CHILD_CONDITION, fname, param);
		}
		if (fname.equalsIgnoreCase("nth-of-type")) {
			return convertNth(ConditionType.NTH_OF_TYPE_CONDITION, fname, param);
		}
		if (fname.equalsIgnoreCase("nth-last-child")) {
			// Resolved in STRUCTURE_SCAN (see the development plan, "2パス制御モード")
			return convertNth(ConditionType.NTH_LAST_CHILD_CONDITION, fname, param);
		}
		if (fname.equalsIgnoreCase("nth-last-of-type")) {
			return convertNth(ConditionType.NTH_LAST_OF_TYPE_CONDITION, fname, param);
		}
		return null;
	}

	/**
	 * Parses :nth-child() / :nth-of-type() arguments (An+B syntax).
	 * If parsing fails, warns about an unsupported selector and returns a condition that
	 * never matches (2026-07 policy: continue with no match for unknown selectors, without exceptions).
	 */
	private static Condition convertNth(ConditionType type, String name, String param) {
		int[] ab = parseNth(param);
		if (ab == null) {
			LOG.warning(":" + name + "() の引数を解析できません: " + param);
			return new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION, name + "(" + param + ")");
		}
		return new NthCondition(type, ab[0], ab[1], param.trim());
	}

	/**
	 * Parses An+B syntax (odd / even / integer / an+b) using only iteration (character
	 * scanning), without recursion. Returns null if parsing fails.
	 */
	static int[] parseNth(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.equalsIgnoreCase("odd")) {
			return new int[] { 2, 1 };
		}
		if (s.equalsIgnoreCase("even")) {
			return new int[] { 2, 0 };
		}
		int nIndex = -1;
		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			if (c == 'n' || c == 'N') {
				nIndex = i;
				break;
			}
		}
		if (nIndex < 0) {
			// No "n": integer only (a=0)
			Integer b = parseSignedInt(s);
			return b == null ? null : new int[] { 0, b.intValue() };
		}
		String aPart = s.substring(0, nIndex).trim();
		int a;
		if (aPart.isEmpty() || aPart.equals("+")) {
			a = 1;
		} else if (aPart.equals("-")) {
			a = -1;
		} else {
			Integer parsedA = parseSignedInt(aPart);
			if (parsedA == null) {
				return null;
			}
			a = parsedA.intValue();
		}
		String bPart = s.substring(nIndex + 1).trim();
		int b;
		if (bPart.isEmpty()) {
			b = 0;
		} else {
			Integer parsedB = parseSignedInt(bPart);
			if (parsedB == null) {
				return null;
			}
			b = parsedB.intValue();
		}
		return new int[] { a, b };
	}

	/**
	 * Parses a signed integer (also permits whitespace between sign and digits, e.g. "+ 3").
	 * Returns null if parsing fails.
	 */
	private static Integer parseSignedInt(String part) {
		part = part.trim();
		if (part.isEmpty()) {
			return null;
		}
		boolean negative = false;
		int i = 0;
		char first = part.charAt(0);
		if (first == '+' || first == '-') {
			negative = first == '-';
			++i;
			while (i < part.length() && Character.isWhitespace(part.charAt(i))) {
				++i;
			}
		}
		int start = i;
		while (i < part.length() && Character.isDigit(part.charAt(i))) {
			++i;
		}
		if (start == i || i != part.length()) {
			return null;
		}
		try {
			int value = Integer.parseInt(part.substring(start, i));
			return Integer.valueOf(negative ? -value : value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Condition convertAttribute(CSSSelectorAttribute attribute) {
		String name = attribute.getAttrName();
		String value = attribute.getAttrValue();
		if (value != null && value.length() >= 2) {
			char first = value.charAt(0);
			if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
				value = value.substring(1, value.length() - 1);
			}
		}
		if (attribute.getOperator() == null || value == null) {
			return new AttributeCondition(ConditionType.ATTRIBUTE_CONDITION, name, null);
		}
		switch (attribute.getOperator()) {
		case INCLUDES:
			return new AttributeCondition(ConditionType.ONE_OF_ATTRIBUTE_CONDITION, name, value);
		case DASHMATCH:
			return new AttributeCondition(ConditionType.BEGIN_HYPHEN_ATTRIBUTE_CONDITION, name, value);
		case BEGINMATCH:
			return new AttributeCondition(ConditionType.PREFIX_ATTRIBUTE_CONDITION, name, value);
		case ENDMATCH:
			return new AttributeCondition(ConditionType.SUFFIX_ATTRIBUTE_CONDITION, name, value);
		case CONTAINSMATCH:
			return new AttributeCondition(ConditionType.SUBSTRING_ATTRIBUTE_CONDITION, name, value);
		default:
			return new AttributeCondition(ConditionType.ATTRIBUTE_CONDITION, name, value);
		}
	}
}
