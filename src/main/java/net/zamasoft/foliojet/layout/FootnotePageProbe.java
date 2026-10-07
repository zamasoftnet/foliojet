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
 * B, driven once from the start of input to EOF. Does not attach MEASURE while C delivers events.
 * Exceptions from B or the reporting listener propagate to the input source. DirectSession defaults to conversion
 * failure, and dispose reclaims unfinished resources. Observation exceptions are never swallowed to continue.
 */
public final class FootnotePageProbe {
	/** Inner dimensions resolved from page rules, before reservation. Does not share page trees or counters. */
	public record PageGeometry(double width, double height, net.zamasoft.foliojet.layout.box.params.WritingMode flow) { }
	/** Borrows C's initial geometry before reservation. Queries again after a name transition; retains no mutable page tree. */
	public record PageStart(UserAgent ua, BlockParamsTemplate template, double width, double height, String pageName) {
		public static PageStart capture(final PageBox page, final double width, final double height, final String pageName) {
			return new PageStart(page.getUserAgent(), BlockParamsTemplate.freeze(page.getBlockParams()), width, height, pageName);
		}
	}

	/**
	 * A test observer that receives the reclaimed safe point after each delivery. Only resourcesBeforeReclaim
	 * is the registration count just before that reclamation, or 0 when closed. resources also includes moving pins.
	 */
	public record Retention(boolean closed, boolean inputFinished, int pages, long nextId, long pin,
			long unfinishedFrom, int resources, int leases, long currentBytes, int compactionRequests,
			int unfinishedTables, int structureTokens, int resourcesBeforeReclaim, int footnoteLedgerSize,
			long mainGeneration, int retainedReports) { }
	static volatile java.util.function.Consumer<Retention> retentionObserver;

	/** C's undelivered queue and main log. windowDeliveries counts generation waits advanced by input position. */
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

	/** Can acquire a pin without attaching, even during the first C input. */
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

	/** Queries with B's page name and number of emitted pages. Does not advance C's pages. */
	public void setPageGeometry(final java.util.function.BiFunction<String, Integer, PageGeometry> geometry) {
		this.pageGeometry = geometry;
	}

	/** Also samples the sink's retained reports at B's reclamation safe point. */
	public void observeReports(final java.util.function.LongSupplier generation, final java.util.function.IntSupplier reports) {
		this.mainGeneration = generation;
		this.retainedReports = reports;
	}

	/** The caller's width when the note recipe is frozen. */
	public double namedPageWidth() {
		return this.generator == null ? Double.NaN : this.generator.namedPageWidth();
	}

	private void start() {
		this.generator = new MeasurePageGenerator(this.ua, this.start.template(), this.start.width(), this.start.height(), this.source);
		// C has already set the html page name before creating the initial page. Start with the same name
		// to prevent a spurious name transition before opening the root flow and a B/C generation mismatch.
		this.generator.setPageName(this.start.pageName());
		this.generator.setPageGeometry(this.pageGeometry);
		this.start = null;
		this.generator.setPageObserver(this::pageDrawn);
		this.generator.setCompactionObserver(watermark -> this.pageFrom = Math.min(this.pageFrom, watermark));
		this.root = new RootBuilder(this.generator, BreakableBuilder.MODE_PAGE_BREAK);
		this.doc = new DocumentBuilder(this.generator, this.root);
		this.generator.setDeliveredEventEnd(() -> this.eventId + 1);
	}

	/** Checks B's own live boundary before recording the real event. Attaches only for this call. */
	public LayoutSource.Event preDispatch(final DocumentBuilder.DispatchEvent type, final IBox box, final long nextId) {
		try (final ScratchReplayScope scope = this.owner.attach()) {
			if (this.doc == null) this.start();
			return this.doc.preDispatch(type, box, nextId);
		} catch (final RuntimeException failure) {
			throw new FootnoteProbeException(failure);
		}
	}

	/** Delivers fresh instances after recording boundaries and real events. Allows multiple page breaks within one event. */
	public void deliver(final DocumentBuilder.DispatchEvent type, final LayoutSource.Event event,
			final long id, final LayoutSource.Event boundary) {
		try (final ScratchReplayScope scope = this.owner.attach()) {
			if (this.doc == null) this.start();
			this.eventId = id;
			this.deliveryStart = boundary == null ? id : id - 1;
			++this.deliveredEvents;
			final IBox fresh = this.materialize(event, id);
			// Use the original anchor acquired by preDispatch; B also opens and closes through the normal raw-event path.
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

	/** Reclaims only completed MEASURE ownership, retaining unfinished hosts, page-break remainders, and events in delivery. */
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
			// Share structural tokens only within the replay session, as in SegmentExecutor.
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

	/** Finishes normally only after all End deliveries; reports the final and note-only pages through normal draw. */
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

	/** A lifetime endpoint separate from page reporting. Cleans up owners even on exception, without sealing unfinished hosts. */
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
