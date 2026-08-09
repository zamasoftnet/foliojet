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
 * ph-css のセレクタ構文木を内部セレクタモデルに変換します。
 */
public final class SelectorConverter {
	private static final Logger LOG = Logger.getLogger(SelectorConverter.class.getName());

	private static final CSSWriterSettings WRITER_SETTINGS = new CSSWriterSettings();

	private SelectorConverter() {
		// utility
	}

	/**
	 * CSSの識別子エスケープ({@code \X}・{@code \XXXXXX}形式)を復号します。
	 *
	 * <p>
	 * <b>ph-cssの{@code CSSSelectorSimpleMember.getValue()}はセレクタの生の
	 * 文字列をそのまま返し、識別子エスケープを解決しない</b>(2026-08-06、
	 * 実物大コーパスのTailwind CSS——`hover:`・`lg:`・`[&_svg]:size-4`のような
	 * 状態/レスポンシブ/アリビトラリのバリアント接頭辞をコロン・角括弧・
	 * アンパサンド等の逆スラッシュエスケープでクラス名にした形——で発覚)。
	 * 一方HTMLの{@code class}属性値にエスケープは無い(素の文字列)ため、
	 * 逆スラッシュを残したまま比較すると{@code ce.isStyleClass()}が常に
	 * 不一致になり、該当クラスに紐づく宣言(width/height・visibility等)が
	 * 一つも適用されなかった。クラス名・ID名だけでなく要素名・擬似クラス名
	 * にも同じ理屈が及ぶため、値を取り出す全箇所で復号する。
	 * </p>
	 */
	private static String unescapeCssIdent(String s) {
		if (s.indexOf('\\') < 0) {
			return s;
		}
		StringBuilder buf = new StringBuilder(s.length());
		int i = 0;
		int len = s.length();
		while (i < len) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < len) {
				char next = s.charAt(i + 1);
				if (isHexDigit(next)) {
					int start = i + 1;
					int end = start;
					while (end < len && end < start + 6 && isHexDigit(s.charAt(end))) {
						++end;
					}
					int codePoint = Integer.parseInt(s.substring(start, end), 16);
					buf.appendCodePoint(codePoint);
					i = end;
					// 16進エスケープ直後の単一の空白は区切りとして消費される(CSS Syntax仕様)
					if (i < len && Character.isWhitespace(s.charAt(i))) {
						++i;
					}
				} else {
					buf.append(next);
					i += 2;
				}
			} else {
				buf.append(c);
				++i;
			}
		}
		return buf.toString();
	}

	private static boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/**
	 * 妥当な(=セレクタリストを無効化しない)擬似クラス名の台帳です。
	 *
	 * <p>
	 * CSSでは<b>無効なセレクタを1つでも含むセレクタリストは規則ごと
	 * 破棄される</b>。未知の擬似クラスを「決して一致しない条件」として
	 * 受理してしまうと、この仕様に依存したブラウザ判別ハック
	 * ({@code _::-webkit-full-page-media, _:future, :root body .foo}の
	 * ようなSafari専用規則)がCopperPDFにだけ適用されてしまう
	 * (pc.watch.impress.co.jpで実測、2026-08-09)。ここに載っている名前は
	 * 「解釈はしないが規則は殺さない」(インタラクティブ系など)、
	 * 載っていない名前は規則ごと無効(Chromeと同じ側に倒す)。
	 * </p>
	 */
	private static final java.util.Set<String> VALID_PSEUDO_CLASSES = java.util.Set.of(
			// リンク・ユーザー操作系(印刷では一致しないが妥当)
			"link", "visited", "any-link", "local-link", "hover", "active", "focus", "focus-visible",
			"focus-within", "target", "target-within", "scope",
			// 構造系
			"root", "empty", "first-child", "last-child", "only-child", "first-of-type", "last-of-type",
			"only-of-type", "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type",
			// 論理コンビネータ
			"is", "where", "not", "has",
			// 入力状態系
			"enabled", "disabled", "checked", "indeterminate", "default", "placeholder-shown", "autofill",
			"read-only", "read-write", "required", "optional", "valid", "invalid", "user-valid",
			"user-invalid", "in-range", "out-of-range", "blank",
			// 言語・方向
			"lang", "dir",
			// 表示状態系
			"defined", "fullscreen", "modal", "picture-in-picture", "popover-open", "open", "closed",
			// ページ(@page文脈だが要素側に現れても殺さない)
			"first", "left", "right",
			// Shadow DOM(未対応だが妥当な構文)
			"host", "host-context",
			// Chromeが受理する-webkit-系(実サイトで使われる代表)
			"-webkit-any", "-webkit-autofill", "-webkit-full-screen");

	/**
	 * 妥当な擬似要素名の台帳です({@link #VALID_PSEUDO_CLASSES}と同じ趣旨)。
	 * {@code -webkit-}接頭辞の擬似要素は名前を問わず妥当(一致しない)
	 * ——CSS仕様がウェブ互換のために明文で認める特例で、Chromeも同じ。
	 * だからこそSafariハックは{@code ::-webkit-full-page-media}(どの
	 * ブラウザでも規則を殺さない)と{@code :future}(Safari以外は規則ごと
	 * 無効)の組で書かれる。
	 */
	private static final java.util.Set<String> VALID_PSEUDO_ELEMENTS = java.util.Set.of(
			"before", "after", "first-line", "first-letter", "marker", "selection", "placeholder",
			"backdrop", "cue", "cue-region", "file-selector-button", "details-content", "target-text",
			"spelling-error", "grammar-error", "highlight", "part", "slotted",
			// GCPM脚注(CopperPDFが実装)
			"footnote-call", "footnote-marker");

	/**
	 * 未知の擬似クラス/擬似要素なら{@link CSSException}を投げます
	 * (呼び出し側のセレクタリスト処理が規則ごと破棄する)。
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
		// ph-css 8.2.1は:has(...)をCSSSelectorMemberPseudoHasとして正しく
		// 解析するが、その直後に引数と同数の生CSSSelector(それ自体が
		// ICSSSelectorMemberを実装しており、素のままメンバー列に現れる)を
		// 重複して並べてくる(2026-07-19実測で確認。おそらくph-css内部の
		// 別解析経路の名残)。これを無視しないと、下の「未対応メンバー」
		// 分岐で常に不一致の条件が余分に追加され、:has()を含むコンパウンド
		// 全体が常に不一致になってしまう。そのためCSSSelectorMemberPseudoHas
		// を処理した直後、引数の個数分だけ後続の生CSSSelectorメンバーを
		// 読み飛ばす。
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
						if (isLegacyPseudoElement(name)) {
							pseudoElement = name;
						} else if (name.equalsIgnoreCase("first-of-type")) {
							// :first-of-type は :nth-of-type(1) と等価
							conditions.add(new NthCondition(ConditionType.NTH_OF_TYPE_CONDITION, 0, 1, "1"));
						} else if (name.equalsIgnoreCase("last-child")) {
							// STRUCTURE_SCANで解決(docs/PLAN.md「2パス制御モード」参照)
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
							// ph-css 8.2.1 は :nth-child()/:nth-of-type() を
							// CSSSelectorMemberFunctionLike ではなく、括弧を含む
							// 一つの単純セレクタ文字列(例: "nth-child(odd)")として
							// 渡す(:lang()/:dir() は FunctionLike になるのに対し
							// 非対称。2026-07-18 実測で確認)。関数呼び出しの形を
							// していればここで検出する
							Condition functional = tryConvertFunctionalPseudo(name);
							if (functional == null) {
								requireValidPseudoClass(name);
							}
							conditions.add(functional != null ? functional
									: new ValueCondition(ConditionType.PSEUDO_CLASS_CONDITION, name));
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
						localName = unescapeCssIdent(name);
					}
				}
			} else if (member instanceof CSSSelectorAttribute) {
				conditions.add(convertAttribute((CSSSelectorAttribute) member));
			} else if (member instanceof CSSSelectorMemberNot) {
				conditions.add(new SelectorListCondition(ConditionType.NOT_CONDITION,
						convertList(((CSSSelectorMemberNot) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoIs) {
				// :is()の引数はforgivingリスト(無効なセレクタはその引数だけ
				// 落とし、規則は無効化しない。CSS Selectors 4)
				conditions.add(new SelectorListCondition(ConditionType.IS_CONDITION,
						convertForgivingList(((CSSSelectorMemberPseudoIs) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoWhere) {
				// :where()は:is()とマッチング判定は同じだが、詳細度は常にゼロ
				// (CSS Selectors4仕様)。別のConditionTypeで区別する
				// (SelectorListCondition.getSpecificity参照)。
				// 引数は:is()と同じforgivingリスト
				conditions.add(new SelectorListCondition(ConditionType.WHERE_CONDITION,
						convertForgivingList(((CSSSelectorMemberPseudoWhere) member).getAllSelectors())));
			} else if (member instanceof CSSSelectorMemberPseudoHas) {
				List<CSSSelector> hasArgs = ((CSSSelectorMemberPseudoHas) member).getAllSelectors();
				List<Selector> relativeSelectors = new ArrayList<Selector>(hasArgs.size());
				for (CSSSelector hasArg : hasArgs) {
					relativeSelectors.add(convert(unwrapHasArgument(hasArg)));
				}
				conditions.add(new SelectorListCondition(ConditionType.HAS_CONDITION, relativeSelectors));
				// 直後に続く重複した生CSSSelectorメンバーを読み飛ばす(上のコメント参照)
				skipDuplicateHasSelectors = hasArgs.size();
			} else if (member instanceof CSSSelectorMemberFunctionLike) {
				CSSSelectorMemberFunctionLike function = (CSSSelectorMemberFunctionLike) member;
				String name = function.getFunctionName();
				// 形式は ":lang(" のように先頭コロン+末尾括弧
				name = name.substring(name.startsWith("::") ? 2 : 1, name.length() - 1);
				String param = function.getParameterExpression().getAsCSSString(WRITER_SETTINGS, 0);
				if (name.equalsIgnoreCase("lang")) {
					conditions.add(new ValueCondition(ConditionType.LANG_CONDITION, param));
				} else if (name.equalsIgnoreCase("dir")) {
					conditions.add(new ValueCondition(ConditionType.DIR_CONDITION, param.trim()));
				} else {
					// 未対応の関数型擬似クラス(nth-last-child等)は不一致条件として扱う。
					// nth-child/nth-of-type はここに来ない(ph-cssの実装上
					// CSSSelectorSimpleMember側で処理される。上のisPseudo()分岐参照)
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
	 * ph-css 8.2.1は:has()のNestedSelectorsの各要素を、実際のセレクタを
	 * 単一メンバーとしてもう一段CSSSelectorで包んだ形で返す
	 * (:not()/:is()/:where()には無い:has()固有の非対称。2026-07-19実測で
	 * 確認)。そのため単一メンバーがCSSSelectorである間はそれを剥がし、
	 * 実際のセレクタに到達させる(構文木由来の有限な深さのみを辿る反復。
	 * 再帰は使わない)。
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
	 * forgivingセレクタリスト({@code :is()}/{@code :where()}の引数)の変換です。
	 * 解釈できないセレクタはその項だけ落とし、例外は投げません。
	 */
	static List<Selector> convertForgivingList(List<CSSSelector> selectors) {
		List<Selector> result = new ArrayList<Selector>(selectors.size());
		for (CSSSelector selector : selectors) {
			try {
				result.add(convert(selector));
			} catch (CSSException e) {
				// forgiving: この項だけ無かったことにする
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
			// 旧モデル互換: 擬似要素は子孫結合として表現する
			result = new CombinatorSelector(SelectorType.DESCENDANT_SELECTOR, result,
					new PseudoElementSelector(pseudoElement));
		}
		return result;
	}

	/**
	 * ph-cssがCSSSelectorSimpleMemberの生文字列として渡す関数型擬似クラス
	 * (実測: nth-child()/nth-of-type())を検出・解析します。関数呼び出しの
	 * 形をしていなければ、あるいは対応する関数名でなければ null を返します。
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
			// STRUCTURE_SCANで解決(docs/PLAN.md「2パス制御モード」参照)
			return convertNth(ConditionType.NTH_LAST_CHILD_CONDITION, fname, param);
		}
		if (fname.equalsIgnoreCase("nth-last-of-type")) {
			return convertNth(ConditionType.NTH_LAST_OF_TYPE_CONDITION, fname, param);
		}
		return null;
	}

	/**
	 * :nth-child() / :nth-of-type() の引数(An+B構文)を解析します。
	 * 解析できない場合は未対応セレクタとして警告し、常に不一致になる
	 * 条件を返します(2026-07 方針: 未知のセレクタは例外にせず不一致継続)。
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
	 * An+B構文(odd / even / 整数 / an+b)を解析します。反復(文字走査)のみで
	 * 完結し再帰は使いません。解析できなければ null を返します。
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
			// "n" を含まない: 整数のみ(a=0)
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
	 * 符号付き整数を解析します(符号と数字の間の空白も許容: "+ 3" 等)。
	 * 解析できなければ null。
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
