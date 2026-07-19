package net.zamasoft.foliojet.css.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * var()カスタムプロパティの参照をトークン列内で置換します(CSS仕様どおりの
 * トークン置換モデル: var(--name)をそのカスタムプロパティの値の生トークン列で
 * 置き換えてから、通常のプロパティ解析を再実行する)。
 */
public final class VarSubstitution {
	private VarSubstitution() {
		// utility
	}

	/**
	 * カスタムプロパティの連鎖(--a: var(--b); --b: var(--c); ...)が異常に
	 * 深い場合の安全弁。循環参照自体はresolving集合で検出するため、これは
	 * 長い非循環チェーンに対する追加の防御({@link Tokens#fromExpression}の
	 * MAX_NESTING_DEPTHと同じ方針)。
	 */
	private static final int MAX_DEPTH = 64;

	/** tokensがvar()呼び出しを(入れ子の関数引数も含め)含むかを返します。 */
	public static boolean containsVarReference(List<CssToken> tokens) {
		for (CssToken token : tokens) {
			if (token instanceof CssToken.Func func) {
				if (func.is("var")) {
					return true;
				}
				if (containsVarReference(func.args())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * tokens内のvar(--name[, fallback])をすべて置換した新しいトークン列を
	 * 返します。参照先が見つからずフォールバックも無い場合、循環参照を検出
	 * した場合、入れ子上限を超えた場合は null(CSS仕様の「使用値計算時に
	 * 無効」に相当——呼び出し側はこの宣言全体を無視すべき)。
	 */
	public static List<CssToken> substitute(List<CssToken> tokens, CSSStyle style) {
		return substitute(tokens, style, Collections.emptySet(), 0);
	}

	private static List<CssToken> substitute(List<CssToken> tokens, CSSStyle style, Set<String> resolving,
			int depth) {
		if (depth > MAX_DEPTH) {
			return null;
		}
		List<CssToken> result = new ArrayList<CssToken>(tokens.size());
		for (CssToken token : tokens) {
			if (token instanceof CssToken.Func func) {
				if (func.is("var")) {
					List<CssToken> resolved = resolveVar(func, style, resolving, depth);
					if (resolved == null) {
						return null;
					}
					result.addAll(resolved);
				} else {
					List<CssToken> substitutedArgs = substitute(func.args(), style, resolving, depth + 1);
					if (substitutedArgs == null) {
						return null;
					}
					result.add(new CssToken.Func(func.name(), substitutedArgs));
				}
			} else {
				result.add(token);
			}
		}
		return result;
	}

	/** var(--name[, fallback...])を解決した置換トークン列を返す。解決できなければnull。 */
	private static List<CssToken> resolveVar(CssToken.Func func, CSSStyle style, Set<String> resolving, int depth) {
		List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)
				|| !nameToken.name().startsWith("--")) {
			// 構文として不正(var()の第1引数はカスタムプロパティ名)
			return null;
		}
		String name = nameToken.name();
		List<CssToken> fallback = null;
		int commaIndex = indexOfComma(args);
		if (commaIndex != -1) {
			fallback = args.subList(commaIndex + 1, args.size());
		}
		if (!resolving.contains(name)) {
			List<CssToken> declared = style.getCustomProperty(name);
			if (declared != null) {
				Set<String> nextResolving = new HashSet<String>(resolving);
				nextResolving.add(name);
				List<CssToken> resolvedDeclared = substitute(declared, style, nextResolving, depth + 1);
				if (resolvedDeclared != null) {
					return resolvedDeclared;
				}
				// 宣言はあったが(循環参照・入れ子上限等で)解決できなかった
				// 場合もフォールバックへ進む(CSS仕様上、循環参照に陥った
				// カスタムプロパティは「未設定」と同じ扱いになる)
			}
		}
		if (fallback != null) {
			return substitute(fallback, style, resolving, depth + 1);
		}
		return null;
	}

	private static int indexOfComma(List<CssToken> args) {
		for (int i = 0; i < args.size(); ++i) {
			if (args.get(i) == CssToken.Op.COMMA) {
				return i;
			}
		}
		return -1;
	}
}
