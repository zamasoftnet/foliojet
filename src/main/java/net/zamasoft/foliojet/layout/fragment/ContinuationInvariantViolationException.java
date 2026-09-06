package net.zamasoft.foliojet.layout.fragment;

/**
 * 継続機構(改ページ・改段の再開)の不変条件が破れたことを示します
 * (2026-07-21新設)。{@code depth}/{@code OpenShape}の整合性、
 * {@code flowStack}の復元結果など、resume完了後に検証すべき構造的な
 * 不変条件の違反はすべてこの例外で表す(実装バグ、または未対応の
 * 継続経路——例えば直交writing-modeの表がOnePass改ページで
 * {@code breakDepth}障壁を迂回するケース——のいずれか)。
 *
 * <p>
 * このチェックはB2で汎用化され、現在は{@link ContinuationValidator}が
 * 検証層を担う。当初は、既に実在する未保護のクラッシュ経路(直交writing-mode表、
 * ChatGPT Pro相談で発見・実測で確認済み)を放置しないため、先行して
 * {@code RootBuilder.pageBreak()}の既存assertをこの例外による無条件
 * チェックへ切り替えた。
 * </p>
 */
public class ContinuationInvariantViolationException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public ContinuationInvariantViolationException(final String message) {
		super(message);
	}
	/** パーサーやformatterが包んだ不変条件違反を取り出す。 */
	public static ContinuationInvariantViolationException findIn(final Throwable failure) {
		final java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof ContinuationInvariantViolationException invariant) return invariant;
		}
		return null;
	}
}
