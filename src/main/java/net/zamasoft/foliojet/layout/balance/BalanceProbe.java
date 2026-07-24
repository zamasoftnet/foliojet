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
 * M6c-4でwinnerの実採用(commit)へ切り替え、2026-07-24のcopper3.2
 * 出力互換廃止(ユーザー方針)によりオプトインを撤去して<b>常時有効</b>
 * となった。不適格時の既存balanceへの自動フォールバックが堅牢性の担保。
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
	 * プローブを実行し、winnerが得られればownerへ<b>実採用(commit)</b>
	 * します(M6c-4で観測→採用へ切替、2026-07-24から常時有効)。
	 * {@code AbstractContainerBox.balance()}が既存の容量計算の直後・
	 * owner変異の前に呼び、trueが返ればlegacy再構築を行わない。
	 *
	 * <p>
	 * <b>例外方針</b>: commit開始前(入力凍結・探索)のRuntimeException
	 * は握り潰してlegacyへフォールバックしてよい(候補はliveから完全に
	 * 隔離されており何もcommitしていないため)。commit
	 * ({@code MulticolumnBlockBox.commitBalance})開始後の例外は
	 * 握り潰さず変換全体を中断する——codex設計§1.7。
	 * </p>
	 *
	 * @param owner        バランス対象
	 * @param builder      liveビルダー(RootBuilder到達のみに使う)
	 * @param columnCount  指定段数
	 * @param seedCapacity 現行ColumnBalancerが計算した容量(最初の候補)
	 * @return winnerをownerへcommitした場合true(呼び出し側はlegacy
	 *         balanceを行わない)。不適格・フォールバック時false
	 */
	public static boolean adoptIfEnabled(final AbstractContainerBox owner, final BlockBuilder builder,
			final int columnCount, final double seedCapacity) {
		if (!LayoutExecutionScope.isLive()) {
			// 再入ベルト(プローブ中の候補が更にbalanceへ入っても何もしない
			// ——nested multicolは入力ゲートで除外済みだが、防御的に切る)
			return false;
		}
		final RootBuilder root = builder.getPageContext();
		if (root == null) {
			// rootless文脈(TwoPass系等)はソースログに到達できない
			return false;
		}
		// 2026-07-24: copper3.2出力互換の廃止(ユーザー方針)により
		// オプトイン(processing.balance-probe)を撤去、プローブは常時有効。
		// 不適格時の自動フォールバックが堅牢性の担保。
		final UserAgent ua = root.getPageGenerator().getUserAgent();
		ContinuationStats.recordBalanceProbeSession();
		final MulticolumnBlockBox multicol;
		final BalanceCommit commit;
		try {
			// ページ直下の文脈(builderのrootBoxがPAGE)の段組のみ対象
			// (2026-07-24、常時有効化時に発覚: absolute配置サブツリー内の
			// 段組(rootBox=AbsoluteBlockBox)でプローブ採用がボックスの
			// 描画を失わせる実回帰(_0410_column_width.AbsoluteTest)。
			// 適格条件を狭めてフォールバックさせる——迷ったらフォールバック、
			// の較正どおり。absolute内段組の解禁は必要が生じた時の将来課題)
			if (!(owner instanceof MulticolumnBlockBox m) || !root.isSegmentRestyle()
					|| builder.getRootBox().getPos()
							.getType() != net.zamasoft.foliojet.layout.box.params.PosType.PAGE) {
				ContinuationStats.recordBalanceProbeFallback();
				return false;
			}
			multicol = m;
			final Optional<BalanceProbeInput> input = BalanceProbeInput.capture(
					root.getPageGenerator().getLayoutSource(), owner.getSourceAnchor(), multicol, columnCount, ua);
			if (input.isEmpty()) {
				ContinuationStats.recordBalanceProbeFallback();
				return false;
			}
			ContinuationStats.recordBalanceProbeEligible();
			// commit直前のidentity検証用(§1.2)。プローブはliveへ触れない
			// ため、探索中にこのcontainerが変わることはないはずである
			final net.zamasoft.foliojet.layout.box.content.Container expectedOwnerContainer = multicol.getContainer();
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
				commit = new BalanceCommit(expectedOwnerContainer, winner);
			}
			case Result.Fallback(final String reason, final int builds) -> {
				ContinuationStats.recordBalanceProbeBuilds(builds);
				ContinuationStats.recordBalanceProbeFallback();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("balance probe fallback: " + reason + " (seed=" + seedCapacity + ", builds=" + builds
							+ ")");
				}
				return false;
			}
			}
		} catch (final RuntimeException e) {
			// commit開始前: 何もcommitしていないため、候補構築中の例外で
			// 変換全体を落とさず既存balanceへ安全にフォールバックする
			// (2026-07-24のユーザー較正: クラッシュ排除が最優先)
			ContinuationStats.recordBalanceProbeFallback();
			LOG.log(Level.FINE, "balance probe failed before commit; falling back to the legacy balance", e);
			return false;
		}
		// ---- commit開始: ここから先の例外は握り潰さず変換全体を中断する ----
		multicol.commitBalance(commit);
		ContinuationStats.recordBalanceProbeCommit();
		return true;
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
			// 収まらない: 倍増して最初に収まった候補を上界とする。
			// 下限(MIN_PAGE_LIMIT)未満の容量は物理的に同一の構築になる
			// (BreakableBuilder.getPageLimitの床)ため、倍増は床から始めて
			// 冗長な試行を省く(M6c-4)
			lower = seed;
			double doubled = Math.max(seed,
					net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.MIN_PAGE_LIMIT);
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

		// 上界が下限(20pt床)以下なら、それ以上細かい容量は物理的に区別
		// できない(全て床で組まれる)ため探索を打ち切る(M6c-4)
		while (builds < MAX_PROBES && upper - lower > BalanceCandidate.TOLERANCE
				&& upper > net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder.MIN_PAGE_LIMIT) {
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
