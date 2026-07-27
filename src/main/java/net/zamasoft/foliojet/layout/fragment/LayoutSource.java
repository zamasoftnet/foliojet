package net.zamasoft.foliojet.layout.fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.TextSpill;

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
 * <b>BeginBoxのappend時freeze(E-6増分3b-4、2026-07-24)</b>:
 * {@link Start}はliveのparams/pos参照ではなく、記録時に
 * {@link BoxRecipe#freeze}で凍結したrecipeを保持する。params/posの変異は
 * 全て記録前のStyleBuilderフェーズに閉じる(codex設計§1.1・独立
 * cross-check済み)ため出力挙動は不変で、ログがliveのparams/pos・
 * {@code CSSElement}のprecedingElementグラフを引き留めない
 * ({@code Params.element}は{@code StructureToken}へ切り離される——
 * {@code StructureToken}のjavadoc参照)。
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
 * <p>
 * <b>text payloadのspill(E-6増分3b-2、2026-07-24)</b>: {@link Chars}の
 * char[]本体は、inline保持量が設定予算({@code processing.text-spill
 * -budget})を超えた後の新規追記から{@link TextSpill}(一時ファイル)へ
 * 退避される。判定は設定値と累積inline bytesのみの決定的なもので、
 * heap残量には依存しない。イベント境界は一切変えない(1 Chars =
 * 1 payload。分割・結合はNFC正規化・text-transformの呼び出し境界を
 * 変えるため禁止——codex設計§2.4)。spillストアはこのLayoutSourceの
 * 寿命に紐づき、{@link #close()}(変換終了経路のfinally)で一時
 * ファイルごと確実に削除される。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LayoutSource implements AutoCloseable {
	public sealed interface Event permits Start, Replaced, Chars, EndBlock, Opaque {
	}

	/**
	 * ボックスの種別です。params/pos からの再インスタンス化の
	 * ファクトリ選択({@code BoxRecipeBoxFactory.create}のkindカーネル)と
	 * 記録時freeze({@link BoxRecipe#freeze})のvariant選択に使います。
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
		/** テーブル行グループ(TableRowGroupBox)。 */
		TABLE_ROW_GROUP,
		/** テーブル行(TableRowBox)。 */
		TABLE_ROW,
		/** テーブルセル(TableCellBox)。 */
		TABLE_CELL,
		/** テーブル列グループ(TableColumnGroupBox)。 */
		TABLE_COLUMN_GROUP,
		/** テーブル列(TableColumnBox)。 */
		TABLE_COLUMN,
		/**
		 * 絶対配置ブロック(AbsoluteBlockBox)。E-6増分4e(2026-07-24)で
		 * Opaque記録からrecipe記録へ昇格——末尾追加なのは既存ordinalを
		 * 変えないため({@code segment.BoxKind}と並びを揃える)。
		 */
		ABSOLUTE;
	}

	/**
	 * ボックスの開始です(E-6増分3b-4、2026-07-24: live params/pos参照の
	 * 保持から記録時freezeのrecipe保持へ置換)。記録時
	 * ({@code StyleBuilder.startBox})に{@link BoxRecipe#freeze}で凍結され、
	 * 再生は{@code BoxRecipeBoxFactory.create(BoxRecipe)}のmaterializeで
	 * 新品のボックスを作る——liveのparams/pos({@code CSSElement}グラフ
	 * 含む)はログに残らない。freezeは{@code StyleBuilder.boxKind}が
	 * 非nullを返す全13 kind(E-6増分4eでABSOLUTE追加)をカバーする総関数
	 * ({@code ReplacedRecipe.freeze}と違い失敗変種はない)。生成内容・マーカー番号等は解決済みの
	 * 後続イベントとして続くため、再生でスタイル副作用は再実行されません。
	 */
	public record Start(BoxRecipe recipe) implements Event {
	}

	/**
	 * 置換要素です(E-6増分3b-3、2026-07-24: live box保持からrecipe保持へ
	 * 置換)。記録時({@code StyleBuilder.addReplacedBox})に
	 * {@code ReplacedRecipe.freeze}で凍結され、再生は
	 * {@code BoxRecipeBoxFactory.createReplaced}のmaterializeで新品の
	 * ボックスを作る——liveボックスへの参照はログに残らない。
	 * E-6増分3b-6: {@code ReplacedBoxImage}実装(BarcodeImage等)を
	 * 参照するボックスも{@code duplicate()}の独立複製ベースでfreeze
	 * されるようになり、live box保持の過渡変種({@code ReplacedLive})は
	 * 撤去された。freezeできない未知サブクラス(現存4実装ではゼロ)は
	 * {@link Opaque}+{@link EndBlock}の対でfail closed記録される。
	 */
	public record Replaced(net.zamasoft.foliojet.layout.segment.ReplacedRecipe recipe) implements Event {
	}

	/**
	 * テキストです。charOffset はソース文字オフセット(生成内容は -1)。
	 * fixed は doc プロトコルの固定テキストフラグをそのまま保持します。
	 * payload は inline char[] または spill 済み record 参照です
	 * (E-6増分3b-2。{@link TextPayload})。
	 */
	public record Chars(int charOffset, TextPayload payload, boolean fixed) implements Event {
		/** inline payload での簡易構築です(テスト・小規模呼び出し用)。 */
		public Chars(final int charOffset, final char[] ch, final boolean fixed) {
			this(charOffset, new TextPayload.Inline(ch), fixed);
		}
	}

	/**
	 * {@link Chars}のテキスト本体です(E-6増分3b-2)。UTF-16長
	 * ({@link #utf16Length()})はSpilledでもheapメタデータとして持ち、
	 * {@link LayoutSource#findCharsAt(int)}等の範囲計算がdecodeなしで
	 * 成立する(挙動不変の保証)。
	 */
	public sealed interface TextPayload permits TextPayload.Inline, TextPayload.Spilled {
		/** UTF-16単位の文字数です(heapメタデータ——decode不要で読める)。 */
		int utf16Length();

		/**
		 * テキストを<b>常に呼び出しごとに新しい(freshな)char[]</b>で
		 * 返します。replay駆動の下流({@code StyledTextUnitizer})は配列を
		 * in-placeで書き換えるため、保持配列を直接返してはならない
		 * (3b-1のfresh copy方針)。Spilledの読み出し失敗は
		 * {@link TextSpillException}(型付きレイアウト失敗)。
		 */
		char[] freshChars();

		/** heap上のinline保持です。 */
		record Inline(char[] ch) implements TextPayload {
			@Override
			public int utf16Length() {
				return this.ch.length;
			}

			@Override
			public char[] freshChars() {
				return this.ch.clone();
			}
		}

		/**
		 * {@link TextSpill}へ書き出し済みのrecord参照です。heapに残るのは
		 * (store参照, recordId, utf16Length)の定数サイズのみ。
		 */
		record Spilled(TextSpill spill, long recordId, int utf16Length) implements TextPayload {
			@Override
			public char[] freshChars() {
				try {
					return this.spill.read(this.recordId, this.utf16Length);
				} catch (final IOException e) {
					throw new TextSpillException(
							"spill済みテキストpayloadの読み出しに失敗しました: record=" + this.recordId, e);
				}
			}
		}
	}

	/**
	 * ブロックの終了です。
	 */
	public record EndBlock() implements Event {
	}

	/**
	 * 範囲replay不能マーカーです(recipe化に対応していないボックス種別
	 * ——{@code StyleBuilder.boxKind}がnullを返すもの、および未知の
	 * {@code AbstractReplacedBox}サブクラスのfail closed)。
	 * ログの完全性(正直な全記録)のために
	 * 位置を占有し、範囲にこれを含む再生要求はフォールバックさせます。
	 * 常に{@link EndBlock}と対を成す開始イベントとして積まれます
	 * ({@code compact}/{@code endOf}の開閉対称性)。
	 *
	 * <p>
	 * <b>撤去対象ではない(E-6増分3b-6で明確化)</b>: live型
	 * ({@code ReplacedLive})と違い、Opaqueはreplay適格判定
	 * ({@code containsOpaque})のfail closed基盤として恒久的に必要
	 * ——「変換できないものは正直にマークして範囲ごとフォールバック」
	 * が、silent hole(内容の黙失)を構造的に防ぐ。recipe化対応が
	 * 広がるほど発生頻度は下がるが、型自体は残る。
	 * </p>
	 *
	 * <p>
	 * <b>発生源の実測(F-4、2026-07-25。{@code files/unittest}全数
	 * 436文書)</b>: 計319件で、内訳は表本体310+表キャプション9のみ
	 * (キャプションは必ず表の内側にあるので前者の真部分集合)。
	 * 未知の{@code AbstractReplacedBox}サブクラスは現存4実装では
	 * 構造的にゼロ。ルビ由来160件は2026-07-25の注釈付きテキスト化で
	 * 消滅した。よって「Opaqueの残存源=表」であり、
	 * {@link BoxKind#TABLE}の記録条件を正すことが唯一の削減手段。
	 * </p>
	 */
	public record Opaque() implements Event {
	}

	private record Entry(long id, Event event) {
	}

	private final List<Entry> entries = new ArrayList<Entry>();

	private long nextId = 0;

	/**
	 * text payloadのinline保持予算の既定値(bytes)です(E-6増分3b-2)。
	 * {@code UAProps.PROCESSING_TEXT_SPILL_BUDGET}の既定値と同値。
	 * コーパス実測(SOURCE_EVENT_HIGH_WATER=数千イベント規模の文書が
	 * 大半)より十分大きく、通常文書ではspillは起きない。
	 */
	public static final long DEFAULT_TEXT_SPILL_BUDGET_BYTES = 8L * 1024L * 1024L;

	/** inline text payloadの予算(bytes)。超過後の新規Charsはspillされる。 */
	private final long textSpillBudgetBytes;

	/**
	 * 現在entriesが保持しているinline text payloadの累積bytes
	 * (UTF-16見積り: char数×2)。append時に加算し、compactでinline
	 * Charsが破棄されたとき減算する——判定はこの値と設定予算のみで行う
	 * 決定的なもの(heap残量参照は禁止——codex設計§2.1)。
	 */
	private long liveInlineTextBytes = 0;

	/** spillストア(最初に必要になったとき遅延生成。{@link #close()}で削除)。 */
	private TextSpill textSpill = null;

	/**
	 * E-6増分2(2026-07-24): 追記イベントのshadow観測フック(テスト
	 * 専用、LayoutSourceTestHooks経由で設定)。production経路では常に
	 * null——nullのときの挙動は増分前と完全に同一。レイアウトが別
	 * スレッドで走る構成(large stack)があるためvolatile。
	 */
	static volatile java.util.function.Consumer<Event> appendObserver;

	/** 既定予算({@link #DEFAULT_TEXT_SPILL_BUDGET_BYTES})で作ります。 */
	public LayoutSource() {
		this(DEFAULT_TEXT_SPILL_BUDGET_BYTES);
	}

	/**
	 * text payloadのinline保持予算(bytes)を指定して作ります
	 * (E-6増分3b-2。productionはStyleBuilderが
	 * {@code processing.text-spill-budget}を渡す)。
	 */
	public LayoutSource(final long textSpillBudgetBytes) {
		this.textSpillBudgetBytes = textSpillBudgetBytes;
	}

	/**
	 * イベントを追記し、その EventId を返します。
	 */
	public long append(final Event event) {
		final long id = this.nextId++;
		this.entries.add(new Entry(id, event));
		// E-6増分3b-2: inline text payloadの予算会計(append/compactで対称)
		if (event instanceof Chars chars && chars.payload() instanceof TextPayload.Inline inline) {
			this.liveInlineTextBytes += (long) inline.utf16Length() * 2;
			ContinuationStats.recordLiveTextPayloadBytes(this.liveInlineTextBytes);
		}
		// E-6増分1(2026-07-24): 保持量のhigh-water観測のみ(挙動不変)
		ContinuationStats.recordSourceEventRetention(this.entries.size());
		// E-6増分2(2026-07-24): shadow観測のみ(挙動不変)
		final java.util.function.Consumer<Event> observer = appendObserver;
		if (observer != null) {
			observer.accept(event);
		}
		return id;
	}

	/**
	 * テキストイベントを追記し、その EventId を返します(E-6増分3b-2)。
	 * inline保持量({@code liveInlineTextBytes})+今回分が予算内なら
	 * 配列コピーをinline保持し、予算を超える追記は{@link TextSpill}へ
	 * 書く(「予算超過後の新規Charsはspillで書く」——sealed済みの古い
	 * inline chunkを後からspillする複雑さは作らない。compactでinline分が
	 * 解放されれば以降の追記は再びinlineに戻るため、inline保持量は常に
	 * 予算以下に保たれる)。
	 *
	 * <p>
	 * spillの書き込み失敗は{@link TextSpillException}(型付きレイアウト
	 * 失敗)として伝播する——黙殺やinline継続へのフォールバックはしない
	 * (spill成否で出力・メモリ挙動が変わる非決定性を作らない)。
	 * </p>
	 */
	public long appendChars(final int charOffset, final char[] ch, final int off, final int len, final boolean fixed) {
		final long bytes = (long) len * 2;
		final TextPayload payload;
		if (this.liveInlineTextBytes + bytes <= this.textSpillBudgetBytes) {
			final char[] copy = new char[len];
			System.arraycopy(ch, off, copy, 0, len);
			payload = new TextPayload.Inline(copy);
		} else {
			try {
				if (this.textSpill == null) {
					this.textSpill = TextSpill.open();
				}
				final long recordId = this.textSpill.append(ch, off, len);
				payload = new TextPayload.Spilled(this.textSpill, recordId, len);
			} catch (final IOException e) {
				throw new TextSpillException(
						"テキストpayloadのspill書き込みに失敗しました: charOffset=" + charOffset + ", length=" + len, e);
			}
			ContinuationStats.recordTextSpill(bytes);
		}
		return this.append(new Chars(charOffset, payload, fixed));
	}

	/**
	 * text payloadのspillストアを閉じ、一時ファイルを削除します(冪等。
	 * E-6増分3b-2)。LayoutSourceの寿命の終端——変換の終了経路
	 * (StyleBuilder.finish、および例外時も通るformatterのfinally→
	 * CSSProcessor.dispose)——で必ず呼ばれる。close後にSpilled payloadを
	 * decodeすることはできない(最終ページ確定後は再生が発生しない)。
	 */
	@Override
	public void close() {
		if (this.textSpill != null) {
			this.textSpill.close();
		}
	}

	/** spillストアを返します(未生成ならnull。テスト観測用)。 */
	public TextSpill textSpillForTest() {
		return this.textSpill;
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
	 * <p>
	 * <b>spill済みrecordとの整合(E-6増分3b-2)</b>: entriesから外れた
	 * {@link TextPayload.Spilled}のrecordは{@link TextSpill}上に残る
	 * (初版ではdisk領域の途中回収はしない——codex裁定)。これは
	 * recordIdへのheap参照が消えるだけであり、リークではない: 一時
	 * ファイル全体が{@link #close()}(変換終了経路のfinally)で削除される。
	 * inline payloadの破棄は予算会計({@code liveInlineTextBytes})から
	 * 減算され、以降の追記が再びinlineに戻れる。
	 * </p>
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
				// E-6増分3b-2: 破棄されるinline payloadを予算会計から除く
				// (Spilledのrecordは残るがリークではない——メソッドjavadoc)
				if (chars.payload() instanceof TextPayload.Inline inline) {
					this.liveInlineTextBytes -= (long) inline.utf16Length() * 2;
				}
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
		// 生きているリースの範囲は compact 後も保持されている
		assert this.retentionLeases.isEmpty() || this.indexOf(this.retentionLeases.firstKey()) >= 0
				|| this.retentionLeases.firstKey() >= this.nextId : this.retentionLeases.firstKey();
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
			// utf16LengthはSpilledでもheapメタデータ(decode不要——挙動不変)
			if (entry.event() instanceof Chars(final int off, final TextPayload payload, final boolean fixed)
					&& off >= 0 && charOffset >= off && charOffset < off + payload.utf16Length()) {
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
			case Start(final BoxRecipe recipe) -> {
				if (depth == 0 && isBlockLevel(recipe.kind())) {
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
	private static boolean isBlockLevel(final net.zamasoft.foliojet.layout.segment.BoxKind kind) {
		// フロートは段落を終わらせない(ソース位置は段落中)。尾部範囲に
		// フロートが入る場合の再生可否は呼び出し側の containsFloat ゲートが
		// 判定する(再生は係留を再実行するため二重化の危険がある)
		return switch (kind) {
		case FLOW, MULTICOL -> true;
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
			if (entry.event() instanceof Start(final BoxRecipe recipe) && recipe instanceof BoxRecipe.Multicol) {
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
			// flowOrNull()はInnerTableParams系(flowを持たない)でnull——
			// 旧liveログのparams instanceof AbstractTextParams判定と同値
			// (E-6増分3b-4、凍結済みStartから読む)
			if (entry.event() instanceof Start(final BoxRecipe recipe)
					&& recipe.flowOrNull() instanceof net.zamasoft.foliojet.layout.box.params.WritingMode flow
					&& flow.isVertical() != vertical) {
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
			// recipeのFloatBlock変種 ⇔ FloatBlockBox ⇔ FloatPos(PosType.FLOAT)
			// の1:1対応(StyleBuilder.boxKindのクラス判定とBoxRecipe.freezeが対
			// ——E-6増分3b-4、旧pos.getType()==FLOAT判定と同値)
			if (entry.event() instanceof Start(final BoxRecipe recipe) && recipe instanceof BoxRecipe.FloatBlock) {
				return true;
			}
			// recipeのFloat変種 ⇔ FloatReplacedBox ⇔ PosType.FLOAT(1:1対応。
			// ReplacedRecipe.freezeの分類とBoxRecipeBoxFactory.createReplacedが対)
			if (entry.event() instanceof Replaced(final net.zamasoft.foliojet.layout.segment.ReplacedRecipe recipe)
					&& recipe.generationKind() == net.zamasoft.foliojet.layout.segment.ReplacedRecipe.GenerationKind.FLOAT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * [fromId, toId] の範囲に絶対配置ブロックの Start が含まれていれば
	 * true を返します(E-6増分4e、2026-07-24)。
	 *
	 * <p>
	 * 増分4e以前は絶対配置が{@link Opaque}で記録されていたため、
	 * {@link #containsOpaque}が全replay経路を暗黙にフォールバックさせて
	 * いた。recipe記録化(適格化の対象は絶対配置ビルダー<b>自身の本文</b>
	 * seal)後も、絶対配置を<b>含む</b>範囲の再生は従来どおり
	 * フォールバックさせる——絶対配置はcontext builderへ係留
	 * ({@code BlockBuilder.addBound}の{@code addAbsolute})され、deferred
	 * bind(ページ末の{@code finishLayoutSelf})を持つため、範囲再生に
	 * よる再構築は「liveで係留済みの箱+再生で新造される箱」の二重登録や
	 * bindされないDeferredBind(リース取り残し)を生む。この判定は
	 * {@link #containsFloat}と同じ「係留の再実行」ゲートの絶対配置版
	 * (置換要素の絶対配置({@code ReplacedRecipe}のABSOLUTE)は増分4e
	 * 以前から再生対象で挙動実績があるため、従来どおり対象外)。
	 * </p>
	 */
	public boolean containsAbsolute(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return true;
		}
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			// recipeのAbsolute変種 ⇔ AbsoluteBlockBox ⇔ AbsolutePosの1:1対応
			// (StyleBuilder.boxKindのクラス判定とBoxRecipe.freezeが対)
			if (entry.event() instanceof Start(final BoxRecipe recipe) && recipe instanceof BoxRecipe.Absolute) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 一回の再生が読むイベント範囲の streaming ビューです(E-6増分3a、
	 * 2026-07-24: 全量 List コピーの所有から「store(本体)+範囲
	 * [fromId, toId]+保持リース」への参照ビューへ変更)。
	 *
	 * <p>
	 * <b>compact からの保護</b>: capture 時に範囲の完全性(連番で穴なし)を
	 * 検証し、自前の {@link RetentionLease}(fromId)を取得する。visitor 内の
	 * 入れ子改ページによる compact(backing list の clear+addAll)は、
	 * {@link #compact(long)} の水位 clamp によって fromId より前へ抑えられる
	 * ため、streaming 走査中に範囲内のイベントが破棄されることは構造的に
	 * ない(従来は全量コピーで隔離していた保証の置き換え。
	 * LayoutSourceTest.testReplaySurvivesNestedCompaction が固定)。
	 * 数値 index は各イベントごとに id で再検証するため、入れ子 compact で
	 * backing list が組み直されてもずれない(silent skip しない——
	 * 外部レビュー指摘の保証も維持)。
	 * </p>
	 *
	 * <p>
	 * <b>consume-once</b>: {@link #replay} は一度だけ呼べ、完了時
	 * (例外時も finally で)リースを解放する。再生せず放棄する場合は
	 * {@link #close()}(冪等)。リースを取り残すと以後の compact が永久に
	 * clamp される(保持リーク)ため、消費経路は必ずどちらかを通ること。
	 * </p>
	 */
	public final class ReplaySlice implements AutoCloseable {
		private final long fromId;
		private final long toId;
		private final RetentionLease lease;
		private boolean consumed = false;

		private ReplaySlice(final long fromId, final long toId) {
			this.fromId = fromId;
			this.toId = toId;
			this.lease = LayoutSource.this.retainFrom(fromId);
		}

		public long fromId() {
			return this.fromId;
		}

		public long toId() {
			return this.toId;
		}

		/**
		 * 範囲のイベントを1件ずつ順に visitor へ渡します(consume-once)。
		 * リースが cursor の未読範囲を compact から守るため、visitor 内の
		 * 入れ子改ページに対して安全です。完了・例外を問わずリースを
		 * 解放します。
		 */
		public void replay(final java.util.function.Consumer<Event> visitor) {
			if (this.consumed) {
				throw new IllegalStateException(
						"ReplaySlice は consume-once: [" + this.fromId + ", " + this.toId + "]");
			}
			this.consumed = true;
			try {
				final List<Entry> entries = LayoutSource.this.entries;
				int hint = -1;
				for (long id = this.fromId; id <= this.toId; ++id) {
					// visitor(入れ子 compact)が backing list を組み直して
					// いる可能性があるため、index は毎回 id で検証する
					if (hint < 0 || hint >= entries.size() || entries.get(hint).id() != id) {
						hint = LayoutSource.this.indexOf(id);
						if (hint < 0) {
							// リースが fromId 以降を守っている限り起きない
							throw new IllegalStateException("replay range lost during streaming replay: id=" + id
									+ " of [" + this.fromId + ", " + this.toId + "]");
						}
					}
					final Event event = entries.get(hint).event();
					++hint;
					visitor.accept(event);
				}
			} finally {
				this.lease.close();
			}
		}

		/**
		 * 消費せず放棄します(リース解放。冪等)。
		 */
		@Override
		public void close() {
			this.consumed = true;
			this.lease.close();
		}
	}

	/**
	 * [fromId, toId] の検証済み streaming ビューを取得します(リース付き。
	 * E-6増分3aで不変スナップショット(全量コピー)から置換)。
	 * 端が破棄済み・範囲の内部に破棄済みの穴がある・toId まで届かない
	 * 場合は null(呼び出し側の契約に応じてフォールバックまたは失敗)。
	 */
	public ReplaySlice capture(final long fromId, final long toId) {
		if (!this.isIntact(fromId, toId)) {
			return null;
		}
		return new ReplaySlice(fromId, toId);
	}

	/**
	 * [fromId, toId] が欠落なく保持されているかを返します
	 * ({@link #capture}の可否判定そのもの。リースを取らずに問い合わせる
	 * ためのもので、再生範囲を<b>記録する側</b>
	 * ({@code RootBuilder.stampRanges})が使います)。
	 *
	 * <p>
	 * <b>なぜ記録時にも要るか(2026-07-27)</b>: {@link #compact(long)}は
	 * 「開いている(未対応の)Start」だけを水位より前から残すため、
	 * 破断時にまだ開いていた要素は<b>Startだけが残り中身が消えた</b>
	 * 状態になる。その要素が後で閉じると{@link #endOf(long)}は疎な
	 * 保持列を走って一見もっともらしい終端を返すので、密度を見ない限り
	 * 「再生可能」と誤判定される。
	 * </p>
	 */
	public boolean isIntact(final long fromId, final long toId) {
		final int index = this.indexOf(fromId);
		if (index < 0 || toId < fromId) {
			return false;
		}
		// EventId は連番で付与され、破棄されても順序は保たれる(狭義単調
		// 増加)。よって entries[index].id == fromId かつ
		// entries[index + n].id == toId == fromId + n なら、その間の n+1 件は
		// 強制的に連番 == 穴なし(旧実装の1件ずつの逐次検証と等価)
		final long offset = toId - fromId;
		if (offset > this.entries.size() - 1 - index) {
			return false;
		}
		final int last = index + (int) offset;
		return this.entries.get(last).id() == toId;
	}

	/**
	 * [fromId, toId] の範囲のイベントを順に visitor へ渡します。
	 * 内部で検証済み streaming ビュー(リース付き)を取ってから駆動する
	 * ため、visitor 内の入れ子改ページ(compact)に対して安全です。
	 * 範囲が完全でなければ実行前に失敗します(フォールバック可能な
	 * 呼び出し側は {@link #capture} を使うこと)。
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
