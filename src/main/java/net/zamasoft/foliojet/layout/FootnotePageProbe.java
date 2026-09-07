package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.ScratchOwner;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 入力先頭からEOFまで一度ずつ駆動するB。Cの配達中にはMEASUREを接続しません。
 * B・報告listenerの例外は入力元へ伝播します。DirectSessionの既定では変換失敗となり、
 * disposeが未完資源を回収します。観測の例外を握りつぶして続行はしません。
 */
public final class FootnotePageProbe {
	/** ページ規則から解決した予約前の内寸。ページ木・カウンタは共有しません。 */
	public record PageGeometry(double width, double height, net.zamasoft.foliojet.layout.box.params.WritingMode flow) { }
	/** Cから初回の予約前幾何を借ります。名前遷移後は別途照会し、可変ページ木は保持しません。 */
	public record PageStart(UserAgent ua, BlockParamsTemplate template, double width, double height, String pageName) {
		public static PageStart capture(final PageBox page, final double width, final double height, final String pageName) {
			return new PageStart(page.getUserAgent(), BlockParamsTemplate.freeze(page.getBlockParams()), width, height, pageName);
		}
	}

	/**
	 * 各配達後の回収済み安全点を渡す試験用観測です。resourcesBeforeReclaimだけは
	 * 同じ回収の直前の登録数で、closed時は0。resourcesには移動pinも含みます。
	 */
	public record Retention(boolean closed, boolean inputFinished, int pages, long nextId, long pin,
			long unfinishedFrom, int resources, int leases, long currentBytes, int compactionRequests,
			int unfinishedTables, int structureTokens, int resourcesBeforeReclaim, int footnoteLedgerSize,
			long mainGeneration, int retainedReports) { }
	static volatile java.util.function.Consumer<Retention> retentionObserver;

	/** Cの未配達キューと主ログ。windowDeliveriesは世代待ちを入力位置で進めた件数。 */
	public record WindowRetention(long currentBytes, int deliveries, long reportGeneration, long mainGeneration,
			long windowDeliveries, LayoutSource.RetentionSnapshot source, long sourceBytes, int retainedReports) { }

	private final UserAgent ua;
	private final LayoutSource source;
	private final ScratchOwner owner;
	private final LayoutSource.CompactionCheckpoint compaction;
	private final java.util.function.Consumer<FootnotePageProbeReport> listener;
	private PageStart start;
	private DocumentBuilder doc;
	private RootBuilder root;
	private MeasurePageGenerator generator;
	private java.util.function.BiFunction<String, Integer, PageGeometry> pageGeometry;
	private java.util.function.LongSupplier mainGeneration = () -> 1;
	private java.util.function.IntSupplier retainedReports = () -> 0;
	private final java.util.Map<Long, net.zamasoft.foliojet.layout.segment.StructureToken> structureTokens = new java.util.HashMap<>();
	private final java.util.Deque<Long> tokenKeys = new java.util.ArrayDeque<>();
	private long deliveredEvents;
	private long eventId = -1, deliveryStart, pageFrom;
	private int charOffset, pages;
	private boolean inputFinished, closed;

	/** 最初のC入力の途中でも、接続せずにpinを取得できます。 */
	public FootnotePageProbe(final PageStart start, final LayoutSource source) {
		this(start, source, start.ua().getUAContext().getFootnotePageProbeListener());
	}

	public FootnotePageProbe(final PageStart start, final LayoutSource source,
			final java.util.function.Consumer<FootnotePageProbeReport> listener) {
		this.ua = start.ua();
		this.listener = listener;
		this.source = source;
		this.start = start;
		this.owner = new ScratchOwner(this.ua.getRetainedTextLimit(), "footnote-probe-page");
		this.owner.retainFrom(source, 0);
		this.compaction = source.checkpointCompaction();
		this.ua.getUAContext().footnotePageProbeCreated();
	}

	/** Bのページ名と出力済み枚数で照会します。Cのページ進行は起こしません。 */
	public void setPageGeometry(final java.util.function.BiFunction<String, Integer, PageGeometry> geometry) {
		this.pageGeometry = geometry;
	}

	/** Bの回収安全点でsinkの報告保持量も同時に採ります。 */
	public void observeReports(final java.util.function.LongSupplier generation, final java.util.function.IntSupplier reports) {
		this.mainGeneration = generation;
		this.retainedReports = reports;
	}

