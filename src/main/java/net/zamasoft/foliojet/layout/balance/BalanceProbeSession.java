package net.zamasoft.foliojet.layout.balance;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.fragment.LayoutExecutionScope;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;

/**
 * M6cバランスプローブの候補構築セッションです(2026-07-24新設、
 * 排除域P2のM6c-2——codex設計§1.3/1.5)。
 *
 * <p>
 * {@link #build(double)}は各試行容量ごとに<b>完全な新品</b>
 * (候補shell・{@code FlowContainer}・rootless builder・
 * {@code DocumentBuilder}・全子box・置換box・テキスト整形状態)を組む。
 * live側は入力の凍結({@link BalanceProbeInput})時点で読み終えており、
 * 構築中に一切触れない——losing candidateを何個作ってもliveは不変。
 * </p>
 *
 * <p>
 * 外側floatの排除域はcandidateへコピーしない——外側floatの効果はlive
 * 構築時に{@code snapshotExclusions()}からmulticolの実幅へ既に反映済みで、
 * ここで再投入すると二重回避になる(§1.3)。段組内floatはM6c-5
 * (2026-07-24)で解禁——候補builder(新品のfloatings台帳)内で通常
 * どおり配置され、P1の{@code commitFloatPlacement}が候補内に閉じる。
 * </p>
 *
 * <p>
 * 構築全体は{@link LayoutExecutionScope#enterBalanceProbe()}で包み、
 * 捨てる可能性のある試行がproduction診断
 * ({@code ContinuationStats}等)を汚さないようにする(§1.6)。
 * </p>
 */
public final class BalanceProbeSession {

	private final BalanceProbeInput input;

	public BalanceProbeSession(final BalanceProbeInput input) {
		this.input = input;
	}

	/**
	 * 試行容量{@code capacity}で候補を新品構築し、観測結果を返します。
	 *
	 * <p>
	 * 候補中のRuntimeExceptionはここでは握り潰さない(呼び出し側も
	 * legacy再実行へrollbackしてはいけない——§1.7)。
	 * </p>
	 *
	 * @param capacity ページ方向の試行容量
	 * @return 観測済み候補
	 */
	public BalanceCandidate build(final double capacity) {
		try (LayoutExecutionScope.Scope scope = LayoutExecutionScope.enterBalanceProbe()) {
			final MulticolumnBlockBox shell = MulticolumnBlockBox.newBalanceProbeShell(this.input.ownerGeometry(),
					capacity);
			final BalanceProbeBuilder builder = new BalanceProbeBuilder(shell);
			final DocumentBuilder doc = new DocumentBuilder(new ProbePageGenerator(this.input.ua()), builder);
			// SourceReplayer.driveと同じく、再生インスタンスへイベントIDから
			// ソースアンカーを再付与する(凍結列はsliceとordinal 1:1)——
			// winnerが実採用(M6c-4)された後の改ページ破断でも、子部分木の
			// ソース再生系譜が保たれる
			long eventId = this.input.source().fromId();
			for (final SegmentEvent event : this.input.frozenEvents()) {
				switch (event) {
				case SegmentEvent.BeginBox(final BoxRecipe recipe) -> {
					final net.zamasoft.foliojet.layout.box.INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
					box.setSourceAnchor(eventId);
					doc.startBox(box);
				}
				case SegmentEvent.EndBox end -> doc.endBox();
				case SegmentEvent.Text(final int sourceOffset, final String text, final boolean fixed) -> {
					final char[] ch = text.toCharArray();
					doc.characters(sourceOffset, ch, 0, ch.length, fixed);
				}
				case SegmentEvent.Replaced(final ReplacedRecipe recipe) -> {
					final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box = BoxRecipeBoxFactory
							.createReplaced(recipe);
					box.setSourceAnchor(eventId);
					doc.addReplacedBox(box);
				}
				// 入力凍結時にBarrierゼロを検証済み(BalanceProbeInput.capture)
				case SegmentEvent.Barrier barrier -> throw new IllegalStateException(
						"barrier event in balance probe input: " + barrier);
				}
				++eventId;
			}
			doc.finishReplay();
			return observe(shell, capacity);
		}
	}

	/** 構築済みshellのカラム構成を観測します(実カラム数と実測extentのみ)。 */
	private static BalanceCandidate observe(final MulticolumnBlockBox shell, final double capacity) {
		final Container container = shell.getContainer();
		final List<Double> usedExtents = new ArrayList<>();
		if (container instanceof ColumnsContainer columns) {
			for (int i = 0; i < columns.getColumnCount(); ++i) {
				usedExtents.add(columns.getColumnContentSize(i));
			}
		} else {
			usedExtents.add(container.getContentSize());
		}
		double maxUsed = 0;
		for (final double used : usedExtents) {
			maxUsed = Math.max(maxUsed, used);
		}
		return new BalanceCandidate(capacity, maxUsed, usedExtents.size(), usedExtents, shell);
	}
}
