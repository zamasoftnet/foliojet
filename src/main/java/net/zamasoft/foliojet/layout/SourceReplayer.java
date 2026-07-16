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
	 * 切断段落の尾部再生の発火計測です(v3 では未実装、実装時に使用)。
	 */
	public static final AtomicLong TEXT_TAIL_REPLAYS = new AtomicLong();

	private SourceReplayer() {
		// driver
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
		if (toId < fromId || log.containsOpaque(fromId, toId)) {
			return false;
		}
		// live パイプライン(shaper)が未配達のまま保留している文字は
		// break 後に live 側から供給されるため、再生はそこで打ち切る
		final int charEndExclusive = pageGenerator.getDeliveredCharEnd();
		if (charEndExclusive <= charOffset) {
			// 再開位置全体が live 保留中: 再生不要(live が全て供給する)
			return false;
		}
		final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
		final boolean[] first = { true };
		log.replay(fromId, toId, event -> {
			switch (event) {
			case LayoutSource.StartBlock(final BlockParams params, final Pos pos) -> doc
					.startBox(new FlowBlockBox(params, (FlowPos) pos));
			case LayoutSource.StartInline(final net.zamasoft.foliojet.layout.box.params.InlineParams params,
					final net.zamasoft.foliojet.layout.box.params.InlinePos pos) -> doc
					.startBox(new net.zamasoft.foliojet.layout.box.impl.InlineBox(params, pos));
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
	 * [fromId, toId] の閉じた部分木列を再駆動します。
	 * 範囲は Opaque を含まないこと(呼び出し側が containsOpaque で検査)。
	 *
	 * @param log           ソースログ
	 * @param fromId        先頭 StartBlock の EventId
	 * @param toId          対応する EndBlock の EventId
	 * @param rootBuilder   再生先のルートビルダー(現在のページ文脈)
	 * @param pageGenerator ページ生成器
	 */
	public static void replay(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
		log.replay(fromId, toId, event -> {
			switch (event) {
			case LayoutSource.StartBlock(final BlockParams params, final Pos pos) -> doc
					.startBox(new FlowBlockBox(params, (FlowPos) pos));
			case LayoutSource.StartInline(final net.zamasoft.foliojet.layout.box.params.InlineParams params,
					final net.zamasoft.foliojet.layout.box.params.InlinePos pos) -> doc
					.startBox(new net.zamasoft.foliojet.layout.box.impl.InlineBox(params, pos));
			case LayoutSource.Chars(final int charOffset, final char[] ch, final boolean fixed) -> doc
					.characters(charOffset, ch, 0, ch.length, fixed);
			case LayoutSource.Replaced(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) -> doc
					.addReplacedBox(box);
			case LayoutSource.EndBlock end -> doc.endBox();
			case LayoutSource.Opaque opaque -> throw new IllegalStateException("opaque event in replay range");
			}
		});
		doc.finishReplay();
		SUBTREE_REPLAYS.incrementAndGet();
	}
}
