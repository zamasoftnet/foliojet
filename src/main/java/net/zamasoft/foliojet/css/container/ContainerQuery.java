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
 * {@code @container}規則1個ぶんの解析結果です(2026-08-15段3——
 * docs/history/2026-08-15-container-queries-design.md §5/§6)。
 *
 * <p>
 * ph-cssは{@code @container}自体を未知のat-rule({@code CSSUnknownRule})
 * として渡すため、{@link #parse}は{@code getParameterList()}が返す生の
 * 引数文字列(例: {@code "card (min-width: 400px)"})を自前で解釈する。
 * ただし個々の括弧項(例: {@code "(min-width: 400px)"})自体の字句解析は、
 * {@code @media}の特性式として読み直すことでph-cssへ委譲する
 * (設計の「@mediaの特性クエリを流用」)。この委譲により、空白・単位・
 * コロンの字句規則を自前で再実装しない。
 * </p>
 *
 * <p>
 * 第1段階で受理する構文は設計§5のとおり:
 * {@code @container [<name>] (<feature>)[ and (<feature>) ]*}、または
 * {@code @container [<name>] not (<feature>)}。{@code or}・
 * スタイルクエリ・{@code cqw}/{@code cqi}等のコンテナ相対単位・
 * {@code container-type: size}軸の特性は対象外。これらを含む入力は
 * {@link ContainerCondition#isValid()}が{@code false}になる
 * (常に不一致、既存の{@code @media}未対応特性と同じ保守的な扱い)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerQuery {
	/** {@code @container}が受理する特性名(width系とinline-size系は同軸)。 */
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

	/** コンテナ名(名前指定が無ければnull)。 */
	public String getName() {
		return this.name;
	}

	public ContainerCondition getCondition() {
		return this.condition;
	}

	/**
	 * {@code @container}の生引数文字列を解析します。失敗しても例外は
	 * 投げず、{@link ContainerCondition#isValid()}が{@code false}の
	 * (常に不一致の)条件を持つインスタンスを返す。
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
	 * {@code "(a) and (b)"}のような括弧項の並びを分割します。{@code and}
	 * 以外の結合子(未対応の{@code or}等)や閉じ括弧の欠落はnull(解析失敗)。
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
			// "and"以外の残り(未対応の"or"等)
			return null;
		}
		return groups;
	}

	/** 1個の括弧項({@code "(min-width: 400px)"})を@media特性式として読み直す。 */
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
