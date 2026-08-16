package net.zamasoft.foliojet.css.container;

import java.util.Collections;
import java.util.List;

/**
 * {@code @container}の条件です(2026-08-15段3——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 *
 * <p>
 * 文法上、{@code not}は条件全体(単一の括弧項)にしか掛からず、
 * 複数の括弧項は{@code and}でのみ連結できる(仕様上{@code and}と{@code or}
 * は同一階層で混在しない)。{@code or}は第1段階の対象外(§5)。未対応の
 * 構文・特性・単位は{@link #never()}(常に不一致)として保守的に扱う——
 * {@code @media}の未対応特性を不一致とする既存方針
 * ({@link net.zamasoft.foliojet.css.CSSStyleSheetBuilder}
 * の{@code evaluateMediaExpression}参照)と同じ考え方。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerCondition {
	private static final ContainerCondition NEVER = new ContainerCondition(false, false, Collections.emptyList());

	private final boolean valid;

	private final boolean negate;

	private final List<ContainerFeature> features;

	private ContainerCondition(boolean valid, boolean negate, List<ContainerFeature> features) {
		this.valid = valid;
		this.negate = negate;
		this.features = features;
	}

	/** 常に不一致になる条件(未対応の構文・特性・単位、または解析失敗)。 */
	static ContainerCondition never() {
		return NEVER;
	}

	/** {@code (a) and (b) and ...}——1個以上の特性式をANDで連結する。 */
	static ContainerCondition and(List<ContainerFeature> features) {
		return new ContainerCondition(true, false, List.copyOf(features));
	}

	/** {@code not (a)}——単一の特性式を否定する。 */
	static ContainerCondition not(ContainerFeature feature) {
		return new ContainerCondition(true, true, List.of(feature));
	}

	/**
	 * コンテナのused inline-size(pt)に対して条件を評価します。
	 * 解析できなかった条件は常に{@code false}。
	 */
	public boolean evaluate(double inlineSize) {
		if (!this.valid) {
			return false;
		}
		boolean allMatch = true;
		for (final ContainerFeature feature : this.features) {
			if (!feature.matches(inlineSize)) {
				allMatch = false;
				break;
			}
		}
		return this.negate ? !allMatch : allMatch;
	}

	/** 解析に成功した条件か(未対応構文で{@link #never()}になっていないか)。 */
	public boolean isValid() {
		return this.valid;
	}
}
