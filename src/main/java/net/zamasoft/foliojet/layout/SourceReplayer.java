package net.zamasoft.foliojet.layout;

import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

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
	 * スナップショット範囲のイベントを doc へそのまま再駆動します
	 * (共有ドライバ)。slice は駆動前に確定した不変コピーのため、
	 * 駆動中の入れ子改ページによる compact の影響を受けません。
	 */
	private static void drive(final DocumentBuilder doc, final LayoutSource.ReplaySlice slice) {
		slice.replay(event -> {
			switch (event) {
			case LayoutSource.Start start -> doc.startBox(newBox(start));
			case LayoutSource.Replaced(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) -> doc
					.addReplacedBox(box);
			case LayoutSource.Chars(final int charOffset, final char[] ch, final boolean fixed) -> doc
					.characters(charOffset, ch, 0, ch.length, fixed);
			case LayoutSource.EndBlock end -> doc.endBox();
			case LayoutSource.Opaque opaque -> throw new IllegalStateException("opaque event in replay range");
			}
		});
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
	 * 記録された kind と params/pos から同型のボックスを作ります
	 * (StyleBuilder.boxKind と対のファクトリ)。
	 */
	private static net.zamasoft.foliojet.layout.box.INonReplacedBox newBox(final LayoutSource.Start start) {
		return switch (start.kind()) {
		case FLOW -> new FlowBlockBox((BlockParams) start.params(), (FlowPos) start.pos());
		case MULTICOL -> new net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox((BlockParams) start.params(),
				(FlowPos) start.pos());
		case INLINE -> new net.zamasoft.foliojet.layout.box.impl.InlineBox(
				(net.zamasoft.foliojet.layout.box.params.InlineParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.InlinePos) start.pos());
		case MARKER -> new net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox((BlockParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.InlinePos) start.pos());
		case FLOAT_BLOCK -> new net.zamasoft.foliojet.layout.box.impl.FloatBlockBox((BlockParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.FloatPos) start.pos());
		case INLINE_BLOCK -> new net.zamasoft.foliojet.layout.box.impl.InlineBlockBox((BlockParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.InlinePos) start.pos());
		case INSIDE_MARKER -> new net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox((BlockParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.InlinePos) start.pos());
		case TABLE -> {
			final net.zamasoft.foliojet.layout.box.params.TableParams tableParams = (net.zamasoft.foliojet.layout.box.params.TableParams) start
					.params();
			yield new net.zamasoft.foliojet.layout.box.impl.TableBox(tableParams,
					new FlowBlockBox(tableParams, (FlowPos) start.pos()));
		}
		case TABLE_ROW_GROUP -> new net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox(
				(net.zamasoft.foliojet.layout.box.params.InnerTableParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.TableRowGroupPos) start.pos());
		case TABLE_ROW -> new net.zamasoft.foliojet.layout.box.impl.TableRowBox(
				(net.zamasoft.foliojet.layout.box.params.InnerTableParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.TableRowPos) start.pos());
		case TABLE_CELL -> new net.zamasoft.foliojet.layout.box.impl.TableCellBox(
				(net.zamasoft.foliojet.layout.box.params.BlockParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.TableCellPos) start.pos(),
				new net.zamasoft.foliojet.layout.box.content.FlowContainer());
		case TABLE_COLUMN_GROUP -> new net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox(
				(net.zamasoft.foliojet.layout.box.params.InnerTableParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.TableColumnPos) start.pos());
		case TABLE_COLUMN -> new net.zamasoft.foliojet.layout.box.impl.TableColumnBox(
				(net.zamasoft.foliojet.layout.box.params.InnerTableParams) start.params(),
				(net.zamasoft.foliojet.layout.box.params.TableColumnPos) start.pos());
		};
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
		final boolean[] first = { true };
		slice.replay(event -> {
			switch (event) {
			case LayoutSource.Start start -> doc.startBox(newBox(start));
			case LayoutSource.Chars(final int off, final char[] ch, final boolean fixed) -> {
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
				if (len > 0) {
					doc.characters(off + skip, ch, skip, len, fixed);
				}
			}
			case LayoutSource.Replaced(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) -> doc
					.addReplacedBox(box);
			case LayoutSource.EndBlock end -> doc.endBox();
			case LayoutSource.Opaque opaque -> throw new IllegalStateException("opaque event in replay range");
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
		if (log == null || selfId < 0) {
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return false;
		}
		if (log.containsOpaque(selfId + 1, endId - 1) || log.containsFloat(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, target.getRootBox().getBlockParams().flow)) {
			// フロート係留・入れ子段組・縦横混在の再現は未検証のためフォールバック
			return false;
		}
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
