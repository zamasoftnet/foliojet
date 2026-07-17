package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;

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
 * 開いている(未対応の)Start を残して破棄します。保持量は
 * O(現在ページ+開いている要素) に保たれます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LayoutSource {
	public sealed interface Event permits Start, Replaced, Chars, EndBlock, Opaque {
	}

	/**
	 * ボックスの種別です。params/pos からの再インスタンス化の
	 * ファクトリ選択に使います(SourceReplayer.newBox)。
	 */
	public enum BoxKind {
		/** 通常ブロック(FlowBlockBox)。 */
		FLOW,
		/** マルチカラムブロック(MulticolumnBlockBox)。 */
		MULTICOL,
		/** インライン(InlineBox)。 */
		INLINE,
		/** 外置きリストマーカー(OutsideMarkerBox)。 */
		MARKER,
		/** 浮動ブロック(FloatBlockBox)。 */
		FLOAT_BLOCK,
		/** インラインブロック(InlineBlockBox)。 */
		INLINE_BLOCK,
		/** 内部マーカー(InsideMarkerBox)。 */
		INSIDE_MARKER,
		/** テーブル(TableBox。blockBox は params 共有+pos クラスで再構成)。 */
		TABLE,
		/** テーブル行グループ(TableRowGroupBox)。 */
		TABLE_ROW_GROUP,
		/** テーブル行(TableRowBox)。 */
		TABLE_ROW,
		/** テーブルセル(TableCellBox)。 */
		TABLE_CELL,
		/** テーブル列グループ(TableColumnGroupBox)。 */
		TABLE_COLUMN_GROUP,
		/** テーブル列(TableColumnBox)。 */
		TABLE_COLUMN;
	}

	/**
	 * ボックスの開始です。kind と params/pos から同型のボックスを
	 * 再インスタンス化できます(生成内容・マーカー番号等は解決済みの
	 * 後続イベントとして続くため、再生でスタイル副作用は再実行されません)。
	 */
	public record Start(BoxKind kind, net.zamasoft.foliojet.layout.box.params.Params params, Pos pos)
			implements Event {
	}

	/**
	 * 置換要素です。置換ボックスは分割で変異しない葉なので、
	 * 同じインスタンスを再生時にそのまま doc へ渡して再配置します
	 * (box-restyle の addBound 再利用と同じ意味論)。
	 */
	public record Replaced(net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) implements Event {
	}

	/**
	 * テキストです。charOffset はソース文字オフセット(生成内容は -1)。
	 * fixed は doc プロトコルの固定テキストフラグをそのまま保持します。
	 */
	public record Chars(int charOffset, char[] ch, boolean fixed) implements Event {
	}

	/**
	 * ブロックの終了です。
	 */
	public record EndBlock() implements Event {
	}

	/**
	 * まだ再生に対応していないイベントです(置換要素・表など)。
	 * ログの完全性(正直な全記録)のために位置だけ占有し、範囲に
	 * これを含む再生要求はフォールバックさせます。
	 */
	public record Opaque() implements Event {
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
	 * 未消費の再生範囲の保持リースです(参照カウント。C1c)。
	 * ボックスを運搬しない source-only の継続アイテムが所有し、消費完了で
	 * close する。リースが生きている間、compact はその fromId より前を
	 * 破棄しない。close は冪等。
	 */
	public final class RetentionLease implements AutoCloseable {
		private final long fromId;
		private boolean closed = false;

		private RetentionLease(final long fromId) {
			this.fromId = fromId;
		}

		public long fromId() {
			return this.fromId;
		}

		@Override
		public void close() {
			if (this.closed) {
				return;
			}
			this.closed = true;
			LayoutSource.this.release(this.fromId);
		}
	}

	/**
	 * fromId 以降のイベントの保持リースです(fromId → 参照数)。
	 * 同じ fromId を複数の継続が独立に所有し得るため参照カウント
	 * (単純な集合では一方の解放が他方の pin を消す — 外部レビュー指摘)。
	 */
	private final java.util.TreeMap<Long, Integer> retentionLeases = new java.util.TreeMap<>();

	/**
	 * fromId 以降のイベントを保持するリースを取得します。
	 */
	public RetentionLease retainFrom(final long fromId) {
		this.retentionLeases.merge(fromId, 1, Integer::sum);
		return new RetentionLease(fromId);
	}

	private void release(final long fromId) {
		final Integer count = this.retentionLeases.get(fromId);
		if (count == null) {
			throw new IllegalStateException("リースの対応が壊れています: " + fromId);
		}
		if (count <= 1) {
			this.retentionLeases.remove(fromId);
		} else {
			this.retentionLeases.put(fromId, count - 1);
		}
	}

	/**
	 * watermark より前のイベントを、開いている Start を残して
	 * 破棄します。開いている Start の id は変わりません。
	 * 生きている保持リースの最小 fromId が watermark を内部で clamp
	 * します(呼び出し側が水位とリースを合成する必要はない)。
	 *
	 * @param watermark これより前(id &lt; watermark)が破棄対象
	 */
	public void compact(final long watermark) {
		final long clamped = this.retentionLeases.isEmpty() ? watermark
				: Math.min(watermark, this.retentionLeases.firstKey());
		final List<Entry> kept = new ArrayList<Entry>();
		// 破棄対象範囲の「未対応 Start」のスタックを求める
		final List<Entry> open = new ArrayList<Entry>();
		for (final Entry entry : this.entries) {
			if (entry.id() >= clamped) {
				break;
			}
			switch (entry.event()) {
			// Opaque も EndBlock と対を成す開始イベント(startBox が Opaque を
			// 積み endBox が EndBlock を積む)。Start と同様に扱わないと、
			// Opaque の対の EndBlock が祖先の Start を誤って pop し、
			// compaction の反復で開チェーンが崩壊する(2026-07-17 修正)
			case Start start -> open.add(entry);
			case Opaque opaque -> open.add(entry);
			case EndBlock end -> {
				if (!open.isEmpty()) {
					open.remove(open.size() - 1);
				}
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			}
		}
		kept.addAll(open);
		for (final Entry entry : this.entries) {
			if (entry.id() >= clamped) {
				kept.add(entry);
			}
		}
		this.entries.clear();
		this.entries.addAll(kept);
	}

	/**
	 * id の Start に対応する EndBlock の id を返します。
	 * 部分木がまだ閉じていなければ -1。
	 */
	public long endOf(final long startId) {
		int index = this.indexOf(startId);
		if (index < 0 || !(this.entries.get(index).event() instanceof Start)) {
			return -1;
		}
		int depth = 0;
		for (int i = index; i < this.entries.size(); ++i) {
			switch (this.entries.get(i).event()) {
			// Opaque は EndBlock と対の開始イベント(compact と同じ対称性)
			case Start start -> ++depth;
			case Opaque opaque -> ++depth;
			case EndBlock end -> {
				if (--depth == 0) {
					return this.entries.get(i).id();
				}
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			}
		}
		return -1;
	}

	/**
	 * 指定のソース文字オフセットを含む Chars イベントの id を返します
	 * (M6b v3 テキスト尾部再開)。parser の charOffset は文書全体で
	 * 単調のため一意です。生成内容(charOffset=-1)は対象外。
	 *
	 * @param charOffset ソース文字オフセット
	 * @return 該当 Chars の id。なければ -1
	 */
	public long findCharsAt(final int charOffset) {
		for (final Entry entry : this.entries) {
			if (entry.event() instanceof Chars(final int off, final char[] ch, final boolean fixed) && off >= 0
					&& charOffset >= off && charOffset < off + ch.length) {
				return entry.id();
			}
		}
		return -1;
	}

	/**
	 * テキスト尾部の終端を返します(M6b v3): fromId から前方走査し、
	 * 範囲内で開かれていない EndBlock(=囲みブロックの終了)または
	 * ブロック級の兄弟の Start(=段落の終わり。インライン級の Start は
	 * 段落の続きなので通過)に当たればその id、capExclusive まで
	 * 当たらなければ capExclusive。
	 *
	 * <p>
	 * ブロック級 Start での停止(2026-07-17)により、終端導出は次兄弟の
	 * ソースアンカーに依存しない — 兄弟が分割断片(アンカー無効)でも
	 * 尾部再生できる。
	 * </p>
	 *
	 * @param fromId       走査開始位置
	 * @param capExclusive 上限(これ以上は走査しない)
	 * @return 尾部の終端(exclusive)
	 */
	public long tailBound(final long fromId, final long capExclusive) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return fromId;
		}
		int depth = 0;
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() >= capExclusive) {
				break;
			}
			switch (entry.event()) {
			// Opaque は EndBlock と対の開始イベント(compact と同じ対称性)
			case Start(final BoxKind kind, final net.zamasoft.foliojet.layout.box.params.Params params,
					final Pos pos) -> {
				if (depth == 0 && isBlockLevel(kind)) {
					// 段落の次のブロック級兄弟 = 尾部の終わり
					return entry.id();
				}
				++depth;
			}
			case Opaque opaque -> ++depth;
			case EndBlock end -> {
				if (depth == 0) {
					return entry.id();
				}
				--depth;
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			}
		}
		return capExclusive;
	}

	/**
	 * ブロック級(段落を終わらせる)種別かを返します。インライン級
	 * (INLINE/INLINE_BLOCK/各マーカー)は段落の続きとして通過させます。
	 */
	private static boolean isBlockLevel(final BoxKind kind) {
		// フロートは段落を終わらせない(ソース位置は段落中)。尾部範囲に
		// フロートが入る場合の再生可否は呼び出し側の containsFloat ゲートが
		// 判定する(再生は係留を再実行するため二重化の危険がある)
		return switch (kind) {
		case FLOW, MULTICOL, TABLE -> true;
		default -> false;
		};
	}

	/**
	 * [fromId, toId] の範囲に Opaque(再生非対応)イベントが
	 * 含まれていれば true を返します。
	 */
	public boolean containsOpaque(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return true;
		}
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			if (entry.event() instanceof Opaque) {
				return true;
			}
		}
		return false;
	}

	/**
	 * [fromId, toId] の範囲にマルチカラムの Start が含まれていれば
	 * true を返します(M6c: 段組内容の再生は列機構(columnBreak/balance)
	 * との相互作用が未検証のためフォールバックさせる)。
	 */
	public boolean containsMulticol(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return true;
		}
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			if (entry.event() instanceof Start(final BoxKind kind, final net.zamasoft.foliojet.layout.box.params.Params params,
					final Pos pos) && kind == BoxKind.MULTICOL) {
				return true;
			}
		}
		return false;
	}

	/**
	 * [fromId, toId] の範囲に、指定の書字方向と異なる内容が含まれていれば
	 * true を返します(M6b: 縦横混在の再生はサブビルダー文脈の再現が
	 * 未設計のためフォールバックさせる)。
	 */
	public boolean containsMixedFlow(final long fromId, final long toId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return true;
		}
		final boolean vertical = rootFlow.isVertical();
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			if (entry.event() instanceof Start(final BoxKind kind,
					final net.zamasoft.foliojet.layout.box.params.Params params, final Pos pos)
					&& params instanceof net.zamasoft.foliojet.layout.box.params.AbstractTextParams textParams
					&& textParams.flow.isVertical() != vertical) {
				return true;
			}
		}
		return false;
	}

	/**
	 * [fromId, toId] の範囲に浮動配置の Start が含まれていれば
	 * true を返します(M6c: バランスのソース再生はフロートの係留の
	 * 再現が未検証のためフォールバックさせる)。
	 */
	public boolean containsFloat(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return true;
		}
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			if (entry.event() instanceof Start(final BoxKind kind, final net.zamasoft.foliojet.layout.box.params.Params params,
					final Pos pos) && pos.getType() == net.zamasoft.foliojet.layout.box.params.PosType.FLOAT) {
				return true;
			}
			if (entry.event() instanceof Replaced(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box)
					&& box.getPos().getType() == net.zamasoft.foliojet.layout.box.params.PosType.FLOAT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 一回の再生が読むイベント列の不変スナップショットです。
	 *
	 * <p>
	 * visitor 呼出し前に範囲の完全性を検証してコピーすることで、visitor 内の
	 * 入れ子改ページによる compact(backing list の clear+addAll)から再生
	 * 走査を隔離します。数値 index で生リストを歩くと、入れ子 compact で
	 * index がずれて未消費イベントを silent skip する(外部レビュー指摘。
	 * LayoutSourceTest.testReplaySurvivesNestedCompaction が固定)。
	 * </p>
	 */
	public record ReplaySlice(long fromId, long toId, List<Event> events) {
		public void replay(final java.util.function.Consumer<Event> visitor) {
			for (final Event event : this.events) {
				visitor.accept(event);
			}
		}
	}

	/**
	 * [fromId, toId] の不変スナップショットを検証付きで取得します。
	 * 端が破棄済み・範囲の内部に破棄済みの穴がある・toId まで届かない
	 * 場合は null(呼び出し側の契約に応じてフォールバックまたは失敗)。
	 */
	public ReplaySlice capture(final long fromId, final long toId) {
		final int index = this.indexOf(fromId);
		if (index < 0 || toId < fromId) {
			return null;
		}
		final List<Event> events = new ArrayList<Event>();
		long expected = fromId;
		for (int i = index; i < this.entries.size(); ++i) {
			final Entry entry = this.entries.get(i);
			if (entry.id() != expected) {
				// 内部に破棄済みの穴(EventId は連番で付与されるため)
				return null;
			}
			events.add(entry.event());
			if (entry.id() == toId) {
				return new ReplaySlice(fromId, toId, List.copyOf(events));
			}
			++expected;
		}
		return null;
	}

	/**
	 * [fromId, toId] の範囲のイベントを順に visitor へ渡します。
	 * 内部で不変スナップショットを取ってから駆動するため、visitor 内の
	 * 入れ子改ページ(compact)に対して安全です。範囲が完全でなければ
	 * 実行前に失敗します(フォールバック可能な呼び出し側は
	 * {@link #capture} を使うこと)。
	 */
	public void replay(final long fromId, final long toId, final java.util.function.Consumer<Event> visitor) {
		final ReplaySlice slice = this.capture(fromId, toId);
		if (slice == null) {
			throw new IllegalStateException("replay range is not intact: [" + fromId + ", " + toId + "], retained=["
					+ (this.entries.isEmpty() ? "-" : this.entries.get(0).id() + ".." + this.entries.get(this.entries.size() - 1).id())
					+ "]");
		}
		slice.replay(visitor);
	}
}