	/** 注のrecipeを凍結する時点の呼び出し側の幅です。 */
	public double namedPageWidth() {
		return this.generator == null ? Double.NaN : this.generator.namedPageWidth();
	}

	private void start() {
		this.generator = new MeasurePageGenerator(this.ua, this.start.template(), this.start.width(), this.start.height(), this.source);
		// Cはhtmlのpage名を初回ページの生成前に設定済み。同じ名前から始め、
		// ルートflowを開く前の偽の名前遷移と、B/Cの世代ずれを防ぐ。
		this.generator.setPageName(this.start.pageName());
		this.generator.setPageGeometry(this.pageGeometry);
		this.start = null;
		this.generator.setPageObserver(this::pageDrawn);
		this.generator.setCompactionObserver(watermark -> this.pageFrom = Math.min(this.pageFrom, watermark));
		this.root = new RootBuilder(this.generator, BreakableBuilder.MODE_PAGE_BREAK);
		this.doc = new DocumentBuilder(this.generator, this.root);
		// Cの終端やChars全長ではなく、Bのshaperが配達した現在値で打ち切る。
		this.generator.setDeliveredCharEnd(this.doc::getDeliveredCharEnd);
		this.generator.setDeliveredEventEnd(() -> this.eventId + 1);
	}

	/** B自身のlive境界を、実イベントの記録前に判定します。接続はこの呼び出しだけです。 */
	public LayoutSource.Event preDispatch(final DocumentBuilder.DispatchEvent type, final IBox box, final long nextId) {
		try (final ScratchReplayScope scope = this.owner.attach()) {
			if (this.doc == null) this.start();
			return this.doc.preDispatch(type, box, nextId);
		} catch (final RuntimeException failure) {
			throw new FootnoteProbeException(failure);
		}
	}

	/** 境界・実イベントの記録後に新品を配達する。1イベント内の複数改頁も止めません。 */
	public void deliver(final DocumentBuilder.DispatchEvent type, final LayoutSource.Event event,
			final long id, final LayoutSource.Event boundary) {
		try (final ScratchReplayScope scope = this.owner.attach()) {
			if (this.doc == null) this.start();
			this.eventId = id;
			this.deliveryStart = boundary == null ? id : id - 1;
			++this.deliveredEvents;
			final IBox fresh = this.materialize(event, id);
			// preDispatchが確保した元anchorを使い、Bも生イベントの通常経路で開閉する。
			this.dispatch(event, id, fresh);
			this.charOffset = Math.max(this.charOffset, this.doc.getDeliveredCharEnd());
		} catch (final RuntimeException failure) {
			throw new FootnoteProbeException(failure);
		}
		this.reclaim();
	}

	private void pageDrawn(final MeasurePageGenerator.PageMeasurement page) {
		this.pages = (int) page.generation();
		this.pageFrom = this.deliveryStart;
		final int position = this.doc == null ? this.charOffset : Math.max(this.charOffset, this.doc.getDeliveredCharEnd());
		final long bytes = this.owner.finishPage();
		if (this.listener != null) this.listener.accept(new FootnotePageProbeReport(page.generation(), page.pageName(), page.emitted(),
				this.eventId, position, page.innerWidth(), page.innerHeight(), page.flow(), page.callIds(),
				page.heights(), page.unmeasuredIds(), page.h0(), this.deliveredEvents,
				page.lastPage() ? FootnotePageProbeReport.Completion.END_OF_INPUT : FootnotePageProbeReport.Completion.DRAW_PAGE, bytes));
	}

	/** 未完宿主・改頁残余・配達中イベントを残し、終了したMEASURE所有だけを回収します。 */
	private void reclaim() {
		try {
			this.reclaimResources();
		} catch (final RuntimeException failure) {
			throw new FootnoteProbeException(failure);
		}
	}

	private void reclaimResources() {
		final long unfinished = this.doc.oldestUnfinishedSourceId();
		final int resourcesBeforeReclaim = this.owner.registeredResourceCount();
		this.owner.reclaimCompleted();
		final long from = Math.min(Math.min(this.pageFrom, unfinished), this.owner.oldestOpenSourceId(this.source));
		this.owner.retainFrom(this.source, from);
		this.root.reclaimProbeFootnotes(this.owner.retainedFrom());
		this.compaction.reapply();
		this.observeRetention(unfinished, this.doc.getOpenRetainedTableCount(), resourcesBeforeReclaim);
	}

