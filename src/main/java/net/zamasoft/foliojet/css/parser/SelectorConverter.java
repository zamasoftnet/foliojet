package net.zamasoft.foliojet.css.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSSelectorAttribute;
import com.helger.css.decl.CSSSelectorMemberFunctionLike;
import com.helger.css.decl.CSSSelectorMemberNot;
import com.helger.css.decl.CSSSelectorMemberPseudoIs;
import com.helger.css.decl.CSSSelectorMemberPseudoWhere;
import com.helger.css.decl.CSSSelectorSimpleMember;
import com.helger.css.decl.ECSSSelectorCombinator;
import com.helger.css.decl.ICSSSelectorMember;
import com.helger.css.writer.CSSWriterSettings;

import net.zamasoft.foliojet.css.selector.AttributeCondition;
import net.zamasoft.foliojet.css.selector.CombinatorSelector;
import net.zamasoft.foliojet.css.selector.Condition;
import net.zamasoft.foliojet.css.selector.Condition.ConditionType;
import net.zamasoft.foliojet.css.selector.ElementSelector;
import net.zamasoft.foliojet.css.selector.PseudoElementSelector;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.selector.Selector.SelectorType;
import net.zamasoft.foliojet.css.selector.SelectorListCondition;
import net.zamasoft.foliojet.css.selector.SimpleSelector;
import net.zamasoft.foliojet.css.selector.ValueCondition;

/**
 * ph-css のセレクタ構文木を内部セレクタモデルに変換します。
 */
public final class SelectorConverter {
	private static final Logger LOG = Logger.getLogger(SelectorConverter.class.getName());

	private static final CSSWriterSettings WRITER_SETTINGS = new CSSWriterSettings();

	private SelectorConverter() {
		// utility
	}

	/**
	 * CSS2.1で単一コロン記法が許される擬似要素。
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

		for (ICSSSelectorMember member : selector.getAllMembers()) {
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
					conditions.add(new ValueCondition(ConditionType.ID_CONDITION, value.substring(1)));
				} else if (simple.isClass()) {
					conditions.add(new ValueCondition(ConditionType.CLASS_CONDITION, value.substring(1)));
				} else if (simple.isPseudo()) {
					if (value.startsWith("::")) {
						pseudoElement = value.substring(2);
					} else {
						String name = value.substring(1);
						if (isLegacyPseudoElement(name)) {
							pseudoElement = name;
						} else {
							conditions.add(new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION, name));
						}
					}
				} else {
					// 要素名(名前空間プレフィクスは無視する)
					String name = value;
					int bar = name.lastIndexOf('|');
					if (bar != -1) {
						name = name.substring(bar + 1);
					}
					if (!name.equals("*")) {
						localName = name;
					}
				}
			} else if (member instanceof CSSSelectorAttribute) {
				conditions.add(convertAttribute((CSSSelectorAttribute) member));
			} else if (member instanceof CSSSelectorMemberNot) {
				conditions.add(new SelectorListCondition(ConditionType.NOT_CONDITION,
						convertList(((CSSSelectorMemberNot) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoIs) {
				conditions.add(new SelectorListCondition(ConditionType.IS_CONDITION,
						convertList(((CSSSelectorMemberPseudoIs) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoWhere) {
				conditions.add(new SelectorListCondition(ConditionType.IS_CONDITION,
						convertList(((CSSSelectorMemberPseudoWhere) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberFunctionLike) {
				CSSSelectorMemberFunctionLike function = (CSSSelectorMemberFunctionLike) member;
				String name = function.getFunctionName();
				// 形式は ":lang(" のように先頭コロン+末尾括弧
				name = name.substring(name.startsWith("::") ? 2 : 1, name.length() - 1);
				String param = function.getParameterExpression().getAsCSSString(WRITER_SETTINGS, 0);
				if (name.equalsIgnoreCase("lang")) {
					conditions.add(new ValueCondition(ConditionType.LANG_CONDITION, param));
				} else {
					// 未対応の関数型擬似クラス(nth-child等)は不一致条件として扱う
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

	public static List<Selector> convertList(List<CSSSelector> selectors) throws CSSException {
		List<Selector> result = new ArrayList<Selector>(selectors.size());
		for (CSSSelector selector : selectors) {
			result.add(convert(selector));
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
			// 旧モデル互換: 擬似要素は子孫結合として表現する
			result = new CombinatorSelector(SelectorType.DESCENDANT_SELECTOR, result,
					new PseudoElementSelector(pseudoElement));
		}
		return result;
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
