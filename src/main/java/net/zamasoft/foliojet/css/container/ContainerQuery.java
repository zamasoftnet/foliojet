package net.zamasoft.foliojet.css.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.helger.css.decl.CSSMediaExpression;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;
import com.helger.css.writer.CSSWriterSettings;

import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Parse result for one {@code @container} rule (2026-08-15 stage 3;
 * development record §5/§6).
 *
 * <p>
 * ph-css passes {@code @container} itself as an unknown at-rule ({@code CSSUnknownRule}),
 * so {@link #parse} interprets the raw argument string returned by {@code getParameterList()}
 * (e.g., {@code "card (min-width: 400px)"}) itself.
 * However, it delegates lexical analysis of each parenthesized term
 * (e.g., {@code "(min-width: 400px)"}) to ph-css by reparsing it as a {@code @media}
 * feature expression (the design's "reuse @media feature queries"). This delegation avoids
 * reimplementing lexical rules for whitespace, units, and colons.
 * </p>
 *
 * <p>
 * Phase 1 accepts the syntax specified in design §5:
 * {@code @container [<name>] (<feature>)[ and (<feature>) ]*}, or
 * {@code @container [<name>] not (<feature>)}. {@code or},
 * style queries, container-relative units such as {@code cqw}/{@code cqi},
 * and features on the {@code container-type: size} axis are out of scope. Input containing
 * these makes {@link ContainerCondition#isValid()} return {@code false}
 * (always non-matching, as in the existing conservative treatment of unsupported {@code @media} features).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerQuery {
	/** Feature names accepted by {@code @container} (width and inline-size families share an axis). */
	private static final java.util.Map<String, ContainerFeature.Kind> FEATURE_KINDS = java.util.Map.of( //
			"width", ContainerFeature.Kind.EXACT, //
			"inline-size", ContainerFeature.Kind.EXACT, //
			"min-width", ContainerFeature.Kind.MIN, //
			"min-inline-size", ContainerFeature.Kind.MIN, //
			"max-width", ContainerFeature.Kind.MAX, //
			"max-inline-size", ContainerFeature.Kind.MAX);

	private static final CSSWriterSettings VALUE_WRITER_SETTINGS = new CSSWriterSettings();

	private final String name;

	private final ContainerCondition condition;

	private ContainerQuery(String name, ContainerCondition condition) {
		this.name = name;
		this.condition = condition;
	}

	/** Container name (null if unspecified). */
	public String getName() {
		return this.name;
	}

	public ContainerCondition getCondition() {
		return this.condition;
	}

	/**
	 * Parses a raw {@code @container} argument string. On failure, does not
	 * throw; returns an instance whose condition has {@link ContainerCondition#isValid()}
	 * equal to {@code false} (always non-matching).
	 */
	public static ContainerQuery parse(final String rawParams, final UserAgent ua) {
		if (rawParams == null) {
			return new ContainerQuery(null, ContainerCondition.never());
		}
		String text = rawParams.trim();
		String name = null;
		if (!text.isEmpty() && text.charAt(0) != '(') {
			int j = 0;
			while (j < text.length() && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '(') {
				++j;
			}
			final String head = text.substring(0, j);
			if (!"not".equalsIgnoreCase(head)) {
				name = head;
				text = text.substring(j).trim();
			}
		}
		return new ContainerQuery(name, parseCondition(text, ua));
	}

	private static ContainerCondition parseCondition(String text, final UserAgent ua) {
		if (text.isEmpty()) {
			return ContainerCondition.never();
		}
		boolean negate = false;
		if (text.length() > 3 && text.regionMatches(true, 0, "not", 0, 3)
				&& Character.isWhitespace(text.charAt(3))) {
			negate = true;
			text = text.substring(4).trim();
		}
		final List<String> groups = splitParenGroups(text);
		if (groups == null || groups.isEmpty() || (negate && groups.size() != 1)) {
			return ContainerCondition.never();
		}
		final List<ContainerFeature> features = new ArrayList<>(groups.size());
		for (final String group : groups) {
			final ContainerFeature feature = parseFeature(group, ua);
			if (feature == null) {
				return ContainerCondition.never();
			}
			features.add(feature);
		}
		return negate ? ContainerCondition.not(features.get(0)) : ContainerCondition.and(features);
	}

	/**
	 * Splits a sequence of parenthesized terms such as {@code "(a) and (b)"}. Returns null
	 * (parse failure) for combinators other than {@code and} (e.g., unsupported {@code or}) or missing closing parentheses.
	 */
	private static List<String> splitParenGroups(final String text) {
		final List<String> groups = new ArrayList<>();
		final int n = text.length();
		int i = 0;
		while (i < n) {
			while (i < n && Character.isWhitespace(text.charAt(i))) {
				++i;
			}
			if (i >= n) {
				break;
			}
			if (text.charAt(i) != '(') {
				return null;
			}
			final int start = i;
			int depth = 0;
			while (i < n) {
				final char c = text.charAt(i);
				if (c == '(') {
					++depth;
				} else if (c == ')') {
					--depth;
					if (depth == 0) {
						++i;
						break;
					}
				}
				++i;
			}
			if (depth != 0) {
				return null;
			}
			groups.add(text.substring(start, i));
			while (i < n && Character.isWhitespace(text.charAt(i))) {
				++i;
			}
			if (i >= n) {
				break;
			}
			if (i + 3 <= n && text.regionMatches(true, i, "and", 0, 3)
					&& (i + 3 == n || Character.isWhitespace(text.charAt(i + 3)))) {
				i += 3;
				continue;
			}
			// Remainder other than "and" (unsupported "or", etc.)
			return null;
		}
		return groups;
	}

	/** Reparses one parenthesized term ({@code "(min-width: 400px)"}) as an @media feature expression. */
	private static ContainerFeature parseFeature(final String parenGroup, final UserAgent ua) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CascadingStyleSheet sheet = CSSReader.readFromStringReader("@media " + parenGroup + " {}", settings);
		if (sheet == null || sheet.getRuleCount() != 1) {
			return null;
		}
		final ICSSTopLevelRule rule = sheet.getRuleAtIndex(0);
		if (!(rule instanceof CSSMediaRule mediaRule)) {
			return null;
		}
		final List<CSSMediaQuery> queries = mediaRule.getAllMediaQueries();
		if (queries.size() != 1) {
			return null;
		}
		final CSSMediaQuery query = queries.get(0);
		if (query.getMedium() != null || query.isNot()) {
			return null;
		}
		final List<CSSMediaExpression> expressions = query.getAllMediaExpressions();
		if (expressions.size() != 1) {
			return null;
		}
		return toFeature(expressions.get(0), ua);
	}

	private static ContainerFeature toFeature(final CSSMediaExpression expression, final UserAgent ua) {
		final String feature = expression.getFeature();
		if (feature == null) {
			return null;
		}
		final ContainerFeature.Kind kind = FEATURE_KINDS.get(feature.toLowerCase(Locale.ROOT));
		if (kind == null || expression.getValue() == null) {
			return null;
		}
		final String valueText = expression.getValue().getAsCSSString(VALUE_WRITER_SETTINGS, 0);
		final AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, valueText);
		if (length == null) {
			return null;
		}
		return new ContainerFeature(kind, length.getLength());
	}
}
