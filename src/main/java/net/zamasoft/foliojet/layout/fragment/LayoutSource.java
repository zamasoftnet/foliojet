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
	public sealed interface Event permits Start, Replaced, Chars, EndBlock, Opaque, Leader {
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
		/**
		 * テーブル(TableBox。blockBoxはparams共有+<b>内側blockBoxのpos</b>で
		 * 再構成——外側{@code TableBox.getPos()}は常に{@code TablePos}で
		 * 配置種別を持たない)。G-1調査(2026-07-25)後に一旦撤去、表セット
		 * 実装のユーザー承認(2026-07-30、G-1裁定の更新)で復活。記録適格は
		 * {@code RecordingLayoutSink.boxKind}のTableBox分岐(exact class+
		 * params alias、fail closed)。
		 */
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
		TABLE_COLUMN,
		/**
		 * 絶対配置ブロック(AbsoluteBlockBox)。E-6増分4e(2026-07-24)で
		 * Opaque記録からrecipe記録へ昇格——末尾追加なのは既存ordinalを
		 * 変えないため({@code segment.BoxKind}と並びを揃える)。
		 */
		ABSOLUTE,
		/**
		 * Gridコンテナ(GridBox。Grid G0c、2026-07-31——
		 * consult-codex-2026-07-31-grid.txt §3.7。末尾追加は既存ordinal
		 * 維持のため)。
		 */
		GRID,
		/**
		 * 表キャプション(FlowBlockBox+TableCaptionPos。caption recipe化
		 * C1、2026-08-01——consult-codex-2026-08-01-caption-recipe.txt)。
		 * 文脈依存kind: 再生には同一範囲内で先行するTABLE Startの確立が
		 * 必要で、範囲の根にはなれない(C1ではcontainsCaptionの一律
		 * ゲートで再生対象外、C2でcontext-complete検証へ置換予定)。
		 */
		CAPTION,
		/**
		 * Flexコンテナ(FlexBox。Flex F0c、2026-08-02——
		 * consult-codex-2026-08-02-flexbox.txt。末尾追加は既存ordinal
		 * 維持のため)。
		 */
		FLEX;
	}

	/**
	 * ボックスの開始です(E-6増分3b-4、2026-07-24: live params/pos参照の
	 * 保持から記録時freezeのrecipe保持へ置換)。記録時
	 * ({@code StyleBuilder.startBox})に{@link BoxRecipe#freeze}で凍結され、
	 * 再生は{@code BoxRecipeBoxFactory.create(BoxRecipe)}のmaterializeで
	 * 新品のボックスを作る——liveのparams/pos({@code CSSElement}グラフ
	 * 含む)はログに残らない。freezeは{@code StyleBuilder.boxKind}が
	 * 非nullを返す全14 kind(E-6増分4eでABSOLUTE、表セットでTABLE追加)をカバーする総関数
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
	 * {@code leader()}です(css-content-3、
	 * consult-codex-2026-07-31-leader.txt L1)。payloadは正規化済み
	 * パターン文字列のみ——shape・幅の割り付けは再生のたびに
	 * {@code StyledTextUnitizer.leader}が行うため、再生間で可変状態を
	 * 共有しない(LeaderQuadは駆動ごとに新規生成)。
	 */
	public record Leader(String pattern) implements Event {
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
		this.indexEvent(id, event); // RangeSummary(2026-08-01)
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
	/**
	 * 範囲適格判定の疎な逆引き索引です(RangeSummary、2026-08-01——
	 * エレガンス改善B)。「特別イベント」(Opaque・CAPTION・TABLE・float・
	 * absolute・multicol・grid・縦横flow開始)のidだけをカテゴリ別に保持し、
	 * {@code containsX(from,to)}を範囲の線形走査からO(log k)の二分探索へ
	 * 置き換える(kはカテゴリ内件数——通常ページあたり数個)。
	 *
	 * <p>
	 * idは追記順に単調増加なのでリストは常にソート済み。メモリは
	 * 「特別イベント数×8B」のみで、全イベント並走の累積配列(約40B/
	 * イベント)を避けた——メモリ効率化の指示と両立する設計。
	 * {@code compact()}はkept再構築に相乗りして索引も作り直す。
	 * 線形実装との等価性は{@code LayoutSourceTest}の乱数プロパティテストが
	 * 固定する。
	 * </p>
	 */
	private static final class SparseIndex {
		private long[] ids = new long[4];
		private int size = 0;

		void add(final long id) {
			if (this.size == this.ids.length) {
				this.ids = java.util.Arrays.copyOf(this.ids, this.size * 2);
			}
			this.ids[this.size++] = id;
		}

		void clear() {
			this.size = 0;
		}

		/** [fromId, toId] に1件でもあるか(両端含む)。 */
		boolean anyInRange(final long fromId, final long toId) {
			int low = 0, high = this.size - 1;
			// fromId以上の最初の位置
			while (low <= high) {
				final int mid = (low + high) >>> 1;
				if (this.ids[mid] < fromId) {
					low = mid + 1;
				} else {
					high = mid - 1;
				}
			}
			return low < this.size && this.ids[low] <= toId;
		}
	}

	private final SparseIndex opaqueIds = new SparseIndex();
	private final SparseIndex captionIds = new SparseIndex();
	private final SparseIndex tableIds = new SparseIndex();
	private final SparseIndex multicolIds = new SparseIndex();
	private final SparseIndex gridIds = new SparseIndex();
	private final SparseIndex flexIds = new SparseIndex();
	private final SparseIndex absoluteIds = new SparseIndex();
	private final SparseIndex floatIds = new SparseIndex();
	private final SparseIndex verticalFlowIds = new SparseIndex();
	private final SparseIndex horizontalFlowIds = new SparseIndex();

	/** 追記/再構築時のカテゴリ分類です(containsX系と同じ検出条件)。 */
	private void indexEvent(final long id, final Event event) {
		switch (event) {
		case Opaque opaque -> this.opaqueIds.add(id);
		case Start(final BoxRecipe recipe) -> {
			switch (recipe) {
			case BoxRecipe.Caption c -> this.captionIds.add(id);
			case BoxRecipe.Table t -> this.tableIds.add(id);
			case BoxRecipe.Multicol m -> this.multicolIds.add(id);
			case BoxRecipe.Grid g -> this.gridIds.add(id);
			case BoxRecipe.Flex f -> this.flexIds.add(id);
			case BoxRecipe.Absolute a -> this.absoluteIds.add(id);
			case BoxRecipe.FloatBlock f -> this.floatIds.add(id);
			default -> {
			}
			}
			if (recipe.flowOrNull() instanceof net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
				(flow.isVertical() ? this.verticalFlowIds : this.horizontalFlowIds).add(id);
			}
		}
		case Replaced(final net.zamasoft.foliojet.layout.segment.ReplacedRecipe recipe) -> {
			if (recipe.generationKind() == net.zamasoft.foliojet.layout.segment.ReplacedRecipe.GenerationKind.FLOAT) {
				this.floatIds.add(id);
			}
		}
		default -> {
		}
		}
	}

	/** {@code compact()}後の索引再構築です(kept走査に相乗り)。 */
	private void rebuildIndexes() {
		this.opaqueIds.clear();
		this.captionIds.clear();
		this.tableIds.clear();
		this.multicolIds.clear();
		this.gridIds.clear();
		this.absoluteIds.clear();
		this.floatIds.clear();
		this.verticalFlowIds.clear();
		this.horizontalFlowIds.clear();
		for (int i = 0; i < this.entries.size(); ++i) {
			final Entry entry = this.entries.get(i);
			this.indexEvent(entry.id(), entry.event());
		}
	}

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
			case Leader leader -> {
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
		this.rebuildIndexes(); // RangeSummary(2026-08-01)
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
			case Leader leader -> {
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
			case Leader leader -> {
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
		// TABLE: 表は段落を終わらせるブロック級(G-1実装と同一。旧Opaque
		// 記録時代は++depth貫通だったが、尾部が表を含めばcontainsTable/
		// containsOpaqueゲートが再生を拒否していたため、早期停止で尾部を
		// 表の手前で切る方が適格範囲が広がるだけで出力は不変——G-1が
		// 436文書byte-parityで実証済み)
		// CAPTION: 表キャプションも段落を終わらせるブロック級(caption
		// recipe化C4——depth 0のCAPTION Startを尾部が含むと、キャプション
		// 全体がtext-tail範囲へ紛れ込む。表と同じく早期停止で手前で切る)
		// FLEX: Flexコンテナも段落を終わらせるブロック級(Flex F0c。
		// GRIDと同じ扱い)
		case FLOW, MULTICOL, TABLE, GRID, CAPTION, FLEX -> true;
		default -> false;
		};
	}

	/**
	 * [fromId, toId] の範囲に Opaque(再生非対応)イベントが
	 * 含まれていれば true を返します。
	 */
	public boolean containsOpaque(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.opaqueIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲に表キャプション({@link BoxKind#CAPTION})の
	 * Start が含まれていれば true を返します(caption recipe化C1、
	 * 2026-08-01——consult-codex-2026-08-01-caption-recipe.txt)。
	 *
	 * <p>
	 * キャプションは文脈依存kind(再生に囲みTableBuilderが必要)のため、
	 * C1では従来のOpaque記録と同じ範囲を同じ判定で弾く(routing不変)。
	 * C2でcontext-complete検証(範囲内に対応するTABLE Startの確立)へ
	 * 置換する。なお{@code replayTextTail}の尾部範囲はこの検査を
	 * 持たない——キャプションStartへ到達する前に必ずTABLE Start
	 * (block級)で{@code tailBound}が停止するため構造的に含まれない。
	 * </p>
	 */
	public boolean containsCaption(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.captionIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲が文脈依存kindについて自己完結しているかを
	 * 返します(caption recipe化C2、2026-08-01——
	 * consult-codex-2026-08-01-caption-recipe.txt Q1)。単なる
	 * 「根がCAPTIONでない」より強い検証で、次を全て要求する:
	 *
	 * <ul>
	 * <li>各CAPTION Startの時点で、範囲内で開いた明示的なTABLEが
	 * スタック上にある(CAPTIONが範囲の根になる経路を構造的に禁止——
	 * G-1の単独replay根クラッシュの再発防止)</li>
	 * <li>範囲の終端で、範囲内から始まったboxが開いたまま残っていない
	 * (CAPTIONを途中で切る範囲の禁止)</li>
	 * <li>範囲内に対応の取れないEndBlockがない</li>
	 * </ul>
	 *
	 * <p>
	 * 疎な範囲の検出は{@link #isIntact}の担当(呼び出し側が合成する)。
	 * Opaqueは「未知の開始イベント」としてスタックに積むがTABLEを確立
	 * しない(fail closed)。C2ではshadow観測のみに使い、実routingは
	 * {@link #containsCaption}の一律拒否のまま——C4で実ゲートへ昇格する。
	 * </p>
	 */
	/**
	 * キャプションの一律ゲート+観測です(ページ破断頻度の再生経路用——
	 * {@code stampRanges}/{@code canReplayChildren})。範囲がキャプションを
	 * 含むなら常にtrue(=box-restyleへ)。
	 *
	 * <p>
	 * <b>C4で実ゲート化を試み、撤回した(2026-08-01実測)</b>: キャプション
	 * 付き多ページ表(0217のthead/tfoot反復、100px頁)をページ破断のたびに
	 * 表全体replayする形になり、Java heap spaceで変換死(tableReplays
	 * 37→3891)。破断頻度で駆動される経路の解禁は、replay一回性が保証される
	 * bind経路と性質が違う——bind一回のTwoPass seal側だけ実ゲート
	 * ({@link #captionSealGate})とし、こちらは恒久的に一律拒否+観測に
	 * 留める。
	 * </p>
	 */
	public boolean observeCaptionGate(final long fromId, final long toId) {
		if (!this.containsCaption(fromId, toId)) {
			return false;
		}
		if (this.isContextCompleteRange(fromId, toId)) {
			ContinuationStats.CAPTION_CONTEXT_ACCEPTS.incrementAndGet();
		} else {
			ContinuationStats.CAPTION_ROOT_REJECTS.incrementAndGet();
		}
		return true;
	}

	/**
	 * キャプションの実ゲートです(bind一回性のあるTwoPass seal専用、C4)。
	 * キャプションを含む範囲でも、context-complete検証(範囲内に対応する
	 * TABLE Startの確立・完全閉鎖)を満たせば吸収・range bindを許可する。
	 * CAPTION単独根・途中切断の形(G-1のクラッシュ形)は恒久的にreject。
	 */
	public boolean captionSealGate(final long fromId, final long toId) {
		if (!this.containsCaption(fromId, toId)) {
			return false;
		}
		if (this.isContextCompleteRange(fromId, toId)) {
			ContinuationStats.CAPTION_CONTEXT_ACCEPTS.incrementAndGet();
			return false;
		}
		ContinuationStats.CAPTION_ROOT_REJECTS.incrementAndGet();
		return true;
	}

	public boolean isContextCompleteRange(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return false;
		}
		// 範囲内で開いたkind列(CAPTIONに対するTABLEの確立を問う)
		final java.util.ArrayDeque<net.zamasoft.foliojet.layout.segment.BoxKind> stack = new java.util.ArrayDeque<>();
		int tableDepth = 0;
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			switch (entry.event()) {
			case Start(final BoxRecipe recipe) -> {
				final net.zamasoft.foliojet.layout.segment.BoxKind kind = recipe.kind();
				if (kind == net.zamasoft.foliojet.layout.segment.BoxKind.CAPTION && tableDepth == 0) {
					// 範囲内にTABLEの確立がないCAPTION(単独根・表の外)
					return false;
				}
				if (kind == net.zamasoft.foliojet.layout.segment.BoxKind.TABLE) {
					++tableDepth;
				}
				stack.push(kind);
			}
			case Opaque opaque -> {
				// 未知の開始イベントはスタック対応を保証できない——fail
				// closedで不適格(Opaque範囲はいずれにせよcontainsOpaqueが弾く)
				return false;
			}
			case EndBlock end -> {
				if (stack.isEmpty()) {
					// 範囲外で開いたboxを閉じるEndBlock(範囲がboxを跨ぐ)
					return false;
				}
				if (stack.pop() == net.zamasoft.foliojet.layout.segment.BoxKind.TABLE) {
					--tableDepth;
				}
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			case Leader leader -> {
			}
			}
		}
		// 範囲内から始まったboxが全て閉じていること
		return stack.isEmpty();
	}

	/**
	 * [fromId, toId] の範囲に Grid の Start が含まれていれば true を
	 * 返します(Grid G1d、2026-07-31)。TwoPass計測はGridBuilder不活性
	 * (G0)、DocumentBuilder経由の範囲再生は活性のため、両者が混ざる
	 * 消費側(range seal・実測計測)はGridを含む範囲をフォールバック
	 * させる({@code ContinuationStats.TwoPassSealReject.GRID_RANGE})。
	 */
	public boolean containsGrid(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.gridIds.anyInRange(fromId, toId);
	}

	/**
	 * [fromId, toId] の範囲に Flex の Start が含まれていれば true を
	 * 返します(Flex F0c、2026-08-02)。F0時点ではFlexBuilderが存在せず
	 * 再生は挙動同一だが、F1d以降scratch再生でFlexBuilderが活性化する
	 * 一方TwoPass本経路は縮退のまま——Grid G1dと同じ機序を先回りで
	 * fail closedに塞ぐ。
	 */
	public boolean containsFlex(final long fromId, final long toId) {
		// fromId不在時のfail closed(true)はcontainsGridと同じ
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.flexIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲にマルチカラムの Start が含まれていれば
	 * true を返します(M6c: 段組内容の再生は列機構(columnBreak/balance)
	 * との相互作用が未検証のためフォールバックさせる)。
	 */
	public boolean containsMulticol(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.multicolIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲に、指定の書字方向と異なる内容が含まれていれば
	 * true を返します(M6b: 縦横混在の再生はサブビルダー文脈の再現が
	 * 未設計のためフォールバックさせる)。
	 */
	public boolean containsMixedFlow(final long fromId, final long toId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		// RangeSummary(2026-08-01): 「targetと違う向きのflow開始が範囲内に
		// あるか」——照会されるのは常に文書主方向と逆側の索引で、それは
		// 典型文書では疎(横文書なら縦flow開始、縦文書なら横flow開始)
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return (rootFlow.isVertical() ? this.horizontalFlowIds : this.verticalFlowIds).anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲に浮動配置の Start が含まれていれば
	 * true を返します(M6c: バランスのソース再生はフロートの係留の
	 * 再現が未検証のためフォールバックさせる)。
	 */
	public boolean containsFloat(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.floatIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲に表({@link BoxKind#TABLE})の Start が
	 * 含まれていれば true を返します(G-1、2026-07-25。表セット実装の
	 * ユーザー承認——2026-07-30——で復活)。
	 *
	 * <p>
	 * <b>なぜ必要か(G-1実測)</b>: 表のrecipe記録化で「範囲を再生できる」
	 * ようになっても、<b>再生してよいか</b>は消費側ごとに別問題である。
	 * {@code MeasuredIntrinsics}(実レイアウト実測)は、表を含む範囲を
	 * 通し始めると固有寸法が「模倣計測」から「∞幅scratchページへの実
	 * source replay計測」へ<b>アルゴリズムごと切り替わり</b>、出力が壊れる
	 * ——実測では{@code 0070-table-layout/float-in-auto-4.html}の
	 * shrink-to-fitフロート幅が376/414.5/276/216 → 全て500pt(=ページ幅)へ
	 * 発散した(∞幅ページでは表の%指定セルが1e6基準で解決されるため)。
	 * E-6増分4eが絶対配置に対して{@link #containsAbsolute}で行った
	 * 切り分けと同型のゲート。表replay消費者(T-c)が解禁するのは
	 * {@code restyleItem case TABLE}の直接replayだけで、これらの間接
	 * 消費側は明示的に段階解禁するまで表を含む範囲を通さない。
	 * </p>
	 */
	public boolean containsTable(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.tableIds.anyInRange(fromId, toId);
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
		// RangeSummary(2026-08-01): 線形走査から疎索引の二分探索へ。
		// fromId不在時のfail closed(true)は従来どおり
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.absoluteIds.anyInRange(fromId, toId);
	}


	/**
	 * [fromId, toId] の範囲内の全絶対配置Start({@code BoxRecipe.Absolute})が
	 * {@code ownedAnchors}と<b>完全一致</b>するかを返します(absolute吸収=
	 * codex増分9、2026-07-30。副作用なし)。
	 *
	 * <p>
	 * {@code containsAbsolute}(1件でもあればfalse=fail closed)の
	 * 「全件owned(親recordsが排他所有を証明済み)なら許可、1件でも
	 * unmatchedならreject」への置換に使う。unmatchedなStartは「外側
	 * contextまたは別実行計画(表セル等)に属するabsolute」の兆候であり、
	 * 吸収すると係留の二重化またはリースの孤児化を生む。範囲先頭が
	 * 失われている(compact済み)場合もfalse。
	 * </p>
	 */
	public boolean absoluteStartsExactly(final long fromId, final long toId,
			final java.util.Set<Long> ownedAnchors) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return false;
		}
		int matched = 0;
		for (; index < this.entries.size(); ++index) {
			final Entry entry = this.entries.get(index);
			if (entry.id() > toId) {
				break;
			}
			if (entry.event() instanceof Start(final BoxRecipe recipe) && recipe instanceof BoxRecipe.Absolute) {
				if (!ownedAnchors.contains(entry.id())) {
					return false;
				}
				++matched;
			}
		}
		// ownedAnchorsの全IDが範囲内の実Absolute Startであること
		// (収集側が範囲・種別を検証済みだが、二重防壁として数で照合)
		return matched == ownedAnchors.size();
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
