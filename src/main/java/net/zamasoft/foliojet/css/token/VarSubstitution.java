package net.zamasoft.foliojet.css.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * Substitutes var() custom property references in token sequences (the CSS-specified
 * token substitution model: replace var(--name) with the custom property's raw value
 * tokens, then rerun normal property parsing).
 */
public final class VarSubstitution {
	private VarSubstitution() {
		// utility
	}

	/**
	 * Safeguard for abnormally deep custom property chains
	 * (--a: var(--b); --b: var(--c); ...). The resolving set detects cycles themselves;
	 * this additionally protects against long acyclic chains (same policy as
	 * MAX_NESTING_DEPTH in {@link Tokens#fromExpression}).
	 */
	private static final int MAX_DEPTH = 64;

	/** Returns whether tokens contain a var() call (including nested function arguments). */
	public static boolean containsVarReference(List<CssToken> tokens) {
		return containsFunction(tokens, "var");
	}

	/** Returns whether tokens contain an env() call (including nested function arguments; 2026-08-29). */
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
	 * Returns a new token sequence with every env(&lt;name&gt;[, fallback]) substituted
	 * (2026-08-29, css-env-1). Unlike var(), it does not depend on element context, so
	 * substitution can occur during declaration parsing (once per document when parsing
	 * the stylesheet). An unknown name with no fallback yields null (invalidates the
	 * whole declaration, as specified).
	 *
	 * <p>
	 * Known names are {@code safe-area-inset-*} (iOS notch avoidance) and
	 * {@code titlebar-area-*} (PWA window controls). Paper has no corresponding regions,
	 * so all resolve to {@code 0px}. They appeared inside calc(), e.g.
	 * {@code max(16px, env(safe-area-inset-right))}, on 32 of 50 real sites.
	 * Substitution recurses into function arguments, so leaves in calc() RPN sequences
	 * (Tokens.convertCalc) are replaced through the same path (one token to one token,
	 * preserving postfix structure).
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

	/** Returns replacement tokens after resolving env(name[, fallback...]), or null if resolution fails. */
	private static List<CssToken> resolveEnv(CssToken.Func func, int depth) {
		List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)) {
			return null;
		}
		final String name = nameToken.lower();
		int commaIndex = indexOfComma(args);
		// Known names such as safe-area-inset-top (including indexed names such as
		// titlebar-area-x). Values are always 0 on paper.
		if (name.startsWith("safe-area-inset-") || name.startsWith("titlebar-area-")) {
			// The specification allows integer indexes after known names, but
			// all values are 0px, so read and discard the indexes.
			return Collections.singletonList(ZERO_PX);
		}
		if (commaIndex != -1) {
			return substituteEnv(args.subList(commaIndex + 1, args.size()), depth + 1);
		}
		return null;
	}

	/**
	 * Returns a new token sequence with every var(--name[, fallback]) substituted.
	 * Returns null if a reference is missing with no fallback, a cycle is detected, or
	 * the nesting limit is exceeded (equivalent to CSS invalid at computed-value time:
	 * the caller should ignore this entire declaration).
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
					// env() in custom property values (raw tokens) reaches here
					// through var() (2026-08-29).
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

	/** Returns replacement tokens after resolving var(--name[, fallback...]), or null if resolution fails. */
	private static List<CssToken> resolveVar(CssToken.Func func, CSSStyle style, Set<String> resolving, int depth) {
		List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)
				|| !nameToken.name().startsWith("--")) {
			// Invalid syntax (var()'s first argument must be a custom property name)
			return null;
		}
		String name = nameToken.name();
		List<CssToken> fallback = null;
		int commaIndex = indexOfComma(args);
		if (commaIndex != -1) {
			fallback = args.subList(commaIndex + 1, args.size());
		}
		if (!resolving.contains(name)) {
			// **Resolve in the declaring element's context** (2026-08-03). The computed
			// value of a custom property is its token sequence after var() substitution,
			// computed before inheritance (CSS Variables 1). If an ancestor declares
			// `--y: calc(var(--x) + 1px)` and a child changes only `--x`, inherited `--y`
			// still holds the value computed using **the ancestor's** `--x`.
			// Previously resolution used the current element, reevaluating it on the child.
			final CSSStyle owner = style.getCustomPropertyOwner(name);
			List<CssToken> declared = owner == null ? null : owner.getCustomProperty(name);
			if (declared != null) {
				Set<String> nextResolving = new HashSet<String>(resolving);
				nextResolving.add(name);
				List<CssToken> resolvedDeclared = substitute(declared, owner, nextResolving, depth + 1);
				if (resolvedDeclared != null) {
					return resolvedDeclared;
				}
				// Also use the fallback if a declaration exists but cannot be resolved
				// (cycle, nesting limit, etc.). The CSS specification treats custom properties
				// involved in a cycle as unset.
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
