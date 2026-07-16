package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Pos;

/**
 * レイアウトのソースプロトコルログです(M6b v3)。
 *
 * <p>
 * スタイル適用・疑似要素合成の後、レイアウトの前という境界
 * (DocumentBuilder への入力プロトコル)のイベントを追記専用で記録します。
 * params/pos は計算済みの産物なので、再生でセレクタ照合・生成内容合成・
 * カウンタ評価が再実行されることは構造的にありません。改ページ残余の
 * 再生はこのログを read-only で読み、ライブの StyleBuilder/DocumentBuilder
 * の状態には一切触れない専用ドライバが行います(ARCHITECTURE.md §5.6 v3)。
 * </p>
 *
 * <p>
 * <b>EventId</b>: 各イベントには付与時から不変の id が振られます。
 * ボックスへの刻印(アンカー)は id で行い、compaction 後も安定です。
 * <b>水位破棄</b>: {@link #compact(long)} は指定 id より前のイベントを、
 * 開いている(未対応の)StartBlock を残して破棄します。保持量は
 * O(現在ページ+開いている要素) に保たれます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LayoutSource {
	public sealed interface Event permits StartBlock, Chars, EndBlock {
	}

	/**
	 * ブロックの開始です。params/pos から同型のボックスを再インスタンス化
	 * できます。
	 */
	public record StartBlock(BlockParams params, Pos pos) implements Event {
	}

	/**
	 * テキストです。charOffset はソース文字オフセット(生成内容は -1)。
	 */
	public record Chars(int charOffset, char[] ch) implements Event {
	}

	/**
	 * ブロックの終了です。
	 */
	public record EndBlock() implements Event {
	}

	private record Entry(long id, Event event) {
	}

	private final List<Entry> entries = new ArrayList<Entry>();

	private long nextId = 0;

	/**
	 * イベントを追記し、その EventId を返します。
	 */
	public long append(final Event event) {
		final long id = this.nextId++;
		this.entries.add(new Entry(id, event));
		return id;
	}

	/**
	 * 次に付与される EventId を返します(= 現在の末尾位置)。
	 */
	public long nextId() {
		return this.nextId;
	}

	/**
	 * 保持しているイベント数を返します。
	 */
	public int size() {
		return this.entries.size();
	}

	/**
	 * id のイベントを返します(破棄済み・未付与なら null)。
	 */
	public Event get(final long id) {
		final int index = this.indexOf(id);
		return index < 0 ? null : this.entries.get(index).event();
	}

	/**
	 * id 以降で最初に保持されているイベントの位置を返します(内部用)。
	 * compaction で疎になった id 列を二分探索します。
	 */
	private int indexOf(final long id) {
		int low = 0;
		int high = this.entries.size() - 1;
		while (low <= high) {
			final int mid = (low + high) >>> 1;
			final long midId = this.entries.get(mid).id();
			if (midId < id) {
				low = mid + 1;
			} else if (midId > id) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	/**
	 * watermark より前のイベントを、開いている StartBlock を残して
	 * 破棄します。開いている StartBlock の id は変わりません。
	 *
	 * @param watermark これより前(id &lt; watermark)が破棄対象
	 */
	public void compact(final long watermark) {
		final List<Entry> kept = new ArrayList<Entry>();
		// 破棄対象範囲の「未対応 StartBlock」のスタックを求める
		final List<Entry> open = new ArrayList<Entry>();
		for (final Entry entry : this.entries) {
			if (entry.id() >= watermark) {
				break;
			}
			switch (entry.event()) {
			case StartBlock start -> open.add(entry);
			case EndBlock end -> {
				if (!open.isEmpty()) {
					open.remove(open.size() - 1);
				}
			}
			case Chars chars -> {
			}
			}
		}
		kept.addAll(open);
		for (final Entry entry : this.entries) {
			if (entry.id() >= watermark) {
				kept.add(entry);
			}
		}
		this.entries.clear();
		this.entries.addAll(kept);
	}

	/**
	 * id の StartBlock に対応する EndBlock の id を返します。
	 * 部分木がまだ閉じていなければ -1。
	 */
	public long endOf(final long startId) {
		int index = this.indexOf(startId);
		if (index < 0 || !(this.entries.get(index).event() instanceof StartBlock)) {
			return -1;
		}
		int depth = 0;
		for (int i = index; i < this.entries.size(); ++i) {
			switch (this.entries.get(i).event()) {
			case StartBlock start -> ++depth;
			case EndBlock end -> {
				if (--depth == 0) {
					return this.entries.get(i).id();
				}
			}
			case Chars chars -> {
			}
			}
		}
		return -1;
	}

	/**
	 * [fromId, toId] の範囲のイベントを順に visitor へ渡します。
	 * 範囲内に破棄済みの穴があってはなりません(呼び出し側の契約)。
	 */
	public void replay(final long fromId, final long toId, final java.util.function.Consumer<Event> visitor) {
		int index = this.indexOf(fromId);
		assert index >= 0 : "replay from discarded event: " + fromId;
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			visitor.accept(entry.event());
		}
	}
}
