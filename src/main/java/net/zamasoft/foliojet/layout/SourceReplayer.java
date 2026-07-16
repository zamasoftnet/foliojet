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
			case LayoutSource.Chars(final int charOffset, final char[] ch, final boolean fixed) -> doc
					.characters(charOffset, ch, 0, ch.length, fixed);
			case LayoutSource.EndBlock end -> doc.endBox();
			case LayoutSource.Opaque opaque -> throw new IllegalStateException("opaque event in replay range");
			}
		});
		doc.finishReplay();
		SUBTREE_REPLAYS.incrementAndGet();
	}
}
