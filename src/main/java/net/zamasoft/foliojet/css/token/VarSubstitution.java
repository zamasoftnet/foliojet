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
		return containsFunction(tokens, "var");
	}

	/** tokensがenv()呼び出しを(入れ子の関数引数も含め)含むかを返します(2026-08-29)。 */
	public static boolean containsEnvReference(List<CssToken> tokens) {
		return containsFunction(tokens, "env");
	}

	private static boolean containsFunction(List<CssToken> tokens, String name) {
		for (CssToken token : tokens) {
			if (token instanceof CssToken.Func func) {
				if (func.is(name)) {
					return true;
				}
				if (containsFunction(func.args(), name)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * tokens内のenv(&lt;name&gt;[, fallback])をすべて置換した新しいトークン列を
	 * 返します(2026-08-29、css-env-1)。var()と違って要素の文脈に依存しない
	 * ので、宣言解析時(スタイルシート解析、文書全体で1回)に置換できる。
	 * 未知の名前でフォールバックも無い場合は null(宣言全体が無効——
	 * 仕様どおり)。
	 *
	 * <p>
	 * 既知の名前は{@code safe-area-inset-*}(iOSのノッチ回避)と
	 * {@code titlebar-area-*}(PWAのウィンドウ制御)で、紙には該当する
	 * 領域が無いのでいずれも{@code 0px}に解決する。実サイト50件中32件で
	 * {@code max(16px, env(safe-area-inset-right))}のようにcalc()の中に
	 * 現れていた——置換は関数の引数の中まで再帰するので、calc()のRPN列
	 * (Tokens.convertCalc)の葉も同じ経路で置き換わる(1トークン→1トークン
	 * なので後置記法の構造は保たれる)。
	 * </p>
	 */
	public static List<CssToken> substituteEnv(List<CssToken> tokens) {
		return substituteEnv(tokens, 0);
	}

	private static final CssToken ZERO_PX = new CssToken.Dim(0, Unit.PX, "px");

	private static List<CssToken> substituteEnv(List<CssToken> tokens, int depth) {
		if (depth > MAX_DEPTH) {
			return null;
		}
		List<CssToken> result = new ArrayList<CssToken>(tokens.size());
		for (CssToken token : tokens) {
			if (token instanceof CssToken.Func func) {
				if (func.is("env")) {
					List<CssToken> resolved = resolveEnv(func, depth);
					if (resolved == null) {
						return null;
					}
					result.addAll(resolved);
				} else {
					List<CssToken> substitutedArgs = substituteEnv(func.args(), depth + 1);
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

	/** env(name[, fallback...])を解決した置換トークン列を返す。解決できなければnull。 */
	private static List<CssToken> resolveEnv(CssToken.Func func, int depth) {
		List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)) {
			return null;
		}
		final String name = nameToken.lower();
		int commaIndex = indexOfComma(args);
		// safe-area-inset-top のような既知の名前(添字付きのtitlebar-area-x
		// 等も含める)。値は紙では常に0
		if (name.startsWith("safe-area-inset-") || name.startsWith("titlebar-area-")) {
			// 仕様上、既知の名前の後ろに添字(整数)が続く形もあるが、
			// 値はどれも0pxで同じなので読み捨てる
			return Collections.singletonList(ZERO_PX);
		}
		if (commaIndex != -1) {
			return substituteEnv(args.subList(commaIndex + 1, args.size()), depth + 1);
		}
		return null;
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
				} else if (func.is("env")) {
					// カスタムプロパティの値(生トークン列)に書かれたenv()は
					// var()経由でここへ来る(2026-08-29)
					List<CssToken> resolved = resolveEnv(func, depth);
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
			// **宣言した要素の文脈で解決する**(2026-08-03)。カスタム
			// プロパティの計算値は「var()を置換した後のトークン列」であり、
			// 継承より前に計算される(CSS Variables 1)。祖先で
			// `--y: calc(var(--x) + 1px)` と書き、子で `--x` だけ変えても、
			// 継承した `--y` は**祖先の** `--x` で計算された値のままになる。
			// 従来は現在の要素で解決していたため、子で再評価されていた
			final CSSStyle owner = style.getCustomPropertyOwner(name);
			List<CssToken> declared = owner == null ? null : owner.getCustomProperty(name);
			if (declared != null) {
				Set<String> nextResolving = new HashSet<String>(resolving);
				nextResolving.add(name);
				List<CssToken> resolvedDeclared = substitute(declared, owner, nextResolving, depth + 1);
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
