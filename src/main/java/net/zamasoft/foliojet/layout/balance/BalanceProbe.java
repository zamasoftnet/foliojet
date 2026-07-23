package net.zamasoft.foliojet.layout.balance;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutExecutionScope;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * M6cバランスプローブの探索ドライバです(2026-07-24新設、排除域P2の
 * M6c-3——codex設計§1.7)。
 *
 * <p>
 * 上界は{@code 1e6}のような任意上限ではなく実測で作る: 現行
 * {@code ColumnBalancer}の容量を最初の候補とし、収まらなければ倍増、
 * 収まれば下界0として二分探索する。成功候補の実測{@code maxUsed}へ
 * 上界をスナップし、最大{@value #MAX_PROBES}回・
 * {@value BalanceCandidate#TOLERANCE}ptで打ち切る。最小の成功候補
 * <b>そのもの</b>を保持し、最終再buildはしない。容量とfitの単調性に
 * 反する観測が出た場合は推論で押し切らず既存balanceへフォールバックする。
 * </p>
 *
 * <p>
 * M6c-3時点の配線はオプトイン({@code processing.balance-probe}、
 * 既定false)の<b>観測のみ</b>——winnerはまだownerへcommitせず、
 * 既存balanceがそのまま走る(既定では一切実行されない=挙動不変)。
 * commit切替はM6c-4。
 * </p>
 */
public final class BalanceProbe {

	private static final Logger LOG = Logger.getLogger(BalanceProbe.class.getName());

	/** 候補構築の総回数上限です(倍増+二分探索の合計)。 */
	public static final int MAX_PROBES = 20;

	private BalanceProbe() {
		// driver
	}

	/**
	 * 候補の供給者です(通常は{@code BalanceProbeSession::build})。
	 * 探索の反復上限・フォールバックを実セッションなしで単体テストする
	 * ための継ぎ目。
	 */
	@FunctionalInterface
	public interface CandidateSource {
		BalanceCandidate build(double capacity);
	}

	/** 探索の結果です。 */
	public sealed interface Result {
		/**
		 * 最小の成功候補です。{@code winner.committedCapacity()}が実測の
		 * 最小容量(内容境界へスナップ済み)。
		 *
		 * @param winner 成功候補
		 * @param builds 構築した候補数
		 */
		record Winner(BalanceCandidate winner, int builds) implements Result {
		}

		/**
		 * フォールバックです(上界が見つからない・非単調観測)。
		 *
		 * @param reason 理由(診断用)
		 * @param builds 構築した候補数
		 */
		record Fallback(String reason, int builds) implements Result {
		}
	}

	/**
	 * オプトインが有効な場合のみプローブを実行し、結果を観測します
	 * (M6c-3: commitしない。既定falseでは何もしない=挙動不変)。
	 * {@code AbstractContainerBox.balance()}が既存の容量計算の直後・
	 * owner変異の前に呼ぶ。
	 *
	 * @param owner        バランス対象
	 * @param builder      liveビルダー(RootBuilder到達のみに使う)
	 * @param columnCount  指定段数
	 * @param seedCapacity 現行ColumnBalancerが計算した容量(最初の候補)
	 */
	public static void observeIfEnabled(final AbstractContainerBox owner, final BlockBuilder builder,
			final int columnCount, final double seedCapacity) {
		if (!LayoutExecutionScope.isLive()) {
			// 再入ベルト(プローブ中の候補が更にbalanceへ入っても何もしない
			// ——nested multicolは入力ゲートで除外済みだが、防御的に切る)
			return;
		}
		final RootBuilder root = builder.getPageContext();
		if (root == null) {
			// rootless文脈(TwoPass系等)はソースログに到達できない
			return;
		}
		final UserAgent ua = root.getPageGenerator().getUserAgent();
		if (!UAProps.PROCESSING_BALANCE_PROBE.getBoolean(ua)) {
			return;
		}
		ContinuationStats.recordBalanceProbeSession();
		try {
			if (!(owner instanceof MulticolumnBlockBox multicol) || !root.isSegmentRestyle()) {
				ContinuationStats.recordBalanceProbeFallback();
				return;
			}
			final Optional<BalanceProbeInput> input = BalanceProbeInput.capture(
					root.getPageGenerator().getLayoutSource(), owner.getSourceAnchor(), multicol, columnCount, ua);
			if (input.isEmpty()) {
				ContinuationStats.recordBalanceProbeFallback();
				return;
			}
			ContinuationStats.recordBalanceProbeEligible();
			final BalanceProbeSession session = new BalanceProbeSession(input.get());
			final Result result = search(session::build, columnCount, seedCapacity);
			switch (result) {
			case Result.Winner(final BalanceCandidate winner, final int builds) -> {
				ContinuationStats.recordBalanceProbeBuilds(builds);
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("balance probe winner: columns=" + winner.actualColumns() + "/" + columnCount
							+ " capacity=" + winner.committedCapacity() + " (requested=" + winner.requestedCapacity()
							+ ", seed=" + seedCapacity + ", builds=" + builds + ", used=" + winner.usedExtents() + ")");
				}
				// M6c-3: winnerはここで破棄する(ownerへのcommitはM6c-4)。
				// live側はcandidateへの参照を一切保持しない——メソッドを
				// 抜けた時点で候補一式はGC可能
			}
			case Result.Fallback(final String reason, final int builds) -> {
				ContinuationStats.recordBalanceProbeBuilds(builds);
				ContinuationStats.recordBalanceProbeFallback();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("balance probe fallback: " + reason + " (seed=" + seedCapacity + ", builds=" + builds
							+ ")");
				}
			}
			}
		} catch (final RuntimeException e) {
			// M6c-3は観測のみ(何もcommitしていない)ため、候補構築中の例外で
			// 変換全体を落とさず既存balanceへ安全にフォールバックする
			// (2026-07-24のユーザー較正: クラッシュ排除が最優先。M6c-4で
			// commitを始めたら、commit開始後の例外はここで握り潰しては
			// いけない——変換全体を中断する。codex設計§1.7)
			ContinuationStats.recordBalanceProbeFallback();
			LOG.log(Level.FINE, "balance probe failed; falling back to the legacy balance", e);
		}
	}

	/**
	 * 実測二分探索です(§1.7)。
	 *
	 * @param session      候補の供給者(通常は{@code BalanceProbeSession::build})
	 * @param columnCount  指定段数
	 * @param seedCapacity 最初の試行容量(現行ColumnBalancerの計算値)
	 * @return 最小成功候補、またはフォールバック
	 */
	public static Result search(final CandidateSource session, final int columnCount, final double seedCapacity) {
		final double seed = Math.max(seedCapacity, 0);
		int builds = 0;

		BalanceCandidate candidate = session.build(seed);
		++builds;

		double lower = 0;
		double upper;
		BalanceCandidate best;
		if (candidate.fits(columnCount)) {
			best = candidate;
			upper = Math.min(seed, candidate.committedCapacity());
		} else {
			// 収まらない: 倍増して最初に収まった候補を上界とする
			lower = seed;
			double doubled = Math.max(seed, 1);
			best = null;
			while (best == null && builds < MAX_PROBES) {
				doubled *= 2;
				candidate = session.build(doubled);
				++builds;
				if (candidate.fits(columnCount)) {
					best = candidate;
				} else {
					lower = doubled;
				}
			}
			if (best == null) {
				return new Result.Fallback("no fitting upper bound within " + MAX_PROBES + " probes", builds);
			}
			upper = Math.min(doubled, best.committedCapacity());
		}
		if (upper < lower - BalanceCandidate.TOLERANCE) {
			// 成功候補の実測maxUsedが既知の失敗容量を下回った: 非単調
			return new Result.Fallback("non-monotonic: fitting maxUsed " + upper + " below failing capacity " + lower,
					builds);
		}

		while (builds < MAX_PROBES && upper - lower > BalanceCandidate.TOLERANCE) {
			final double mid = (lower + upper) / 2;
			candidate = session.build(mid);
			++builds;
			if (candidate.fits(columnCount)) {
				final double snapped = Math.min(mid, candidate.committedCapacity());
				if (snapped < lower - BalanceCandidate.TOLERANCE) {
					return new Result.Fallback(
							"non-monotonic: fitting maxUsed " + snapped + " below failing capacity " + lower, builds);
				}
				best = candidate;
				upper = snapped;
			} else {
				lower = mid;
			}
		}
		return new Result.Winner(best, builds);
	}
}
