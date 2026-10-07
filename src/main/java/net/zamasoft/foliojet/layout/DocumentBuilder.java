package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TableBuilderHost;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilderLifecycle;
import net.zamasoft.foliojet.layout.builder.impl.GridBuilder;
import net.zamasoft.foliojet.layout.builder.impl.GridBuilderLifecycle;
import net.zamasoft.foliojet.layout.builder.impl.IncrementalTableBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.builder.impl.StyledTextUnitizer;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: DocumentBuilder.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class DocumentBuilder implements TableBuilderHost {
	/** A test observation point confirming that an absolute table passed through the TABLE entry point. */
	static volatile java.util.function.Consumer<TableBox> absoluteTableObserver;

	public static final byte PAGE_MODE_CONTINUOUS = 1;
	public static final byte PAGE_MODE_NO_BREAK = 1 << 1;
	
	private final boolean normalizeText;

	protected static class ContainerBuilderEntry {
		public final Builder builder;

		protected StyledTextUnitizer styledTextAtomizer = null;

		public ContainerBuilderEntry(Builder builder) {
			this.builder = builder;
		}

		/**
		 * Returns the interface for text output.
		 *
		 * @return
		 */
		public StyledTextUnitizer getStyledTextUnitizer() {
			if (this.styledTextAtomizer == null) {
				this.styledTextAtomizer = new StyledTextUnitizer(this.builder);
			}
			return this.styledTextAtomizer;
		}
	}

	/** The page generator. */
	private final PageGenerator pageGenerator;
	/** Connects this owner when creating and feeding scratch state that can be discarded midway. */
	private final net.zamasoft.foliojet.layout.fragment.ScratchOwner scratchOwner;
	private boolean discarded;

	/** Attaches the same document and ownership state to recipe-construction failures as to seal rejections. */
	public String sourceOwnerContext() {
		final Builder builder = this.builderStack.isEmpty() ? null : this.containerBuilder().builder;
		return "uri=" + this.pageGenerator.getUserAgent().getDocumentContext().getBaseURI()
				+ " owner state=" + (builder instanceof TwoPassBlockBuilder twoPass ? twoPass.ownershipState()
						: builder == null ? "NO_BUILDER" : builder.getClass().getSimpleName());
	}

	private byte pageMode = 0;

	private final List<INonReplacedBox> boxStack = new ArrayList<INonReplacedBox>();

	private final List<Object> builderStack = new ArrayList<Object>();

	/** Maps non-root entries on builderStack to the Root owning their translation-prohibition scopes. */
	private final java.util.IdentityHashMap<Object, RootBuilder> translateScopeRoots = new java.util.IdentityHashMap<>();

	private final List<Object> inlineStack = new ArrayList<Object>();

	private final List<Object> columnSpanStack = new ArrayList<Object>();

	/** During temporary measurement, does not consume nested bodies either and skips out-of-page anchoring. */
	private final ReplayIntent replayIntent;

	/** For AnchorMode.NONE only. Not shared with main-document anchors or LayoutSource. */
	private boolean replayOnly;
	private SegmentEvent replayEvent;
	private long replayOrdinal = -1;
	/** The Grid/Flex root whose Start/End lie outside the range when replaying only a child range. */
	private AbstractContainerBox replayItemHost;

	/**
	 * Begins one independent replay event. Appends to existing bodies first;
	 * start/endContainerBuilder adjusts boundaries for bodies opened or closed by this event.
	 */
	public void startReplayOnlyEvent(final SegmentEvent event, final long ordinal) {
		this.requireNotDiscarded();
		this.replayOnly = true;
		this.replayEvent = event;
		this.replayOrdinal = ordinal;
		for (final Object item : this.builderStack) {
			if (item instanceof ContainerBuilderEntry entry && entry.builder instanceof TwoPassBlockBuilder body) {
				body.recordReplayOnlyEvent(event, ordinal);
			}
		}
	}

	/**
	 * The matching end operation prevents cleanup outside the executor
	 * from being confused with the preceding event boundary.
	 */
	public void finishReplayOnlyEvent() {
		this.requireNotDiscarded();
		this.replayEvent = null;
		this.replayOrdinal = -1;
	}

	public DocumentBuilder(PageGenerator pageGenerator) {
		this.pageGenerator = pageGenerator;
		this.scratchOwner = pageGenerator instanceof MeasurePageGenerator
				? net.zamasoft.foliojet.layout.fragment.ScratchReplayScope.currentOwner() : null;
		this.normalizeText = UAProps.INPUT_NORMALIZE_TEXT.getBoolean(pageGenerator.getUserAgent());
		this.replayIntent = ReplayIntent.current();
	}

	/**
	 * Creates a document builder targeting an existing root builder for source replay of
	 * the remainder after a page break (M6b v3). Replays the recorded protocol with fresh state,
	 * without touching the live DocumentBuilder's unitizer or container state.
	 */
	public DocumentBuilder(PageGenerator pageGenerator, BlockBuilder existingRoot) {
		this(pageGenerator, existingRoot, ReplayIntent.current());
	}

	/** A replay builder that explicitly distinguishes actual placement from temporary measurement. */
	public DocumentBuilder(final PageGenerator pageGenerator, final BlockBuilder existingRoot, final ReplayIntent intent) {
		this.pageGenerator = pageGenerator;
		this.scratchOwner = pageGenerator instanceof MeasurePageGenerator
				? net.zamasoft.foliojet.layout.fragment.ScratchReplayScope.currentOwner() : null;
		this.normalizeText = UAProps.INPUT_NORMALIZE_TEXT.getBoolean(pageGenerator.getUserAgent());
		this.replayIntent = java.util.Objects.requireNonNull(intent);
		this.startContainerBuilder(existingRoot);
		if (this.startItemCoordinator(existingRoot, existingRoot.getRootBox())) {
			this.replayItemHost = existingRoot.getRootBox();
			this.boxStack.add(this.replayItemHost);
		}
		this.startContainer();
	}

	/** Finishes source replay and closes text contexts symmetrically (M6b v3). */
	public void finishReplay() {
		this.requireNotDiscarded();
		if (this.replayItemHost != null) {
			this.finishItemCoordinator(this.replayItemHost);
			final INonReplacedBox popped = this.boxStack.remove(this.boxStack.size() - 1);
			assert popped == this.replayItemHost : "再生根の終端でboxStack末尾が一致しません";
			this.replayItemHost = null;
		}
		this.endContainer();
	}

	/**
	 * Returns the end of delivered source characters in the current container (M6b v3).
	 * Undelivered characters in the shaper lie beyond this point.
	 */
	public int getDeliveredCharEnd() {
		if (this.builderStack.isEmpty()) {
			return 0;
		}
		return this.containerBuilder().getStyledTextUnitizer().getDeliveredCharEnd();
	}

	/**
	 * Ends source replay while leaving the builder's text block open
	 * (only flushes the shaper's pending content; the following SAX stream enters the same text block).
	 */
	public void finishReplayKeepText() {
		this.requireNotDiscarded();
		this.containerBuilder().getStyledTextUnitizer().flushText();
	}

	public void setPageMode(byte pageMode) {
		this.requireNotDiscarded();
		this.pageMode = pageMode;
	}

	public byte getPageMode() {
		return this.pageMode;
	}

	private void requirePage() {
		this.requireNotDiscarded();
		if (!this.builderStack.isEmpty()) {
			return;
		}
		byte mode = (this.pageMode & (PAGE_MODE_CONTINUOUS | PAGE_MODE_NO_BREAK)) != 0 ? BreakableBuilder.MODE_NO_BREAK
				: BreakableBuilder.MODE_PAGE_BREAK;
		BlockBuilder builder = new RootBuilder(this.pageGenerator, mode);
		this.startContainerBuilder(builder);
		this.startContainer();
	}

	/** For documents using read-ahead, establishes only the first page's geometry before opening child boxes. */
	public void prepareFootnotePage() {
		if (this.pageGenerator.isFootnotePageProbeEnabled()) this.requirePage();
	}

	public long getPageGeneration() {
		return this.builderStack.isEmpty() ? 1 : this.pageContext().getPageGeneration();
	}

	/** Immediately before the queue's first delivery. Child sizes on the first page remain undetermined until here. */
	public void startFootnoteInput() {
		this.pageContext().startFootnoteInput();
	}

	private void startContainerBuilder(final Builder builder) {
		this.startContainerBuilder(builder, false);
	}

	private void startContainerBuilder(final Builder builder, final boolean includeOpeningEvent) {
		if (this.replayOnly && builder instanceof TwoPassBlockBuilder body) {
			body.startReplayOnly(this.pageGenerator);
			if (includeOpeningEvent && this.replayEvent != null) {
				body.recordReplayOnlyEvent(this.replayEvent, this.replayOrdinal);
			}
		}
		final ContainerBuilderEntry entry = new ContainerBuilderEntry(builder);
		if (this.builderStack.isEmpty()) {
			this.builderStack.add(entry);
			return;
		}
		this.pushScopedBuilder(entry);
	}

	/** Pushes a non-root builder and opens a translation-prohibition scope with the same lifetime. */
	private void pushScopedBuilder(final Object entry) {
		final RootBuilder root = this.pageContext();
		if (root != null) {
			root.enterTranslateBlockScope();
			this.translateScopeRoots.put(entry, root);
		}
		try {
			this.builderStack.add(entry);
		} catch (RuntimeException | Error e) {
			this.finishTranslateBlockScope(entry);
			throw e;
		}
	}

	/** Always closes the scope of a non-root builder after finish/bind completes. */
	private void finishTranslateBlockScope(final Object entry) {
		if (entry instanceof ContainerBuilderEntry container && container.builder instanceof BlockBuilder block) {
			block.finishRetainedContext();
		}
		final RootBuilder root = this.translateScopeRoots.remove(entry);
		if (root != null) {
			root.exitTranslateBlockScope();
		}
	}

	private ContainerBuilderEntry containerBuilder() {
		int index = this.builderStack.size() - 1;
		Object o = this.builderStack.get(index);
		while (o instanceof TableBuilder || o instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator) {
			// Place inline, block, text, etc. inside a table but outside cells before the table,
			// following common browser behavior.
			// Skipping GridBuilder is a safety net (the recommendation said skipping was unnecessary,
			// but routing escaped paths to the host is more robust than a failed cast).
			// The three entry points, startBox/characters/addReplacedBox, turn all content that
			// must be captured into items, so only unwired paths reach here.
			--index;
			assert index >= 0 : "builderStack が全て TableBuilder で、周囲のコンテナが見つかりません";
			o = this.builderStack.get(index);
		}
		return (ContainerBuilderEntry) o;
	}

	/** Records outer placement events in the container queue only when paragraph UBA is enabled. */
	private void noteBidiBarrier(final Object payload) {
		if (!this.builderStack.isEmpty() && this.containerBuilder().builder instanceof BlockBuilder blockBuilder) {
			blockBuilder.noteBidiBarrier(payload);
		}
	}

	private ContainerBuilderEntry contextBuilder() {
		for (int i = this.builderStack.size() - 1; i >= 0; --i) {
			Object entry = this.builderStack.get(i);
			if (entry instanceof ContainerBuilderEntry) {
				return (ContainerBuilderEntry) entry;
			}
		}
		throw new ArrayIndexOutOfBoundsException("builderStack に ContainerBuilderEntry がありません: " + this.builderStack);
	}

	/**
	 * Returns the root of the builder stack (normally the page context's {@code RootBuilder}).
	 * Used as the parent of the builder that two-pass construction binds to.
	 *
	 * <p>
	 * 2026-07-24 (M6c-5): relaxed the type from {@code RootBuilder} to {@link BlockBuilder}.
	 * Live construction always has a {@code RootBuilder} at the stack root, so behavior is unchanged,
	 * but rootless source replay could make the old cast throw {@code ClassCastException}.
	 * All callers use it only as {@code LayoutStack} or via {@code getRootBox()}.
	 * </p>
	 */
	private BlockBuilder pageContextBuilder() {
		return (BlockBuilder) ((ContainerBuilderEntry) this.builderStack.get(0)).builder;
	}

	/**
	 * Returns the page ledger ({@code RootBuilder}), or {@code null} if absent.
	 *
	 * <p>
	 * In live construction, the stack root itself is a {@code RootBuilder}, but <b>table-cell
	 * and absolutely positioned box contents are rebuilt by source replay</b>, whose root is
	 * the cell's bind-target {@code BlockBuilder}. Previously, when the root was not
	 * {@code RootBuilder}, footnotes, page floats, and parallel notes were silently discarded
	 * instead of passed to the ledger. Thus, <b>footnote bodies inside table cells appeared nowhere,
	 * and call numbers remained document-wide sequence numbers</b> (cti.li report, 2026-09-01).
	 * Following {@code LayoutStack} from the root reaches the actual ledger. The caller already
	 * excludes scratch measurement.
	 * </p>
	 */
	private RootBuilder pageContext() {
		final BlockBuilder root = this.pageContextBuilder();
		return root instanceof RootBuilder r ? r : root.getPageContext();
	}

	/** Selects multi-column guidance at note start using the same eligibility check as placement. */
	public boolean isEligibleFootnoteColumnOwner() {
		if (this.builderStack.isEmpty()) return false;
		final Builder parent = this.containerBuilder().builder;
		final RootBuilder root = this.pageContext();
		return root != null && root.isEligibleFootnoteColumnOwner(parent, RootBuilder.footnoteColumnOwner(parent));
	}

	private ContainerBuilderEntry endContainerBuilder() {
		return this.endContainerBuilder(false);
	}

	private ContainerBuilderEntry endContainerBuilder(final boolean includeClosingEvent) {
		ContainerBuilderEntry entry = this.containerBuilder();
		try {
			if (this.replayOnly && entry.builder instanceof TwoPassBlockBuilder body) {
				body.finishReplayOnly(this.replayOrdinal, includeClosingEvent);
			}
			if (!entry.builder.isTwoPass()) {
				((BlockBuilder) entry.builder).close();
			}
			// Invariant: the entry found by containerBuilder() must be the last entry on builderStack.
			// If a TableBuilder remains at the end, removing the last element
			// removes a different entry from the one returned by containerBuilder(),
			// silently corrupting the stack: another example of the builderStack fragility found
			// while investigating the standalone table-caption replay crash (2026-07-18).
			assert this.builderStack.get(this.builderStack.size() - 1) == entry : //
			"containerBuilder() の結果が末尾要素と一致しません: entry=" + entry + ", stack=" + this.builderStack;
			this.builderStack.remove(this.builderStack.size() - 1);
			return entry;
		} catch (RuntimeException | Error e) {
			this.finishTranslateBlockScope(entry);
			throw e;
		}
	}

	private TableBuilder tableBuilder() {
		Object top = this.builderStack.get(this.builderStack.size() - 1);
		// Invariant: when this method is called, a corresponding TABLE box must already
		// have started and pushed a TableBuilder in the same replay/construction session.
		// Otherwise, the end of builderStack remains an ordinary
		// ContainerBuilderEntry and the cast fails (actually occurred in the standalone
		// table-caption source-replay crash, 2026-07-18; fixed).
		// Caption recipes C2 (2026-08-01): even in production with assertions disabled,
		// stop with a typed regular runtime exception instead of an unexplained ClassCastException.
		// (The primary G-1 prevention is context-complete validation of range eligibility
		// and SegmentExecutor's kind stack; this is the final defense.)
		if (!(top instanceof TableBuilder tableBuilder)) {
			throw new IllegalStateException(
					"表構造の外(先行する TABLE 開始イベントなし)で TABLE_CELL/TABLE_ROW/CAPTION 系ボックスを"
							+ "構築しようとしました。単独ソース再生の対象になっていないか確認してください: top=" + top);
		}
		return tableBuilder;
	}

	private TableBuilder endTableBuilder() {
		final Object top = this.builderStack.isEmpty() ? null : this.builderStack.get(this.builderStack.size() - 1);
		try {
			assert top instanceof TableBuilder : //
			"閉じるべき TableBuilder が builderStack の末尾にありません: " + this.builderStack;
			return (TableBuilder) this.builderStack.remove(this.builderStack.size() - 1);
		} catch (RuntimeException | Error e) {
			if (top != null) {
				this.finishTranslateBlockScope(top);
			}
			throw e;
		}
	}

	/**
	 * Returns the {@link GridBuilder} waiting for the next content directly under a Grid
	 * (the last boxStack entry is that GridBox) (Grid G1b, 2026-07-31:
	 * consult-codex-2026-07-31-grid-g1.txt §3). Applies when the last builderStack entry
	 * is the GridBuilder itself, or an open item entry immediately above a GridBuilder.
	 * Nested content inside an item is excluded because the last box is not the GridBox.
	 */
	private net.zamasoft.foliojet.layout.builder.ItemCoordinator coordinatorAwaitingDirectChild() {
		if (this.boxStack.isEmpty() || this.builderStack.isEmpty()) {
			return null;
		}
		final Object tail = this.boxStack.get(this.boxStack.size() - 1);
		final int index = this.builderStack.size() - 1;
		final Object top = this.builderStack.get(index);
		if (top instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
			return c.getItemHostBox() == tail ? c : null;
		}
		if (top instanceof ContainerBuilderEntry && index > 0
				&& this.builderStack.get(index - 1) instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c //
				&& c.hasOpenItem() && c.getItemHostBox() == tail) {
			return c;
		}
		return null;
	}

	/** Returns the coordinator to close at the end of {@code box}. */
	private net.zamasoft.foliojet.layout.builder.ItemCoordinator coordinatorEndingAt(final IBox box) {
		final int index = this.builderStack.size() - 1;
		final Object top = this.builderStack.get(index);
		if (top instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c && c.getItemHostBox() == box) {
			return c;
		}
		if (top instanceof ContainerBuilderEntry && index > 0
				&& this.builderStack.get(index - 1) instanceof net.zamasoft.foliojet.layout.builder.ItemCoordinator c //
				&& c.hasOpenItem() && c.getItemHostBox() == box) {
			return c;
		}
		return null;
	}

	/** Pushes the same coordinator for live construction and child-range replay without Start. */
	private boolean startItemCoordinator(final Builder builder, final AbstractContainerBox box) {
		if (box instanceof GridBox grid && GridBuilderLifecycle.eligible(grid, builder)) {
			this.pushScopedBuilder(GridBuilderLifecycle.start(builder, grid));
			return true;
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox flex
				&& FlexBuilderLifecycle.eligible(flex, builder)) {
			this.pushScopedBuilder(FlexBuilderLifecycle.start(builder, flex));
			return true;
		}
		return false;
	}

	/** Closes and places items while the host flow is active, then releases the scope opened at start. */
	private void finishItemCoordinator(final IBox box) {
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator ending = this.coordinatorEndingAt(box);
		if (ending == null) {
			return;
		}
		this.closeAnonymousItem(ending);
		try {
			final Object popped = this.builderStack.remove(this.builderStack.size() - 1);
			assert popped == ending : "coordinator終端でbuilderStack末尾が一致しません: " + popped;
			ending.finish();
		} finally {
			this.finishTranslateBlockScope(ending);
		}
	}

	/** Records page-margin-note entries in the subtree for all open Grids. */
	private void notePageMarginNoteInGrids() {
		for (final Object entry : this.builderStack) {
			if (entry instanceof GridBuilder grid) {
				grid.notePageMarginNote();
			}
		}
	}

	/** The live input kind. Determines positioning kind from the actual box before freeze (also supports Opaque). */
	public enum DispatchEvent { START_BOX, REPLACED, TEXT, LEADER, END_BOX }

	/** Synthetic Start reserved in the first stage of live recording. Actual opening/closing occurs in the existing dispatch. */
	private long pendingAnonymousAnchor = -1;

	/**
	 * Determines only anonymous boundaries before appending the actual event. Does not write to the log;
	 * the sink appends the returned value before the actual event. SegmentExecutor does not call this live-only entry point.
	 */
	public net.zamasoft.foliojet.layout.fragment.LayoutSource.Event preDispatch(
			final DispatchEvent event, final IBox box, final long nextId) {
		this.requireNotDiscarded();
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator c = this.coordinatorAwaitingDirectChild();
		if (c == null) {
			return null;
		}
		final boolean opens = switch (event) {
		case TEXT, LEADER -> true;
		case START_BOX, REPLACED -> box.getPos().getType() == net.zamasoft.foliojet.layout.box.params.PosType.INLINE;
		case END_BOX -> false;
		};
		if (opens && !c.hasOpenItem()) {
			this.pendingAnonymousAnchor = nextId;
			return new net.zamasoft.foliojet.layout.fragment.LayoutSource.AnonymousItemStart(nextId);
		}
		final boolean closes = switch (event) {
		case START_BOX -> switch (box.getPos().getType()) {
			case FLOW, TABLE -> true;
			default -> false;
		};
		case REPLACED -> box.getPos().getType() == net.zamasoft.foliojet.layout.box.params.PosType.FLOW;
		case END_BOX -> c.getItemHostBox() == this.boxStack.get(this.boxStack.size() - 1);
		case TEXT, LEADER -> false;
		};
		return closes && c.hasOpenItem() && !c.hasOpenElementItem()
				? new net.zamasoft.foliojet.layout.fragment.LayoutSource.AnonymousItemEnd() : null;
	}

	/** Replays synthetic boundaries. A standalone item bind has the existing item box as root, so does not reopen it. */
	public void startAnonymousItem(final long anchor) {
		this.requireNotDiscarded();
		if (this.coordinatorAwaitingDirectChild() == null && !this.isItemReplayTarget()) {
			throw new IllegalStateException("coordinatorのない匿名項目Start: anchor=" + anchor);
		}
		this.requireCoordinatorAnonymousItem(anchor, false);
	}

	public void endAnonymousItem() {
		this.requireNotDiscarded();
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator c = this.coordinatorAwaitingDirectChild();
		if (c != null) {
			this.closeAnonymousItem(c);
		} else if (!this.isItemReplayTarget()) {
			throw new IllegalStateException("coordinatorのない匿名項目End");
		}
	}

	private boolean isItemReplayTarget() {
		if (!this.boxStack.isEmpty() || this.builderStack.size() != 1
				|| !(this.builderStack.get(0) instanceof ContainerBuilderEntry entry)
				|| !(entry.builder instanceof BlockBuilder)) {
			return false;
		}
		return entry.builder.getRootBox() instanceof net.zamasoft.foliojet.layout.box.impl.GridItemBox
				|| entry.builder.getRootBox() instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
	}

	/** Closes an open anonymous item (for direct text). Does not apply to element items. */
	private void closeAnonymousItem(final net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
		if (c.hasOpenItem() && !c.hasOpenElementItem()) {
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			try {
				c.itemClosed();
			} finally {
				this.finishTranslateBlockScope(entry);
			}
		}
	}

	/** Prepares an anonymous item for direct text/inline content immediately under the coordinator. */
	private void requireCoordinatorAnonymousItem() {
		final long anchor = this.pendingAnonymousAnchor;
		this.pendingAnonymousAnchor = -1;
		this.requireCoordinatorAnonymousItem(anchor, true);
	}

	private void requireCoordinatorAnonymousItem(final long anchor, final boolean includeOpeningEvent) {
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator c = this.coordinatorAwaitingDirectChild();
		if (c != null && !c.hasOpenItem()) {
			// Synthetic anchors only identify bodies. Attaching one to the wrapper makes stampRanges
			// absorb the standalone anonymous item without its coordinator as an ordinary subtree at a page break.
			final Builder builder = c.requireAnonymousItem(anchor);
			this.startContainerBuilder(builder, includeOpeningEvent);
			this.startContainer();
		}
	}

	/**
	 * Opens an element item directly under a Grid (closes any open anonymous item).
	 * {@code spec} is an explicit-placement snapshot from the authored child's FlowPos
	 * (G4a: consult-codex-2026-07-31-grid-g4.txt Q1).
	 */
	private GridBuilder startGridElementItem(final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec,
			final long sourceAnchor) {
		return this.startGridElementItem(spec, -1, sourceAnchor);
	}

	private GridBuilder startGridElementItem(final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec,
			final double minContributionCap, final long sourceAnchor) {
		if (this.coordinatorAwaitingDirectChild() instanceof GridBuilder grid) {
			this.closeAnonymousItem(grid);
			this.startContainerBuilder(grid.startElementItem(spec, minContributionCap, sourceAnchor), true);
			this.startContainer();
			return grid;
		}
		return null;
	}

	/**
	 * Determines the cap on a Grid item's inline min-content contribution (2026-08-19,
	 * automatic minimum size in css-grid §6.6; see {@code GridItemContent.minContributionCap}).
	 * Zero for scroll containers; uses the inline minimum size if explicitly declared
	 * (reuses FlexItemSpec's F1a check) and ABSOLUTE. Otherwise unlimited (-1).
	 * Percentage minima do not count because the reference is indefinite (same convention as IntrinsicMeasurer).
	 */
	private static double gridItemMinContributionCap(final IBox box) {
		final net.zamasoft.foliojet.layout.box.params.BlockParams params;
		if (box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox c) {
			params = c.getBlockParams();
		} else if (box instanceof TableBox table) {
			params = table.getBlockBox().getBlockParams();
		} else {
			return -1;
		}
		if (params.overflow.clipsPaint()) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexSpec = box
				.getPos() instanceof FlowPos flowPos ? flowPos.flexItem : null;
		if (flexSpec == null) {
			return -1;
		}
		final boolean vertical = params.flow.isVertical();
		final boolean minAuto = vertical ? flexSpec.minHeightAuto() : flexSpec.minWidthAuto();
		if (minAuto) {
			return -1;
		}
		final net.zamasoft.foliojet.layout.box.params.Dimension minSize = params.minSize;
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = params.flow;
		if (minSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			return Math.max(0, minSize.getLineLength(flow));
		}
		return -1;
	}

	/** Extracts explicit placement settings from a box (auto for positions without FlowPos). */
	private static net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItemSpecOf(final IBox box) {
		if (box.getPos() instanceof FlowPos flowPos) {
			return flowPos.gridItem;
		}
		if (box instanceof TableBox table && table.getBlockBox().getPos() instanceof FlowPos flowPos) {
			return flowPos.gridItem;
		}
		return net.zamasoft.foliojet.layout.box.params.GridItemSpec.AUTO;
	}

	/** Closes one element item (shared by the one-shot path and post-child-endBox processing). */
	private void endCoordinatorElementItem(final net.zamasoft.foliojet.layout.builder.ItemCoordinator c) {
		this.endContainer();
		final ContainerBuilderEntry entry = this.endContainerBuilder(true);
		try {
			c.itemClosed();
		} finally {
			this.finishTranslateBlockScope(entry);
		}
	}

	/**
	 * Returns the GridBuilder if its takeover element item originating from {@code box}
	 * is open at the end (G7, 2026-08-29: same form as {@link #flexItemEndingAt}).
	 */
	private GridBuilder gridItemEndingAt(final IBox box) {
		final int index = this.builderStack.size() - 1;
		if (index > 0 && this.builderStack.get(index) instanceof ContainerBuilderEntry
				&& this.builderStack.get(index - 1) instanceof GridBuilder grid //
				&& grid.isElementItemSource(box)) {
			return grid;
		}
		return null;
	}

	/**
	 * Returns the FlexBuilder if its takeover element item originating from {@code box}
	 * is open at the end (Flex F1d: matching the authored box's endBox).
	 */
	private FlexBuilder flexItemEndingAt(final IBox box) {
		// Takeover (transferring an authored box to an item box) is Flex-specific,
		// so it is outside coordinator generalization.
		final int index = this.builderStack.size() - 1;
		if (index > 0 && this.builderStack.get(index) instanceof ContainerBuilderEntry
				&& this.builderStack.get(index - 1) instanceof FlexBuilder flex //
				&& flex.isElementItemSource(box)) {
			return flex;
		}
		return null;
	}

	/**
	 * Opens an element item with a neutral wrapper directly under Flex
	 * (for non-plain children, tables, and replaced content).
	 * {@code authored} (non-null) contains the child's params; the wrapper takes over inline size settings
	 * (see {@link FlexBuilder#startNeutralElementItem}).
	 */
	private FlexBuilder startFlexNeutralElementItem(final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec,
			final FlexBuilder.NeutralTransfer authored, final long sourceAnchor) {
		if (this.coordinatorAwaitingDirectChild() instanceof FlexBuilder flex) {
			this.closeAnonymousItem(flex);
			this.startContainerBuilder(flex.startNeutralElementItem(spec, authored, sourceAnchor), true);
			this.startContainer();
			return flex;
		}
		return null;
	}

	/** Extracts flex sizing settings from a box (defaults for positions without FlowPos). */
	private static net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexItemSpecOf(final IBox box) {
		if (box.getPos() instanceof FlowPos flowPos) {
			return flowPos.flexItem;
		}
		if (box instanceof TableBox table && table.getBlockBox().getPos() instanceof FlowPos flowPos) {
			return flowPos.flexItem;
		}
		return net.zamasoft.foliojet.layout.box.params.FlexItemSpec.DEFAULT;
	}

	/**
	 * The {@link TableBuilderHost} implementation (C4-C deepening, 2026-07-19).
	 * The public path for {@link TableBuilder} implementations (currently only {@link IncrementalTableBuilder})
	 * to invoke inline-context operations needed before and after entering table cells, columns, row groups, and rows.
	 */
	@Override
	public void closeInlines(Params params) {
		this.requireNotDiscarded();
		int count = 0;

		for (int i = this.boxStack.size() - 1; i >= 0; --i) {
			final IBox box = (IBox) this.boxStack.get(i);
			if (box.getType() != BoxType.INLINE) {
				break;
			}
			this.endBox();
			this.inlineStack.add(box);
			++count;
		}
		if (count > 0) {
			this.inlineStack.add(NumberUtils.intValue(count));
			this.inlineStack.add(params);
		}
	}

	private void restoreInlines(Params params) {
		if (this.inlineStack.isEmpty() || this.inlineStack.get(this.inlineStack.size() - 1) != params) {
			return;
		}
		this.inlineStack.remove(this.inlineStack.size() - 1);
		final Integer count = (Integer) this.inlineStack.remove(this.inlineStack.size() - 1);
		for (int i = 0; i < count.intValue(); ++i) {
			InlineBox box = (InlineBox) this.inlineStack.remove(this.inlineStack.size() - 1);
			box = box.splitLine(false);
			this.startBox(box);
		}
	}

	private void startColumnSpan(FlowPos pos) {
		final Builder builder = this.containerBuilder().builder;
		if (builder.getMulticolumnBox() == null) {
			return;
		}

		// **Do not reopen inlines here** (2026-07-28).
		//
		// Previously, each iteration ended by calling {@code restoreInlines(blockBox.getParams())},
		// but this **prematurely consumed** the registration that should pair
		// with {@code closeInlines(params)} in {@code startBox(blockBox)}.
		// Its proper counterpart is {@code restoreInlines}
		// in {@code endBox(blockBox)}.
		//
		// Consuming it early leaves inlines **open** across the next iteration's
		// {@code endContainer()}. {@code endContainer()} removes the first
		// {@code textParamsStack} entry as the container's params,
		// then discards {@code textShaper} (and thus its
		// {@code InlineParamsStack}), so closing the previously open inlines
		// **misaligns three stacks simultaneously**.
		// The symptom is `Index -1` in {@code InlineParamsStack.current}
		// (WPT css-multicol/multicol-span-all-children-height-010, etc.).
		//
		// Without reopening, {@code endBox(blockBox)} consumes the registration normally,
		// following exactly the same path as the non-spanning case.
		// Accordingly, also removed {@code closeInlines} in {@code endColumnSpan},
		// the counterpart that closed the reopened inlines.
		final List<AbstractBlockBox> flows = new ArrayList<AbstractBlockBox>();
		for (;;) {
			final AbstractBlockBox blockBox = (AbstractBlockBox) builder.getFlowBox();
			flows.add(blockBox);
			if (blockBox.getColumnCount() > 1) {
				final BlockParams colParams = blockBox.getBlockParams();
				final Columns oldColumns = colParams.columns;
				colParams.columns = new Columns(colParams.columns.count, colParams.columns.width, colParams.columns.gap,
						colParams.columns.rule, Columns.FILL_BALANCE);
				this.endContainer();
				builder.endFlowBlock();
				this.startContainer();
				colParams.columns = oldColumns;
				break;
			} else {
				this.endContainer();
				builder.endFlowBlock();
				this.startContainer();
			}
		}
		this.columnSpanStack.add(flows);
		this.columnSpanStack.add(pos);
	}

	private void endColumnSpan(FlowPos pos) {
		if (this.columnSpanStack.isEmpty() || this.columnSpanStack.get(this.columnSpanStack.size() - 1) != pos) {
			return;
		}
		final Builder builder = this.containerBuilder().builder;
		this.columnSpanStack.remove(this.columnSpanStack.size() - 1);
		final List<?> flows = (List<?>) this.columnSpanStack.remove(this.columnSpanStack.size() - 1);
		for (int i = flows.size() - 1; i >= 0; --i) {
			FlowBlockBox flowBox = (FlowBlockBox) flows.get(i);
			// Since {@code startColumnSpan} no longer reopens inlines, nothing needs closing
			// again here (removed as a pair; see that function's comment for the reason).
			this.endContainer();
			if (flowBox.getColumnCount() > 1) {
				flowBox = new MulticolumnBlockBox(flowBox.getBlockParams(), flowBox.getFlowPos());
			} else {
				flowBox = new FlowBlockBox(flowBox.getBlockParams(), flowBox.getFlowPos());
			}
			builder.startFlowBlock(flowBox);
			this.startContainer();
		}
	}

	@Override
	public void startContainer() {
		this.requireNotDiscarded();
		final ContainerBuilderEntry cbe = this.containerBuilder();
		cbe.getStyledTextUnitizer().startContainer();
	}

	@Override
	public void endContainer() {
		this.requireNotDiscarded();
		final ContainerBuilderEntry cbe = this.containerBuilder();
		cbe.getStyledTextUnitizer().endContainer();
	}

	public void startBox(final INonReplacedBox box) {
		this.requireNotDiscarded();
		this.requirePage();
		// Wrap direct Grid children in items (synthetic boxes with fixed track widths)
		// before the existing switch (Grid G1b). Block-level content (FLOW/TABLE) becomes
		// element items; inlines become anonymous items. In G1, float/absolute content
		// stays in the host context without becoming items (recorded and deferred: CSS treats
		// floats as grid items, but positioning is undefined in fixed-track G1).
		switch (box.getPos().getType()) {
		case FLOW:
			// Take over plain direct block children (G7, 2026-08-29): transfer authored
			// params/pos to GridItemBox without constructing the original outer box.
			// This makes the item the authored box itself, so backgrounds and borders follow
			// stretching to the row height (the same form adopted earlier by Flex).
			if (this.coordinatorAwaitingDirectChild() instanceof GridBuilder gridHost
					&& box.getClass() == FlowBlockBox.class
					&& ((FlowBlockBox) box).getBlockParams().flow == gridHost.getGridBox().getGridParams().flow) {
				final FlowBlockBox sourceBox = (FlowBlockBox) box;
				this.closeInlines(sourceBox.getBlockParams());
				this.closeAnonymousItem(gridHost);
				this.endContainer();
				this.startContainerBuilder(gridHost.startElementItem(sourceBox, gridItemSpecOf(box),
						gridItemMinContributionCap(box)));
				this.startContainer();
				this.boxStack.add(box);
				return;
			}
			this.startGridElementItem(gridItemSpecOf(box), gridItemMinContributionCap(box), box.getSourceAnchor());
			break;
		case TABLE:
			this.startGridElementItem(gridItemSpecOf(box), gridItemMinContributionCap(box), box.getSourceAnchor());
			break;
		case INLINE:
			// Anonymous items for direct inlines are shared by Grid/Flex (coordinator generalization).
			this.requireCoordinatorAnonymousItem();
			break;
		default:
			break;
		}
		// Turn direct Flex children into items before the existing switch (Flex F1d).
		// Take over plain blocks (transfer authored params/pos to FlexItemBox
		// without building the original outer box: the recommendation's most critical prototype condition).
		// Non-plain children (tables, nested containers, vertical writing) use neutral wrappers; inlines
		// use anonymous items. float/absolute content stays in the host context, as with Grid.
		if (this.coordinatorAwaitingDirectChild() instanceof FlexBuilder flexHost) {
			switch (box.getPos().getType()) {
			case FLOW:
				if (box.getClass() == FlowBlockBox.class
						&& ((FlowBlockBox) box).getBlockParams().flow == flexHost.getFlexBox()
								.getFlexParams().flow) {
					final FlowBlockBox sourceBox = (FlowBlockBox) box;
					this.closeInlines(sourceBox.getBlockParams());
					this.closeAnonymousItem(flexHost);
					this.endContainer();
					this.startContainerBuilder(flexHost.startElementItem(sourceBox, flexItemSpecOf(box)));
					this.startContainer();
					this.boxStack.add(box);
					return;
				}
				this.startFlexNeutralElementItem(flexItemSpecOf(box),
						box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox acb
								? FlexBuilder.NeutralTransfer.of(acb.getBlockParams())
								: null, box.getSourceAnchor());
				break;
			case TABLE:
				// Do not take over table sizing; the table's own mechanism resolves it.
				this.startFlexNeutralElementItem(flexItemSpecOf(box), null, box.getSourceAnchor());
				break;
			default:
				break;
			}
		}
		switch (box.getPos().getType()) {
		case TABLE: {
			// Table.
			final TableBox tableBox = (TableBox) box;
			// Align ownership proof for absolute hosts with the table Start in both live processing and SegmentExecutor.
			// Do not attach body-replay anchors to synthetic float/inline hosts (their children are table structure).
			if (tableBox.getBlockBox() instanceof AbsoluteBlockBox absolute) {
				absolute.setSourceAnchor(tableBox.getSourceAnchor());
				final var observer = absoluteTableObserver;
				if (observer != null && this.replayIntent == ReplayIntent.MAIN
						&& this.containerBuilder().builder instanceof BlockBuilder) {
					observer.accept(tableBox);
				}
			}
			final TableParams tableParams = tableBox.getTableParams();
			final Builder builder = this.containerBuilder().builder;
			switch (tableBox.getBlockBox().getPos().getType()) {
			case FLOW:
				this.closeInlines(tableParams);
				this.endContainer();
				this.startContainer();
				break;
			}
			// Delegate builder selection (fixed/auto) and startup to
			// TableBuilderLifecycle (formerly TableLayout; C4 preparatory seam, 2026-07-19; renamed 2026-07-21). Behavior is unchanged.
			final TableBuilder tableBuilder = net.zamasoft.foliojet.layout.builder.impl.TableBuilderLifecycle.start(builder,
					tableBox);
			this.pushScopedBuilder(tableBuilder);
		}
			break;

		case TABLE_CELL:
		case TABLE_CAPTION: {
			// Table cell.
			// Caption.
			final TableBuilder tableBuilder = this.tableBuilder();
			tableBuilder.prepareEnterCell(this);
			final AbstractContainerBox containerBox = (AbstractContainerBox) box;
			final Builder newBuilder = tableBuilder.newContext(containerBox);
			this.startContainerBuilder(newBuilder);
			this.startContainer();
		}
			break;

		case TABLE_COLUMN:
		case TABLE_ROW_GROUP:
		case TABLE_ROW: {
			// Table column group.
			// Table column.
			// Table row group.
			// Table row.
			final TableBuilder tableBuilder = this.tableBuilder();
			tableBuilder.prepareEnterTrack(this);
			final AbstractInnerTableBox innerTableBox = (AbstractInnerTableBox) box;
			tableBuilder.startInnerTable(innerTableBox);
			tableBuilder.afterEnterTrack(this);
		}
			break;

		case INLINE: {
			// Inline.
			if (box.getType() == BoxType.INLINE) {
				final InlineBox inlineBox = (InlineBox) box;
				this.containerBuilder().getStyledTextUnitizer().startInline(inlineBox);
			} else {
				// Inline block.
				final InlineBlockBox inlineBlockBox = (InlineBlockBox) box;
				final Builder builder = this.containerBuilder().builder;
				final StyledTextUnitizer parentUnitizer = this.containerBuilder().getStyledTextUnitizer();
				final Builder newBuilder = builder.newBuilder(inlineBlockBox);
				this.startContainerBuilder(newBuilder);
				this.startContainer();
				if (inlineBlockBox.getBlockParams().textCombine != net.zamasoft.foliojet.css.value.TextCombineValue.NONE
						&& parentUnitizer.isCollectingRuby()) {
					// For tate-chu-yoko inside a ruby base, pass the characters to the base (ruby discards the box, 2026-10-06).
					this.containerBuilder().getStyledTextUnitizer().forwardTextCombineToRuby(parentUnitizer);
				}
			}
		}
			break;

		case FLOW: {
			// Normal-flow box.
			final FlowBlockBox blockBox = (FlowBlockBox) box;
			final BlockParams params = blockBox.getBlockParams();

			// Column spanning.
			final FlowPos pos = blockBox.getFlowPos();
			// **Close inlines first** (2026-07-28). Open inlines were opened in the pre-span
			// context and must close in that context.
			// {@code startColumnSpan} unwinds through {@code endFlowBlock}
			// to leave multi-column layout, replacing {@code containerBuilder}.
			// Thus, {@code endInline} emitted by {@code closeInlines} reaches
			// a **new StyledTextUnitizer that never saw the matching
			// {@code startInline}**. Its InlineParamsStack contains only the root,
			// so pop removes that root and
			// {@code InlineParamsStack.current} accesses an empty list
			// (10 WPT column-span:all documents failed here:
			// css-multicol/multicol-span-all-019, etc.).
			this.closeInlines(params);
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}
			this.endContainer();
			final Builder builder = this.containerBuilder().builder;
			// Blocks with intrinsic size keywords (width:max-content, etc.; 2026-08-29)
			// get their widths only after measuring content, so route them through
			// the same two-pass path as orthogonal flows (newBuilder→TwoPass→shrinkToFit).
			// Streaming constraints require buffering their contents temporarily, as for floats.
			if (params.flow.isVertical() == builder.getRootBox().getBlockParams().flow.isVertical()
					&& !blockBox.isFixedMulticolumn() && !params.hasIntrinsicLine()) {
				builder.startFlowBlock(blockBox);
				// Start the Grid itself: if eligible, push a construction coordinator and turn
				// subsequent direct children into items (Grid G1b). Otherwise, retain
				// the BlockBox-like fallback (G0).
				this.startItemCoordinator(builder, blockBox);
			} else {
				// When page progression directions differ.
				final Builder newBuilder = builder.newBuilder(blockBox);
				this.startContainerBuilder(newBuilder);
				// Grid/Flex itself becomes the TwoPass root even for orthogonal flows or intrinsic size keywords.
				this.startItemCoordinator(newBuilder, blockBox);
			}
			this.startContainer();
		}
			break;

		case FLOAT:
		case ABSOLUTE: {
			if (box.getPos().getType() == PosType.FLOAT) {
				this.containerBuilder().getStyledTextUnitizer().flushText();
			}
			// Absolute positioning.
			final AbstractBlockBox stfBox = (AbstractBlockBox) box;
			this.noteBidiBarrier(stfBox);
			final Builder builder = this.contextBuilder().builder;
			if (box.getPos().getType() == PosType.ABSOLUTE) {
				final AbsolutePos pos = (AbsolutePos) stfBox.getPos();
				if (pos.autoPosition == AutoPosition.INLINE) {
					this.containerBuilder().getStyledTextUnitizer().flushText();
					this.containerBuilder().getStyledTextUnitizer().requireTextShaper();
				}
			}
			final Builder newBuilder = builder.newBuilder(stfBox);
			this.startContainerBuilder(newBuilder);
			this.startContainer();
		}
			break;
		default:
			throw new IllegalStateException();
		}

		this.boxStack.add(box);
	}

	public void endBox() {
		this.requireNotDiscarded();
		IBox box = (IBox) this.boxStack.remove(this.boxStack.size() - 1);
		switch (box.getPos().getType()) {
		case TABLE: {
			// Table.
			final TableBuilder tableBuilder = this.endTableBuilder();
			try {
				TableBox tableBox = tableBuilder.getTableBox();
				final TableParams tableParams = tableBox.getTableParams();
				AbstractBlockBox tableBlock = tableBox.getBlockBox();
				final PosType tablePosition = tableBlock.getPos().getType();
				switch (tablePosition) {
				case FLOW:
					this.closeInlines(tableBlock.getParams());
					this.endContainer();
					break;
				case FLOAT:
					this.containerBuilder().getStyledTextUnitizer().flushText();
					break;
				}
				// FLOW finish can break pages while emitting lines. Release the original table/wrapper, no longer needed at the end.
				tableBox = null;
				box = null;
				if (tablePosition == PosType.FLOW) tableBlock = null;
				final Builder builder = this.containerBuilder().builder;
				// Delegate cleanup to TableBuilderLifecycle too (as before, ask tableBuilder itself instead of recomputing
				// conditions to match startup routing). Behavior is unchanged.
				net.zamasoft.foliojet.layout.builder.impl.TableBuilderLifecycle.finish(tableBuilder, builder);
				switch (tablePosition) {
				case FLOW:
					this.startContainer();
					this.restoreInlines(tableParams);
					break;
				case INLINE:
					this.containerBuilder().getStyledTextUnitizer()
							.addInlineBlock((InlineBlockBox) tableBlock);
					break;
				case ABSOLUTE:
					final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) tableBlock;
					if (absoluteBox.getAbsolutePos().autoPosition == AutoPosition.INLINE) {
						this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
					}
					break;
				}
			} finally {
				this.finishTranslateBlockScope(tableBuilder);
			}
		}
			break;
		case TABLE_CELL:
		case TABLE_CAPTION: {
			// Table cell.
			// Caption.
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			try {
				if (box.getPos().getType() == net.zamasoft.foliojet.layout.box.params.PosType.TABLE_CAPTION
						&& entry.builder instanceof TwoPassBlockBuilder sealable) {
					// Caption recipes C3 (2026-08-01, consult-codex-2026-08-01-
					// caption-recipe.txt): seal the range when caption-body recording finishes
					// (the same form as sealing on float/inline-block close).
					// C1 recipe recording makes endOf(anchor) available. The body
					// range [anchor+1, endId-1] excludes the box's own Start,
					// so this is not standalone CAPTION replay (G-1). The caption builder
					// itself stays in top/bottomCaptions and later undergoes
					// bind(anonBuilder) as usual; that bind becomes range-driven.
					sealable.sealBodyForRangeBind();
				} else {
					// E-6 increment 5a (2026-07-24): seal the range when cell recording finishes
					// (Retained implementation only; CellContent releases its measurement builder
					// and switches to retaining a range + lease).
					this.tableBuilder().sealCellContext(entry.builder);
				}
				assert this.builderStack.size() != 1;
			} finally {
				this.finishTranslateBlockScope(entry);
			}
		}
			break;
		case TABLE_COLUMN:
		case TABLE_ROW_GROUP:
		case TABLE_ROW: {
			// Table column group.
			// Table column.
			// Table row group.
			// Table row.
			this.tableBuilder().endInnerTable();
		}
			break;

		case INLINE: {
			if (box.getType() == BoxType.INLINE) {
				// Inline.
				this.containerBuilder().getStyledTextUnitizer().endInline();
			} else {
				// Inline block.
				this.endContainer();
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				try {
					if (entry.builder instanceof TwoPassBlockBuilder sealable) {
						// Seal the range when input completes. Ineligibility fails conversion.
						sealable.sealBodyForRangeBind();
					}
					final InlineBlockBox inlineBlockBox = (InlineBlockBox) entry.builder.getRootBox();
					final Builder parentBuilder = this.containerBuilder().builder;
					if (!parentBuilder.isTwoPass() && entry.builder.isTwoPass()) {
						// When the inline-block box width was not explicitly specified.
						final TwoPassBlockBuilder stfBuilder = (TwoPassBlockBuilder) entry.builder;
						inlineBlockBox.shrinkToFit(parentBuilder,
								this.shrinkToFitSizes(inlineBlockBox, stfBuilder, parentBuilder), false);
						final BlockBuilder inlineBlockBuilder = new BlockBuilder(this.pageContextBuilder(), inlineBlockBox);
						stfBuilder.bind(inlineBlockBuilder, this.replayIntent);
						inlineBlockBuilder.close();
					}
					this.containerBuilder().getStyledTextUnitizer().addInlineBlock(inlineBlockBox);
				} finally {
					this.finishTranslateBlockScope(entry);
				}
			}
		}
			break;

		case FLOW: {
			// End of a Flex takeover element item (Flex F1d): the authored box was not
			// constructed, so close the item instead of using the normal endFlowBlock.
			final GridBuilder gridItemHost = this.gridItemEndingAt(box);
			if (gridItemHost != null) {
				this.endContainer();
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				try {
					gridItemHost.itemClosed();
				} finally {
					this.finishTranslateBlockScope(entry);
				}
				this.startContainer();
				this.restoreInlines(box.getParams());
				break;
			}
			final FlexBuilder flexItemHost = this.flexItemEndingAt(box);
			if (flexItemHost != null) {
				this.endContainer();
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				try {
					flexItemHost.itemClosed();
				} finally {
					this.finishTranslateBlockScope(entry);
				}
				this.startContainer();
				this.restoreInlines(box.getParams());
				break;
			}
			// End of the coordinator (shared by Grid G1b/Flex F1d): close the anonymous item,
			// remove the coordinator, and finalize placement. Call finish() while the host's active
			// flow is still this container, before endFlowBlock below,
			// since item placement and parent-cursor synchronization target that flow.
			this.finishItemCoordinator(box);
			// Normal flow.
			this.endContainer();
			final FlowBlockBox blockBox = (FlowBlockBox) box;
			final Builder builder = this.containerBuilder().builder;
			if (builder.getRootBox() != box) {
				builder.endFlowBlock();
				this.startContainer();
			} else {
				final ContainerBuilderEntry entry = this.endContainerBuilder();
				try {
					final Builder parentBuilder = this.containerBuilder().builder;
					if (entry.builder instanceof TwoPassBlockBuilder sealable) {
						sealable.sealBodyForRangeBind();
					}
					if (!parentBuilder.isTwoPass()) {
						if (entry.builder.isTwoPass()) {
							// Build.
							final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
							blockBox.shrinkToFit(parentBuilder, this.shrinkToFitSizes(blockBox, contentBuilder, parentBuilder),
									false);
							final BlockBuilder bindBuilder = new BlockBuilder(this.pageContextBuilder(), blockBox);
							contentBuilder.bind(bindBuilder, this.replayIntent);
							bindBuilder.close();
						}
						parentBuilder.addBound(blockBox);
					} else if (entry.builder.isTwoPass()) {
						// If the parent is also measuring, pass the child's outer contribution to
						// the parent's intrinsic sizes as well as recording replay events. Orthogonal
						// flows in particular need conversion from the child's page axis to the parent's inline axis.
						((TwoPassBlockBuilder) parentBuilder).fitBlock((TwoPassBlockBuilder) entry.builder);
					}
				} finally {
					this.finishTranslateBlockScope(entry);
				}
				this.startContainer();
			}

			final FlowPos pos = blockBox.getFlowPos();
			// Return from column spanning.
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
			// **Restore inlines last** (2026-07-28). Since startBox orders operations as
			// closeInlines→startColumnSpan, the mirror order must be
			// endColumnSpan→restoreInlines to avoid crossed nesting.
			this.restoreInlines(box.getParams());
		}
			break;

		case FLOAT: {
			// Float.
			this.endContainer();
			final ContainerBuilderEntry entry = this.endContainerBuilder();
			try {
				if (entry.builder instanceof TwoPassBlockBuilder sealable) {
					// Seal the range when input completes. Ineligibility fails conversion.
					sealable.sealBodyForRangeBind();
				}
				final Builder parentBuilder = this.containerBuilder().builder;
				this.noteBidiBarrier(box);
				if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos pageFloatPos) {
					// Page floats (2026-08-02): separate from the body text through the same path
					// as footnotes and pass to the page ledger. In contexts without a ledger
					// (scratch measurement or replay), place nowhere, preserving measurement equivalence.
					final FloatBlockBox pageFloatBox = (FloatBlockBox) entry.builder.getRootBox();
					if (parentBuilder.isTwoPass() || this.replayIntent == ReplayIntent.MEASURE) {
						// In a TwoPass body, defer in a dedicated record and pass to the page ledger only once at bind.
						// In scratch, discard it without contributing to measurement because it is an out-of-page element.
						if (!parentBuilder.isTwoPass() && entry.builder instanceof TwoPassBlockBuilder body) {
							body.completeScratchHost();
						}
						break;
					}
					if (entry.builder.isTwoPass()) {
						final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						pageFloatBox.shrinkToFit(parentBuilder,
								this.shrinkToFitSizes(pageFloatBox, contentBuilder, parentBuilder), false);
						final BlockBuilder pageFloatBuilder = new BlockBuilder(this.pageContextBuilder(), pageFloatBox);
						// Do not consume during disposable measurement, for the same reason as floats and footnotes.
						contentBuilder.bind(pageFloatBuilder, this.replayIntent);
						pageFloatBuilder.close();
					}
					this.finishTranslateBlockScope(entry);
					if (this.pageContext() instanceof RootBuilder root) {
						root.addPageFloat(pageFloatBox, pageFloatPos.top);
					}
					break;
				}
				if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageMarginNotePos notePos) {
					// Parallel notes in JLREQ 4.2.7. Build with the same separate builder as page floats,
					// then pass to the note area outside the type area near the current body-text position.
					this.notePageMarginNoteInGrids();
					final FloatBlockBox noteBox = (FloatBlockBox) entry.builder.getRootBox();
					if (parentBuilder.isTwoPass() || this.replayIntent == ReplayIntent.MEASURE) {
						if (!parentBuilder.isTwoPass() && entry.builder instanceof TwoPassBlockBuilder body) {
							body.completeScratchHost();
						}
						break;
					}
					if (entry.builder.isTwoPass()) {
						final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						noteBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
						final BlockBuilder noteBuilder = new BlockBuilder(this.pageContextBuilder(), noteBox);
						contentBuilder.bind(noteBuilder, this.replayIntent);
						noteBuilder.close();
					}
					this.finishTranslateBlockScope(entry);
					if (this.pageContext() instanceof RootBuilder root) {
						root.addPageMarginNote(noteBox, notePos.start);
					}
					break;
				}
				if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.FootnotePos) {
					// Footnotes F2/F3 (2026-07-31, consult-codex-2026-07-31-footnote.txt
					// §3): keep the footnote body out of the parent's flow (only F1's
					// ::footnote-call remains at the call position). Pass the completed body box
					// to the page-footnote ledger (RootBuilder) for drawing
					// in the page-bottom area. Contexts without RootBuilder at the root, such as
					// scratch measurement/replay, have no ledger and place it nowhere (the body
					// is out of flow, so measurement is equivalent; two-pass seal→bind remain
					// paired normally, leaving no orphaned leases).
					final FloatBlockBox noteBox = (FloatBlockBox) entry.builder.getRootBox();
					if (this.replayIntent == ReplayIntent.MEASURE
							&& this.pageGenerator instanceof MeasurePageGenerator measure && measure.isFootnoteProbe()) {
						// Inside a TwoPass parent, complete the body during that parent's MEASURE replay.
						if (!parentBuilder.isTwoPass()) {
							if (entry.builder instanceof TwoPassBlockBuilder contentBuilder) {
								noteBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
								final BlockBuilder noteBuilder = new BlockBuilder(this.pageContextBuilder(), noteBox);
								contentBuilder.bind(noteBuilder, ReplayIntent.MEASURE);
								noteBuilder.close();
							}
							measure.measureFootnote(noteBox);
							if (this.pageContext() instanceof RootBuilder root) {
								root.addFootnote(noteBox, parentBuilder, RootBuilder.footnoteColumnOwner(parentBuilder));
							}
						}
						break;
					}
					if (parentBuilder.isTwoPass() || this.replayIntent == ReplayIntent.MEASURE) {
						// As with PageFloatPos, defer separate placement until the parent's actual layout.
						if (!parentBuilder.isTwoPass() && entry.builder instanceof TwoPassBlockBuilder body) {
							body.completeScratchHost();
						}
						break;
					}
					final AbstractContainerBox footnoteOwner = RootBuilder.footnoteColumnOwner(parentBuilder);
					if (entry.builder.isTwoPass()) {
						final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						final double hostLineSize = this.pageContext() instanceof RootBuilder root
								? root.getFootnoteLineSize(parentBuilder, footnoteOwner) : LayoutUtils.NONE;
						if (LayoutUtils.isNone(hostLineSize)) {
							noteBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
						} else {
							noteBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false, hostLineSize);
						}
						final BlockBuilder noteBuilder = new BlockBuilder(this.pageContextBuilder(), noteBox);
						// Do not consume during disposable measurement, for the same reason as floats.
						contentBuilder.bind(noteBuilder, this.replayIntent);
						noteBuilder.close();
					}
					this.finishTranslateBlockScope(entry);
					if (this.pageContext() instanceof RootBuilder root) {
						root.addFootnote(noteBox, parentBuilder, footnoteOwner);
					}
					break;
				}
				if (!parentBuilder.isTwoPass()) {
					final BlockBuilder boundBuilder = (BlockBuilder) parentBuilder;
					final FloatBlockBox floatBox = (FloatBlockBox) entry.builder.getRootBox();
					if (entry.builder.isTwoPass()) {
						// Build.
						final TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						floatBox.shrinkToFit(parentBuilder, contentBuilder.intrinsicSizesMeasured(), false);
						final BlockBuilder floatBuilder = new BlockBuilder(this.pageContextBuilder(), floatBox);
						// **Do not consume the body during disposable measurement** (2026-08-03).
						// An actual bind here closes the usage right, leaving it empty for the later
						// actual layout (content loss; see TwoPassBlockBuilder.bind's Javadoc).
						contentBuilder.bind(floatBuilder, this.replayIntent);
						floatBuilder.close();
					}
					final FloatPos pos = (FloatPos) box.getPos();
					final boolean pageBreak = (this.pageMode == 0 && ((pos.pageBreakBefore != PageBreakMode.AUTO
							&& pos.pageBreakBefore != PageBreakMode.AVOID)
							|| (pos.pageBreakAfter != PageBreakMode.AUTO
									&& pos.pageBreakAfter != PageBreakMode.AVOID)));
					if (pageBreak) {
						this.closeInlines(box.getParams());
						this.endContainer();
					}
					boundBuilder.addBound(floatBox);
					if (pageBreak) {
						this.startContainer();
						this.restoreInlines(box.getParams());
					}
				} else if (entry.builder.isTwoPass()) {
					// Inside an STF context.
					TwoPassBlockBuilder stfBuilder = (TwoPassBlockBuilder) parentBuilder;
					TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
					stfBuilder.fitFloating(contentBuilder);
				}
			} finally {
				this.finishTranslateBlockScope(entry);
			}
		}
			break;

		case ABSOLUTE: {
			// Absolute positioning.
			this.endContainer();
			ContainerBuilderEntry entry = this.endContainerBuilder();
			try {
				if (this.replayIntent == ReplayIntent.MEASURE) {
					if (this.pageGenerator instanceof MeasurePageGenerator measure && measure.isFootnoteProbe()
							&& entry.builder instanceof TwoPassBlockBuilder body) {
						// For long-lived B, also aggregate discarded absolute-positioned child ranges into the parent to close their lifetimes.
						// If a TwoPass parent remains, retain ownership until its seal absorbs them.
						body.sealBodyForRangeBind();
						if (!this.contextBuilder().builder.isTwoPass()) body.completeScratchHost();
						break;
					}
					// Disposable measurement (table Pass B): skip seal, prepareBind, and anchoring;
					// discard the child builder with its replica (sealing would acquire a real lease
					// that becomes orphaned when discarded). Absolute positioning is out of flow
					// and does not contribute to measurements, so measurement remains equivalent
					// (absolute absorption = codex increment 9, 2026-07-30).
					break;
				}
				if (entry.builder instanceof TwoPassBlockBuilder sealable) {
					// E-6 increments 4a/4b: seal the range at recording completion. Recipe recording
					// in E-6 increment 4e also makes absolute positioning eligible (eliminates old NO_RANGE=81).
					// prepareBind below transfers eligible bodies into DeferredBind.
					sealable.sealBodyForRangeBind();
				}
				Builder builder = this.contextBuilder().builder;
				if (!builder.isTwoPass()) {
					BlockBuilder boundBuilder = (BlockBuilder) builder;
					AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) entry.builder.getRootBox();
					if (entry.builder.isTwoPass()) {
						// Build.
						TwoPassBlockBuilder contentBuilder = (TwoPassBlockBuilder) entry.builder;
						if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
							// For position: fixed;, build here.
							IFramedBox containerBox = this.pageContextBuilder().getRootBox();
							absoluteBox.shrinkToFit(containerBox, contentBuilder.intrinsicSizesMeasured());
							BlockBuilder absoluteBuilder = new BlockBuilder(this.pageContextBuilder(), absoluteBox);
							final RetainedTextLimit limit = RetainedTextLimit.get(absoluteBuilder);
							try (var retained = limit == null ? null
									: limit.enter(RetainedTextLimit.elementName(absoluteBox.getParams(), "fixed"))) {
								contentBuilder.bind(absoluteBuilder, this.replayIntent);
								absoluteBuilder.close();
							}
						} else {
							// Build position: absolute; later.
							absoluteBox.prepareBind(contentBuilder);
						}
					}
					switch (absoluteBox.getAbsolutePos().autoPosition) {
					case AutoPosition.BLOCK:
						boundBuilder.addBound(absoluteBox);
						break;
					case AutoPosition.INLINE:
						this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
						break;
					default:
						throw new IllegalStateException();
					}
				}
			} finally {
				this.finishTranslateBlockScope(entry);
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}

		// An element item directly under the coordinator consists of one child box; close it
		// immediately after the child's endBox (shared by Grid G1b/Flex F1d). Nested children
		// do not trigger this because the last box is not that container. Flex takeover items
		// are already closed in endBox's FLOW branch.
		final net.zamasoft.foliojet.layout.builder.ItemCoordinator tail = this.coordinatorAwaitingDirectChild();
		if (tail != null && tail.hasOpenElementItem()) {
			this.endCoordinatorElementItem(tail);
		}
	}

	public void addReplacedBox(AbstractReplacedBox replacedBox) {
		this.requireNotDiscarded();
		this.requirePage();

		// Turn replaced elements directly under Grid into items (Grid G1b): block-level
		// elements become one-shot element items; inline elements become anonymous items.
		net.zamasoft.foliojet.layout.builder.ItemCoordinator oneShot = null;
		switch (replacedBox.getPos().getType()) {
		case FLOW:
			oneShot = this.startGridElementItem(gridItemSpecOf(replacedBox), replacedBox.getSourceAnchor());
			if (oneShot == null) {
				// Transfer inline size settings to the wrapper (see FlexBuilder.NeutralTransfer:
				// percentage-width SVGs, etc. measure as zero in two-pass measurement, so without
				// this transfer the flex base size collapses to zero). Size resolution itself
				// still belongs to the replaced-content mechanism (calculateReplacedSize).
				oneShot = this.startFlexNeutralElementItem(flexItemSpecOf(replacedBox),
						FlexBuilder.NeutralTransfer.of(replacedBox.getReplacedParams()), replacedBox.getSourceAnchor());
			}
			break;
		case INLINE:
			this.requireCoordinatorAnonymousItem();
			break;
		default:
			break;
		}

		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// Normal flow.
			// Column spanning.
			final FlowPos pos = ((FlowReplacedBox) replacedBox).getFlowPos();
			// Close inlines first (same reason as FLOW in startBox).
			// Obtain the builder **after startColumnSpan**: leaving multi-column layout
			// replaces containerBuilder, so addBound must target the new one.
			this.closeInlines(replacedBox.getParams());
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.startColumnSpan(pos);
			}
			final Builder builder = this.containerBuilder().builder;
			this.endContainer();
			builder.addBound(replacedBox);
			this.startContainer();

			// Return from column spanning.
			if (pos.columnSpan == FlowPos.COLUMN_SPAN_ALL) {
				this.endColumnSpan(pos);
			}
			this.restoreInlines(replacedBox.getParams());
		}
			break;

		case FLOAT: {
			// Float.
			final Builder context = this.containerBuilder().builder;
			final FloatPos pos = (FloatPos) replacedBox.getPos();
			boolean pageBreak = (this.pageMode == 0 && ((pos.pageBreakBefore != PageBreakMode.AUTO
					&& pos.pageBreakBefore != PageBreakMode.AVOID)
					|| (pos.pageBreakAfter != PageBreakMode.AUTO && pos.pageBreakAfter != PageBreakMode.AVOID)));
			if (pageBreak) {
				this.closeInlines(replacedBox.getParams());
				this.endContainer();
			} else {
				this.containerBuilder().getStyledTextUnitizer().flushText();
			}
			this.noteBidiBarrier(replacedBox);
			context.addBound(replacedBox);
			if (pageBreak) {
				this.startContainer();
				this.restoreInlines(replacedBox.getParams());
			}
		}
			break;
		case ABSOLUTE: {
			// Absolute positioning.
			final Builder context = this.containerBuilder().builder;
			final IAbsoluteBox absoluteBox = (IAbsoluteBox) replacedBox;
			this.noteBidiBarrier(replacedBox);
			switch (absoluteBox.getAbsolutePos().autoPosition) {
			case AutoPosition.BLOCK:
				context.addBound(replacedBox);
				break;
			case AutoPosition.INLINE:
				this.containerBuilder().getStyledTextUnitizer().addInlineAbsolute(absoluteBox);
				break;
			default:
				throw new IllegalStateException();
			}
		}
			break;

		case INLINE: {
			// Inline.
			this.containerBuilder().getStyledTextUnitizer().addInlineReplaced(replacedBox);
		}
			break;

		default:
			throw new IllegalStateException();
		}

		if (oneShot != null) {
			this.endCoordinatorElementItem(oneShot);
		}
	}

	public void characters(int charOffset, char[] ch, int off, int len, boolean lineFeed) {
		this.requireNotDiscarded();
		if (this.normalizeText) {
			String s = new String(ch, off, len);
			s = Normalizer.normalize(s, Form.NFC);
			ch = s.toCharArray();
			off = 0;
			len = s.length();
		}
		
		this.requirePage();
		// Direct text under Grid goes into an anonymous item (Grid G1b).
		this.requireCoordinatorAnonymousItem();
		this.containerBuilder().getStyledTextUnitizer().characters(charOffset, ch, off, len, lineFeed);
	}

	/**
	 * Feeds {@code leader()} into the current inline context (leader() L1:
	 * consult-codex-2026-07-31-leader.txt). {@code StyledTextUnitizer.leader} and later
	 * processing perform shaping and width allocation on each execution.
	 */
	public void addLeader(final String pattern) {
		this.requirePage();
		this.requireCoordinatorAnonymousItem();
		this.containerBuilder().getStyledTextUnitizer().leader(pattern);
	}

	public void end() {
		this.requirePage();
		// If output.page-limit, etc. stops input midway, document end arrives without SAX
		// endElement events. Close from the inside through the normal endBox path,
		// symmetrically finalizing Flex/Grid coordinators and two-pass builders too.
		while (!this.boxStack.isEmpty()) {
			this.endBox();
		}
		this.endContainer();
		final ContainerBuilderEntry entry = this.endContainerBuilder();
		this.finishTranslateBlockScope(entry);
		assert this.builderStack.isEmpty() : "document end後もbuilderStackが残っています: " + this.builderStack;
		assert this.translateScopeRoots.isEmpty() : "document end後もtranslate scopeが残っています";
	}

	/** For B's pin: the oldest start ID among TwoPass hosts receiving input or still binding after being popped. */
	public long oldestUnfinishedSourceId() {
		long oldest = Long.MAX_VALUE;
		for (final Object entry : this.builderStack) oldest = Math.min(oldest, unfinishedSourceId(entry));
		for (final Object entry : this.translateScopeRoots.keySet()) oldest = Math.min(oldest, unfinishedSourceId(entry));
		return oldest;
	}

	private static long unfinishedSourceId(final Object entry) {
		final long anchor;
		if (entry instanceof ContainerBuilderEntry container && container.builder instanceof TwoPassBlockBuilder body) {
			// Protect only unsealed bodies with the pin. TwoPass does not derive from BlockBuilder.
			// Sealed bodies entrusted to tables, etc. are protected by RangeHandle's own lease and lifetime notifications.
			anchor = body.getRootBox().getSourceAnchor();
		} else if (entry instanceof RetainedTableBuilder table) {
			anchor = table.getSourceAnchor();
		} else {
			return Long.MAX_VALUE;
		}
		return anchor < 0 ? Long.MAX_VALUE : anchor;
	}

	/** For discard reporting: counts only unfinished Retained tables, without invoking cleanup. */
	public int getOpenRetainedTableCount() {
		int count = 0;
		for (final Object entry : this.builderStack) {
			if (entry instanceof net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder) ++count;
		}
		return count;
	}

	/**
	 * Discards unfinished scratch state after dispatch returns. Releases handles, leases, and accounting
	 * scopes for all builders registered with their owner at creation, without sealing, binding, or placing.
	 * Use a dedicated ScratchOwner for each long-lived document, connected only during creation and input.
	 * May be called with the owner disconnected. Cannot be used with a production page generator.
	 * Also detaches unfinished Grid/Flex items and ReplayOnly objects retaining actual characters,
	 * without normal completion. Even if a release notification fails, cleans up remaining resources
	 * and rejects subsequent input, end, and repeated discard.
	 */
	public void discard() {
		this.requireNotDiscarded();
		if (!(this.pageGenerator instanceof MeasurePageGenerator) || this.scratchOwner == null) {
			throw new IllegalStateException("discardはScratchReplayScope内で生成した計測文書専用です");
		}
		this.discarded = true;
		Throwable failure = null;
		while (!this.builderStack.isEmpty()) {
			final Object entry = this.builderStack.remove(this.builderStack.size() - 1);
			try {
				this.finishTranslateBlockScope(entry);
			} catch (final RuntimeException | Error e) {
				if (failure == null) failure = e;
				else if (failure != e) failure.addSuppressed(e);
			}
		}
		try {
			this.scratchOwner.release();
		} catch (final RuntimeException | Error e) {
			if (failure == null) failure = e;
			else if (failure != e) failure.addSuppressed(e);
		} finally {
			this.boxStack.clear();
			this.inlineStack.clear();
			this.columnSpanStack.clear();
			this.translateScopeRoots.clear();
			this.replayItemHost = null;
			this.replayEvent = null;
			this.replayOrdinal = -1;
		}
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException exception) throw exception;
	}

	private void requireNotDiscarded() {
		if (this.discarded) throw new IllegalStateException("破棄済み文書への入力");
	}
	/**
	 * Intrinsic sizes of a shrink-to-fit box (2026-10-05). If contents are orthogonal (e.g., a horizontal-writing
	 * table or paragraph inside a vertical-writing container), measurement replay (without consuming the body)
	 * first lays them out in the box, reads the orthogonal child's actual extent, and substitutes it for
	 * the simulated measurement's proxy values (table width or one line). Discards the laid-out contents,
	 * leaving the box empty again. jigensha report: a horizontal-writing table inside a vertical-writing
	 * float: bottom container had its width counted as the container's height and overflowed below the type area.
	 */
	private net.zamasoft.foliojet.layout.sizing.IntrinsicSizes shrinkToFitSizes(
			final net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox box, final TwoPassBlockBuilder content,
			final Builder parent) {
		final net.zamasoft.foliojet.layout.sizing.IntrinsicSizes measured = content.intrinsicSizesMeasured();
		if (!content.hasOrthogonalContent() || this.replayIntent == ReplayIntent.MEASURE || box.getColumnCount() > 1) {
			return measured;
		}
		box.shrinkToFit(parent, measured, false);
		final BlockBuilder trial = new BlockBuilder(this.pageContextBuilder(), box);
		content.bind(trial, ReplayIntent.MEASURE);
		trial.close();
		final double extent = box.orthogonalContentLineExtent();
		box.resetContentForRelayout();
		if (!(extent > 0)) {
			return measured;
		}
		final net.zamasoft.foliojet.layout.sizing.IntrinsicSizes base = content.intrinsicSizesWithoutOrthogonal();
		return new net.zamasoft.foliojet.layout.sizing.IntrinsicSizes(Math.max(base.minContent(), extent),
				Math.max(base.maxContent(), extent), measured.minPage(), measured.columnInflated());
	}

}