	private void observeRetention(final long unfinished, final int tables, final int resourcesBeforeReclaim) {
		final var observer = retentionObserver;
		if (observer != null) observer.accept(new Retention(this.closed, this.inputFinished,
				this.generator == null ? this.pages : this.generator.getPageCount(), this.source.nextId(),
				this.owner.retainedFrom(), unfinished, this.owner.registeredResourceCount(), this.owner.retainedLeaseCount(),
				this.owner.currentBytes(), this.compaction.pendingRequestCount(), tables, this.structureTokens.size(), resourcesBeforeReclaim,
				this.root == null ? 0 : this.root.probeFootnoteLedgerSize(), this.mainGeneration.getAsLong(), this.retainedReports.getAsInt()));
	}

	private IBox materialize(final LayoutSource.Event event, final long id) {
		final IBox box = switch (event) {
		case LayoutSource.Start start -> BoxRecipeBoxFactory.create(start.recipe());
		case LayoutSource.Replaced replaced -> BoxRecipeBoxFactory.createReplaced(replaced.recipe());
		default -> null;
		};
		if (box != null) {
			// SegmentExecutorと同じ、再生セッション内だけの構造token共有。
			final var params = box.getParams();
			if (params.element instanceof net.zamasoft.foliojet.layout.segment.StructureToken token
					&& token.elementKey() >= 0) {
				params.element = event instanceof LayoutSource.Start
						? this.structureTokens.computeIfAbsent(token.elementKey(), key -> token)
						: this.structureTokens.getOrDefault(token.elementKey(), token);
			}
			box.setSourceAnchor(id);
		}
		return box;
	}

	private void dispatch(final LayoutSource.Event event, final long id, final IBox fresh) {
		switch (event) {
		case LayoutSource.Start start -> {
			final INonReplacedBox box = (INonReplacedBox) (fresh == null ? this.materialize(event, id) : fresh);
			this.tokenKeys.push(box.getParams().element instanceof net.zamasoft.foliojet.layout.segment.StructureToken token
					? token.elementKey() : -1L);
			this.doc.startBox(box);
		}
		case LayoutSource.Replaced replaced -> this.doc.addReplacedBox((AbstractReplacedBox) (fresh == null ? this.materialize(event, id) : fresh));
		case LayoutSource.EndBlock end -> {
			this.doc.endBox();
			final long key = this.tokenKeys.pop();
			if (key >= 0 && !this.tokenKeys.contains(key)) this.structureTokens.remove(key);
		}
		case LayoutSource.AnonymousItemStart start -> this.doc.startAnonymousItem(start.anchor());
		case LayoutSource.AnonymousItemEnd end -> this.doc.endAnonymousItem();
		case LayoutSource.Chars chars -> {
			final char[] ch = chars.payload().freshChars();
			this.doc.characters(chars.charOffset(), ch, 0, ch.length, chars.fixed());
		}
		case LayoutSource.Leader leader -> this.doc.addLeader(leader.pattern());
		case LayoutSource.Assignment assignment -> { }
		case LayoutSource.Opaque opaque -> throw new IllegalStateException("probeへ再生不能の置換要素が届きました: " + id);
		}
	}

	/** 全End配達後だけ正常終了し、最終ページ・note-onlyページも通常のdrawで報告します。 */
	public void finishInput(final boolean completeStructure) {
		if (completeStructure) {
			try (final ScratchReplayScope scope = this.owner.attach()) {
				if (this.doc == null) this.start();
				this.doc.end();
				this.inputFinished = true;
			} catch (final RuntimeException failure) {
				throw new FootnoteProbeException(failure);
			}
			this.reclaim();
		}
	}

	/** ページ報告とは別の寿命終端。未完宿主をsealせず、例外時も所有者を清算します。 */
	public void discard() {
		if (this.closed) return;
		this.closed = true;
		if (this.generator != null) this.pages = this.generator.getPageCount();
		final int tables = this.doc == null ? 0 : this.doc.getOpenRetainedTableCount();
		try {
			if (this.doc != null) this.doc.discard();
		} finally {
			try {
				this.owner.release();
			} finally {
				this.doc = null;
				this.root = null;
				this.generator = null;
				this.start = null;
				this.structureTokens.clear();
				this.tokenKeys.clear();
				try {
					this.compaction.close();
				} finally {
					this.observeRetention(Long.MAX_VALUE, tables, 0);
					this.pageGeometry = null;
					this.mainGeneration = () -> 1;
					this.retainedReports = () -> 0;
				}
			}
		}
	}
}
