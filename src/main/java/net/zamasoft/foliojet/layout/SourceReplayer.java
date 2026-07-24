package net.zamasoft.foliojet.layout;

import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;

/**
 * レイアウトソースの再生ドライバです(M6b v3)。
 *
 * <p>
 * 改ページ残余のうち「丸ごと次ページへ移動した閉じた部分木」を、
 * LayoutSource の記録から再スタイルなしで再レイアウトします。
 * ライブの StyleBuilder/DocumentBuilder の状態には一切触れず、
 * 新品の DocumentBuilder を既存のルートビルダーへ向けて駆動します
 * (doc プロトコルの対称性 pop→open→push が新品の unitizer 上で
 * 完結するため、v1 の再入クラッシュは構造的に起きません)。
 * ボックスは記録済みの params/pos から再インスタンス化されるため、
 * 新しいページ文脈(利用可能幅・フロート)で完全に再レイアウトされます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SourceReplayer {
	/**
	 * 閉部分木のソース再生の発火計測です(移行カバレッジの証明・診断用)。
	 */
	public static final AtomicLong SUBTREE_REPLAYS = new AtomicLong();

	/**
	 * 切断段落の尾部再生の発火計測です(実験フラグ制)。
	 */
	public static final AtomicLong TEXT_TAIL_REPLAYS = new AtomicLong();

	/**
	 * 吸収済み再生範囲(C1c prefixItems)経由の発火計測です
	 * (SUBTREE_REPLAYS の内数。ボックス運搬なしの経路が実際に
	 * 通っていることの移行カバレッジ)。
	 */
	public static final AtomicLong PREFIX_REPLAYS = new AtomicLong();

	/**
	 * カラムバランスのソース再生の発火計測です(M6c)。
	 */
	public static final AtomicLong BALANCE_REPLAYS = new AtomicLong();

	private SourceReplayer() {
		// driver
	}

	/**
	 * capture 済み範囲のイベントを doc へそのまま再駆動します
	 * (共有ドライバ)。slice は検証済みの streaming ビュー(リース付き。
	 * E-6増分3a)のため、駆動中の入れ子改ページによる compact の影響を
	 * 受けません(未読範囲はリースが守る)。
	 *
	 * <p>
	 * E-6増分3b-1(2026-07-24): 駆動本体(ボックス構築・SourceAnchor
	 * 再付与・Chars の fresh copy 駆動)は BalanceProbeSession と共有の
	 * {@link SegmentExecutor} へ一元化した。E-6増分3b-6: live型
	 * ({@code ReplacedLive})の撤去により過渡の {@code executeLive}
	 * 経路も撤去され、{@code LayoutSource.Event} をオンザフライで
	 * {@link SegmentEvent} へ変換して単一の
	 * {@link SegmentExecutor#execute(SegmentEvent)} で駆動する
	 * (BalanceProbeSession と完全に同型)。範囲に {@code Opaque} が
	 * 含まれる場合は Barrier 変換 → execute が即時失敗する——適格判定
	 * ({@code containsOpaque})は呼び出し側の契約。
	 * </p>
	 */
	private static void drive(final DocumentBuilder doc, final LayoutSource.ReplaySlice slice) {
		// slice の EventId は fromId からの連番(capture が検証済み)。
		// 再生インスタンスにはイベントIDから SourceAnchor を再付与する
		// (P0: アンカーはボックス個体に属する — 次の破断で再び
		// 再生可能になるための系譜)
		final SegmentExecutor executor = new SegmentExecutor(doc, slice.fromId());
		slice.replay(event -> executor.execute(LayoutSourceEventConverter.convert(event)));
	}

	/**
	 * ログ範囲を scratch ページへ再生し、実レイアウトで計測します(M2c)。
	 * ライブの状態には一切触れず、新品のボックス木を作って測るため、
	 * 何度でも・任意の寸法で呼べます。
	 *
	 * @param log      ソースログ
	 * @param fromId   範囲の先頭 EventId
	 * @param toId     範囲の末尾 EventId
	 * @param template 書体等を引き継ぐ計算済みパラメータ
	 * @param ua       ユーザーエージェント
	 * @param width    scratch ページ幅(max-content 測定は十分大きな値)
	 * @param height   scratch ページ高さ
	 * @param paginate 破断を許すか(収まりのプローブは true、寸法測定は false)
	 * @return 測定結果を保持する生成器(最終ページ・ページ数)
	 */
	public static MeasurePageGenerator measure(final LayoutSource log, final long fromId, final long toId,
			final BlockParams template, final net.zamasoft.foliojet.ua.UserAgent ua, final double width,
			final double height, final boolean paginate) {
		final MeasurePageGenerator pg = new MeasurePageGenerator(ua, template, width, height);
		final DocumentBuilder doc = new DocumentBuilder(pg);
		if (!paginate) {
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
		}
		// 子範囲を裸のまま scratch ページ直下へ流すと、フロート等が
		// ページボックスに係留されようとして壊れる。元のブロックに相当する
		// ラッパーブロックで包んで、係留文脈を通常構築と同型にする
		final BlockParams wrapperParams = new BlockParams();
		wrapperParams.fontStyle = template.fontStyle;
		wrapperParams.fontManager = template.fontManager;
		wrapperParams.lineBreakRules = template.lineBreakRules;
		wrapperParams.flow = template.flow;
		wrapperParams.direction = template.direction;
		doc.startBox(new FlowBlockBox(wrapperParams, new FlowPos()));
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// 計測はフォールバック経路を持たない(範囲は呼び出し側が
			// 生きているうちに確定させる契約)
			throw new IllegalStateException("measure range is not intact: [" + fromId + ", " + toId + "]");
		}
		drive(doc, slice);
		doc.endBox();
		doc.end();
		return pg;
	}

	/**
	 * 切断段落の尾部(charOffset 以降)を再駆動します(M6b v3)。
	 * ログから該当 Chars を charOffset の単調性で直接探索するため、
	 * 分割でアンカーを失ったチェーンにも依存しません。
	 *
	 * @param log            ソースログ
	 * @param charOffset     再開位置のソース文字オフセット
	 * @param endIdExclusive 尾部の終端(次の兄弟の EventId。負ならログ末尾まで)
	 * @param keepTextOpen   再生後もテキストブロックを開いたままにする
	 *                       (続く SAX ストリームが流れ込む場合)
	 * @param rootBuilder    再生先のルートビルダー
	 * @param pageGenerator  ページ生成器
	 * @return 再開位置を特定し再駆動した場合 true
	 */
	public static boolean replayTextTail(final LayoutSource log, final int charOffset, final long endIdExclusive,
			final boolean keepTextOpen, final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		final long fromId = log.findCharsAt(charOffset);
		if (fromId < 0) {
			return false;
		}
		// 尾部は囲みブロックの EndBlock(=このテキストの終わり)または
		// 次の兄弟アイテムの手前まで
		final long cap = endIdExclusive < 0 ? log.nextId() : endIdExclusive;
		final long toId = log.tailBound(fromId, cap) - 1;
		if (toId < fromId || log.containsOpaque(fromId, toId) || log.containsFloat(fromId, toId)) {
			// フロートを含む尾部の再生は係留の再実行(二重化)の危険が
			// あるためフォールバック(replayChildren と同じゲート)
			return false;
		}
		// live パイプライン(shaper)が未配達のまま保留している文字は
		// break 後に live 側から供給されるため、再生はそこで打ち切る
		final int charEndExclusive = pageGenerator.getDeliveredCharEnd();
		if (charEndExclusive <= charOffset) {
			// 再開位置全体が live 保留中: 再生不要(live が全て供給する)
			return false;
		}
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// 範囲が欠けていれば box-restyle へフォールバック
			return false;
		}
		final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
		// E-6増分3b-1: 駆動本体は共有の SegmentExecutor へ。Chars だけは
		// 尾部特有のトリミング(先頭 skip・配達済み終端での打ち切り)を
		// ここで計算し、部分範囲プリミティブで駆動する(それ以外は
		// 3b-6以降 drive と同じオンザフライ変換の単一 execute)
		final SegmentExecutor executor = new SegmentExecutor(doc, slice.fromId());
		final boolean[] first = { true };
		slice.replay(event -> {
			switch (event) {
			case LayoutSource.Chars(final int off, final LayoutSource.TextPayload payload, final boolean fixed) -> {
				// E-6増分3b-2: payloadはspill済みのことがある。freshChars()は
				// 毎回freshな配列(Inline=clone、Spilled=decode)を返す
				final char[] ch = payload.freshChars();
				int skip = 0;
				if (first[0]) {
					first[0] = false;
					skip = charOffset - off;
				}
				int len = ch.length - skip;
				if (off >= 0 && off + skip + len > charEndExclusive) {
					// 配達済み終端で打ち切り(以降は live が供給)
					len = charEndExclusive - off - skip;
				}
				executor.executeCharsRange(off + skip, ch, skip, len, fixed);
			}
			default -> executor.execute(LayoutSourceEventConverter.convert(event));
			}
		});
		if (keepTextOpen) {
			doc.finishReplayKeepText();
		} else {
			doc.finishReplay();
		}
		TEXT_TAIL_REPLAYS.incrementAndGet();
		return true;
	}

	/**
	 * 子範囲の再駆動({@link #replayChildren})が可能かを判定します
	 * (2026-07-24分離、排除域P2のM6c-2——バランスの実プローブが反復の前に
	 * 一度だけ検査するため。判定条件は従来の{@code replayChildren}冒頭と
	 * 完全に同一)。
	 *
	 * @param log    ソースログ
	 * @param selfId 親ボックスの Start の EventId
	 * @param flow   再生先の書字方向
	 * @return 再駆動可能なら true
	 */
	public static boolean canReplayChildren(final LayoutSource log, final long selfId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		if (log == null || selfId < 0) {
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return false;
		}
		// フロート係留・入れ子段組・縦横混在の再現は未検証のためフォールバック
		return !(log.containsOpaque(selfId + 1, endId - 1) || log.containsFloat(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1) || log.containsMixedFlow(selfId + 1, endId - 1, flow));
	}

	/**
	 * 閉じたブロックの「子イベント範囲」を指定ビルダーへ再駆動します
	 * (M6c: カラムバランス。multicol は endFlowBlock 時点で閉部分木
	 * なので、その内容をソースから ColumnBuilder へ再構築できる)。
	 *
	 * @param log        ソースログ
	 * @param selfId     ブロック自身の StartBlock の EventId
	 * @param target     再生先ビルダー(ColumnBuilder 等)
	 * @param pageGenerator ページ生成器
	 * @return 再駆動できた場合 true(範囲不明・Opaque 含みは false)
	 */
	public static boolean replayChildren(final LayoutSource log, final long selfId, final BlockBuilder target,
			final PageGenerator pageGenerator) {
		if (!canReplayChildren(log, selfId, target.getRootBox().getBlockParams().flow)) {
			return false;
		}
		final long endId = log.endOf(selfId);
		final LayoutSource.ReplaySlice slice = log.capture(selfId + 1, endId - 1);
		if (slice == null) {
			// 範囲が欠けていればボックス再生へフォールバック
			return false;
		}
		final DocumentBuilder doc = new DocumentBuilder(pageGenerator, target);
		drive(doc, slice);
		doc.finishReplay();
		BALANCE_REPLAYS.incrementAndGet();
		return true;
	}

	/**
	 * [fromId, toId] の閉じた部分木列を再駆動します。
	 * 範囲は Opaque を含まないこと(呼び出し側が containsOpaque で検査)。
	 *
	 * @param log           ソースログ
	 * @param fromId        先頭 StartBlock の EventId
	 * @param toId          対応する EndBlock の EventId
	 * @param rootBuilder   再生先のルートビルダー(現在のページ文脈)
	 * @param pageGenerator ページ生成器
	 * @return 再駆動した場合 true。範囲が欠けていれば駆動前に false
	 *         (呼び出し側の契約: box フォールバックがある経路は false を
	 *         フォールバックへ、ない経路(C1c prefix)は失敗にする)
	 */
	public static boolean replay(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		assert !log.containsMulticol(fromId, toId);
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			return false;
		}
		final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
		drive(doc, slice);
		doc.finishReplay();
		SUBTREE_REPLAYS.incrementAndGet();
		return true;
	}
}
