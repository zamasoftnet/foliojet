package net.zamasoft.foliojet.layout.fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.TextSpill;

/**
 * The layout source protocol log (M6b v3).
 *
 * <p>
 * Records events in an append-only log at the boundary after style application and pseudo-element synthesis
 * but before layout (the input protocol to DocumentBuilder). Since params/pos are already computed,
 * replay structurally cannot repeat selector matching, generated-content synthesis, or counter evaluation.
 * A dedicated driver replays page-break remainders by reading this log as read-only, without touching
 * any live StyleBuilder/DocumentBuilder state (ARCHITECTURE.md §5.6 v3).
 * </p>
 *
 * <p>
 * <b>Freeze BeginBox on append (E-6 increment 3b-4, 2026-07-24)</b>:
 * {@link Start} holds a recipe frozen by {@link BoxRecipe#freeze} at recording time, rather than live
 * params/pos references. All params/pos mutations stay within the StyleBuilder phase before recording
 * (codex design §1.1, independently cross-checked), so output behavior is unchanged. The log does not retain
 * live params/pos or the precedingElement graph of {@code CSSElement}
 * ({@code Params.element} is detached into {@code StructureToken} ; see the {@code StructureToken} Javadoc).
 * </p>
 *
 * <p>
 * <b>EventId</b>: Each event receives an id that remains immutable from assignment onward.
 * Boxes are stamped with ids (anchors), which remain stable after compaction.
 * <b>Watermark-based discard</b>: {@link #compact(long)} discards events before the specified id,
 * preserving open (unmatched) Starts. Retention stays at O(current page + open elements).
 * </p>
 *
 * <p>
 * <b>Text payload spill (E-6 increment 3b-2, 2026-07-24)</b>: The char[] contents of {@link Chars}
 * are spilled to {@link TextSpill} (a temporary file) starting with new appends that exceed the configured
 * inline retention budget ({@code processing.text-spill
 * -budget}). The decision is deterministic, based only on the configured value and cumulative inline bytes,
 * never on remaining heap space. Event boundaries never change (1 Chars = 1 payload; splitting or merging
 * is prohibited because it changes NFC normalization and text-transform call boundaries; codex design §2.4).
 * The spill store has the lifetime of this LayoutSource and is reliably deleted, including its temporary
 * file, by {@link #close()} (in the finally block of the conversion termination path).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LayoutSource implements AutoCloseable {
	/** A zero-advance, nonstructural, nonpainting substitution position. The page registry owns the payload. */
	public record Assignment(long order) implements Event {
	}
	/** Whether an automatic page-break livelock has ever been confirmed in this document. */
	private boolean autoBreaksAbandoned = false;

	/** Whether automatic page breaking has already livelocked at this input position. */
	public boolean areAutoBreaksAbandoned() {
		return this.autoBreaksAbandoned;
	}

	/** Rejects automatic page breaks at this input position for the rest of the document. */
	public void abandonAutoBreaks() {
		this.autoBreaksAbandoned = true;
	}

	public sealed interface Event permits Start, Replaced, Chars, EndBlock, Opaque, Leader, Assignment,
			AnonymousItemStart, AnonymousItemEnd {
	}

	/**
	 * The box kind. Selects the factory for reinstantiation from params/pos
	 * (the kind kernel of {@code BoxRecipeBoxFactory.create} ) and the variant for freezing at recording time
	 * ({@link BoxRecipe#freeze}).
	 */
	public enum BoxKind {
		/** Normal block (FlowBlockBox). */
		FLOW,
		/** Multi-column block (MulticolumnBlockBox). */
		MULTICOL,
		/** Inline (InlineBox). */
		INLINE,
		/** Outside list marker (OutsideMarkerBox). */
		MARKER,
		/** Floating block (FloatBlockBox). */
		FLOAT_BLOCK,
		/** Inline block (InlineBlockBox). */
		INLINE_BLOCK,
		/** Inside marker (InsideMarkerBox). */
		INSIDE_MARKER,
		/**
		 * Table (TableBox; reconstructs blockBox with shared params and <b>the inner blockBox's pos</b>;
		 * the outer {@code TableBox.getPos()} is always {@code TablePos} and has no positioning kind).
		 * Removed after the G-1 investigation (2026-07-25), then restored with user approval of the table-set
		 * implementation (2026-07-30, revision of the G-1 decision). Recording eligibility is determined by the
		 * TableBox branch of {@code RecordingLayoutSink.boxKind} (exact class + params alias, fail closed).
		 * PlacedTable also uses Start/End with the same TABLE kind.
		 */
		TABLE,
		/** Table row group (TableRowGroupBox). */
		TABLE_ROW_GROUP,
		/** Table row (TableRowBox). */
		TABLE_ROW,
		/** Table cell (TableCellBox). */
		TABLE_CELL,
		/** Table column group (TableColumnGroupBox). */
		TABLE_COLUMN_GROUP,
		/** Table column (TableColumnBox). */
		TABLE_COLUMN,
		/**
		 * Absolutely positioned block (AbsoluteBlockBox). Promoted from Opaque recording to recipe recording
		 * in E-6 increment 4e (2026-07-24). Appended to preserve existing ordinals
		 * (keep the order aligned with {@code segment.BoxKind} ).
		 */
		ABSOLUTE,
		/**
		 * Grid container (GridBox; Grid G0c, 2026-07-31;
		 * consult-codex-2026-07-31-grid.txt §3.7). Appended to preserve existing ordinals.
		 */
		GRID,
		/**
		 * Table caption (FlowBlockBox + TableCaptionPos; caption recipes C1, 2026-08-01;
		 * consult-codex-2026-08-01-caption-recipe.txt).
		 * A context-dependent kind: replay requires a preceding TABLE Start established in the same range,
		 * so it cannot be the range root (C1 excludes it from replay with the blanket containsCaption gate;
		 * C2 is scheduled to replace this with context-complete validation).
		 */
		CAPTION,
		/**
		 * Flex container (FlexBox; Flex F0c, 2026-08-02; consult-codex-2026-08-02-flexbox.txt).
		 * Appended to preserve existing ordinals.
		 */
		FLEX;
	}

	/**
	 * The start of a box (E-6 increment 3b-4, 2026-07-24: replaced retention of live params/pos references
	 * with retention of a recipe frozen at recording time). {@link BoxRecipe#freeze} freezes it during
	 * recording
	 * ({@code StyleBuilder.startBox}), and replay materializes a fresh box through
	 * {@code BoxRecipeBoxFactory.create(BoxRecipe)} . Live params/pos (including the {@code CSSElement} graph)
	 * do not remain in the log. freeze is a total function covering all 14 kinds for which
	 * {@code StyleBuilder.boxKind} returns non-null (ABSOLUTE added in E-6 increment 4e, TABLE in the table
	 * set);
	 * unlike {@code ReplacedRecipe.freeze} , it has no failure variant. Generated content, marker numbers, etc.
	 * follow as already resolved events, so replay does not repeat style side effects.
	 */
	public record Start(BoxRecipe recipe) implements Event {
	}

	/**
	 * A replaced element (E-6 increment 3b-3, 2026-07-24: replaced live box retention with recipe retention).
	 * {@code ReplacedRecipe.freeze} freezes it during recording ({@code StyleBuilder.addReplacedBox});
	 * replay materializes a fresh box through {@code BoxRecipeBoxFactory.createReplaced} ,
	 * so no live box references remain in the log.
	 * E-6 increment 3b-6: Boxes referencing {@code ReplacedBoxImage} implementations (BarcodeImage, etc.)
	 * also became freezable using independent copies from {@code duplicate()} , and the transitional variant
	 * that retained a live box ({@code ReplacedLive}) was removed. Unknown subclasses that cannot be frozen
	 * (none among the four existing implementations) are recorded fail-closed as an
	 * {@link Opaque} + {@link EndBlock} pair.
	 */
	public record Replaced(net.zamasoft.foliojet.layout.segment.ReplacedRecipe recipe) implements Event {
	}

	/**
	 * Text. charOffset is the source character offset (-1 for generated content).
	 * fixed preserves the doc protocol's fixed-text flag unchanged.
	 * payload is an inline char[] or a reference to a spilled record
	 * (E-6 increment 3b-2; {@link TextPayload} ).
	 */
	public record Chars(int charOffset, TextPayload payload, boolean fixed) implements Event {
		/** Convenience construction with an inline payload (for tests and small callers). */
		public Chars(final int charOffset, final char[] ch, final boolean fixed) {
			this(charOffset, new TextPayload.Inline(ch), fixed);
		}
	}

	/**
	 * {@code leader()} (css-content-3, consult-codex-2026-07-31-leader.txt L1).
	 * The payload contains only the normalized pattern string. {@code StyledTextUnitizer.leader} performs
	 * shaping and width allocation on each replay, so replays share no mutable state
	 * (a new LeaderQuad is created for each run).
	 */
	public record Leader(String pattern) implements Event {
	}

	/**
	 * The text contents of {@link Chars} (E-6 increment 3b-2). Even Spilled retains the UTF-16 length
	 * ({@link #utf16Length()}) as heap metadata, allowing range calculations without decoding
	 * (a guarantee of unchanged behavior).
	 */
	public sealed interface TextPayload permits TextPayload.Inline, TextPayload.Spilled {
		/** The character count in UTF-16 units (heap metadata, readable without decoding). */
		int utf16Length();

		/**
		 * Returns the text <b>in a new (fresh) char[] on every call</b>. Downstream replay processing
		 * ({@code StyledTextUnitizer}) modifies the array in place, so the retained array must never be
		 * returned
		 * directly (the fresh-copy policy of 3b-1). Spilled read failures raise
		 * {@link TextSpillException} (a typed layout failure).
		 */
		char[] freshChars();

		/** Inline retention on the heap. */
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
		 * A reference to a record already written to {@link TextSpill} . Only the constant-size tuple
		 * (store reference, recordId, utf16Length) remains on the heap.
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
	 * The end of a block.
	 */
	public record EndBlock() implements Event {
	}

	/** Starts synthesizing an anonymous Grid/Flex item. anchor is this event's own EventId. */
	public record AnonymousItemStart(long anchor) implements Event {
	}

	/** Ends synthesizing an anonymous item. The body range excludes the boundaries of this pair. */
	public record AnonymousItemEnd() implements Event {
	}

	/**
	 * Marks a range as non-replayable (box kinds without recipe support:
	 * those for which {@code StyleBuilder.boxKind} returns null, and fail-closed handling of unknown
	 * {@code AbstractReplacedBox} subclasses). Occupies a position to keep the log complete
	 * (an honest record of everything), and makes replay requests containing it fall back for the entire range.
	 * Always pushed as a start event paired with {@link EndBlock}
	 * (start/end symmetry in {@code compact} /{@code endOf}).
	 *
	 * <p>
	 * <b>Not slated for removal (clarified in E-6 increment 3b-6)</b>: Unlike the live variant
	 * ({@code ReplacedLive}), Opaque is permanently required as the fail-closed foundation of replay
	 * eligibility
	 * ({@code containsOpaque}). Honestly marking what cannot be converted and falling back for the entire range
	 * structurally prevents silent holes (silently lost content). Occurrences decrease as recipe support
	 * expands,
	 * but the type itself remains.
	 * </p>
	 *
	 * <p>
	 * <b>Measured sources (F-4, 2026-07-25; all 436 documents in {@code files/unittest} )</b>:
	 * 319 occurrences in total, all from table bodies (310) and table captions (9)
	 * (captions are always inside tables, so the latter are a proper subset of the former).
	 * Unknown {@code AbstractReplacedBox} subclasses structurally cannot occur among the four existing
	 * implementations. The 160 ruby-derived occurrences disappeared with the switch to annotated text on
	 * 2026-07-25. Thus, tables are the remaining source of Opaque, and correcting the recording conditions for
	 * {@link BoxKind#TABLE} is the only way to reduce it.
	 * </p>
	 */
	public record Opaque() implements Event {
	}

	private static final class Entry {
		private final long id;
		private final Event event;
		/** The share count for the main store and sealed slices. Avoids double-counting the inline payload budget. */
		private int owners = 1;

		Entry(final long id, final Event event) {
			this.id = id;
			this.event = event;
		}

		long id() { return this.id; }
		Event event() { return this.event; }
	}

	private final List<Entry> entries = new ArrayList<Entry>();

	private long nextId = 0;

	/**
	 * The default inline retention budget for text payloads, in bytes (E-6 increment 3b-2).
	 * Equals the default of {@code UAProps.PROCESSING_TEXT_SPILL_BUDGET} .
	 * It is well above corpus measurements (most documents have a SOURCE_EVENT_HIGH_WATER of a few thousand
	 * events), so normal documents do not spill.
	 */
	public static final long DEFAULT_TEXT_SPILL_BUDGET_BYTES = 8L * 1024L * 1024L;

	/** The inline text payload budget in bytes. New Chars spill once it is exceeded. */
	private final long textSpillBudgetBytes;

	/**
	 * Cumulative bytes of inline text payloads currently retained by entries or sealed slices
	 * (UTF-16 estimate: character count × 2). Added on append and subtracted when the final ownership of inline
	 * Chars ends. The decision is deterministic, based only on this value and the configured budget
	 * (consulting remaining heap space is prohibited; codex design §2.1).
	 */
	private long liveInlineTextBytes = 0;

	/** The spill store (created lazily on first use; deleted by {@link #close()}). */
	private TextSpill textSpill = null;

	/**
	 * E-6 increment 2 (2026-07-24): Shadow observation hook for appended events
	 * (test only, set through LayoutSourceTestHooks). Always null in production; when null, behavior is
	 * identical to that before the increment. Volatile because some configurations run layout on another
	 * thread (large stack).
	 */
	static volatile java.util.function.Consumer<Event> appendObserver;
	/** Observes leftover ownership hidden by final cleanup, before release (test only). */
	static volatile java.util.function.Consumer<LayoutSource> beforeCloseObserver;
	/** Observation immediately after T5a compaction (test only). */
	static volatile java.util.function.Consumer<LayoutSource> compactObserver;

	public record RetentionSnapshot(long leases, int openRanges, int retainedEvents, long oldestWatermark, long nextId,
			int slicedRanges, int slicedEvents) { }

	public RetentionSnapshot retentionSnapshot() {
		return new RetentionSnapshot(this.retentionLeaseCount, this.openRanges.size(), this.entries.size(),
				this.retentionLeases.isEmpty() ? -1 : this.retentionLeases.firstKey(), this.nextId,
				this.textSlices.size(), this.slicedEvents);
	}

	/** Bytes of unreleased inline character payloads shared by the main log and sealed slices. */
	public long retainedInlineTextBytes() {
		return this.liveInlineTextBytes;
	}

	/** Creates an instance with the default budget ({@link #DEFAULT_TEXT_SPILL_BUDGET_BYTES}). */
	public LayoutSource() {
		this(DEFAULT_TEXT_SPILL_BUDGET_BYTES);
	}

	/**
	 * Creates an instance with the specified inline text payload retention budget, in bytes
	 * (E-6 increment 3b-2; in production, StyleBuilder passes {@code processing.text-spill-budget} ).
	 */
	public LayoutSource(final long textSpillBudgetBytes) {
		this.textSpillBudgetBytes = textSpillBudgetBytes;
	}

	/**
	 * Appends an event and returns its EventId.
	 */
	public long append(final Event event) {
		final long id = this.nextId++;
		this.entries.add(new Entry(id, event));
		this.indexEvent(id, event); // RangeSummary(2026-08-01)
		// E-6 increment 3b-2: Inline text payload budget accounting (symmetric for append/compact)
		if (event instanceof Chars chars && chars.payload() instanceof TextPayload.Inline inline) {
			this.liveInlineTextBytes += (long) inline.utf16Length() * 2;
			ContinuationStats.recordLiveTextPayloadBytes(this.liveInlineTextBytes);
		}
		// E-6 increment 1 (2026-07-24): Observe retention high-water marks only (behavior unchanged)
		ContinuationStats.recordSourceEventRetention(this.entries.size());
		this.reportRetention();
		// E-6 increment 2 (2026-07-24): Shadow observation only (behavior unchanged)
		final java.util.function.Consumer<Event> observer = appendObserver;
		if (observer != null) {
			observer.accept(event);
		}
		return id;
	}

	/**
	 * Appends a text event and returns its EventId (E-6 increment 3b-2).
	 * If inline retention ({@code liveInlineTextBytes}) plus this append fits the budget, retains an array copy
	 * inline; appends exceeding the budget go to {@link TextSpill} . The rule is to spill new Chars after the
	 * budget is exceeded, avoiding the complexity of retroactively spilling old sealed inline chunks.
	 * If compaction releases inline data, subsequent appends return to inline storage, so inline retention
	 * always stays within budget.
	 *
	 * <p>
	 * Spill write failures propagate as {@link TextSpillException} (a typed layout failure).
	 * They are neither ignored nor handled by falling back to continued inline storage, avoiding
	 * nondeterministic output or memory behavior depending on spill success.
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
	 * Closes the text payload spill store and deletes the temporary file (idempotent; E-6 increment 3b-2).
	 * Always called at the end of the LayoutSource lifetime through the conversion termination path
	 * (StyleBuilder.finish, and the formatter's finally → CSSProcessor.dispose, also reached on exceptions).
	 * No new replay is possible after close. Only when previously acquired text ReplaySlices remain does
	 * spill deletion wait until the last of them closes.
	 */
	@Override
	public void close() {
		if (this.closed) return;
		try {
			final var observer = beforeCloseObserver;
			if (observer != null) observer.accept(this);
		} finally {
			// Ends ownership of body text remaining in the main source, even on interruption or ineligibility.
			for (final RangeHandle handle : java.util.List.copyOf(this.openRanges)) handle.abandon();
			this.closed = true;
			this.retentionLeases.clear();
			this.retentionLeaseCount = 0;
			if (this.textSpill != null && this.textSlices.isEmpty()) {
				this.textSpill.close();
			}
		}
	}

	/** Returns the spill store (null if not yet created; for test observation). */
	public TextSpill textSpillForTest() {
		return this.textSpill;
	}

	/**
	 * Returns the next EventId to be assigned (= the current end position).
	 */
	public long nextId() {
		return this.nextId;
	}

	/**
	 * Returns the number of retained events.
	 */
	public int size() {
		return this.entries.size();
	}

	/**
	 * Returns the event with the given id (null if discarded or not yet assigned).
	 */
	public Event get(final long id) {
		final int index = this.indexOf(id);
		return index < 0 ? null : this.entries.get(index).event();
	}

	/**
	 * A sparse reverse index for range eligibility (RangeSummary, 2026-08-01; elegance improvement B).
	 * Retains only the ids of special events (Opaque, CAPTION, TABLE, float, absolute, multicol, grid,
	 * and vertical/horizontal flow starts), by category. Replaces the linear range scan in
	 * {@code containsX(from,to)} with O(log k) binary search
	 * (k is the category size, usually a few entries per page).
	 *
	 * <p>
	 * Ids increase monotonically in append order, so lists stay sorted. Memory usage is only
	 * "special event count × 8 B", avoiding cumulative arrays alongside all events (about 40 B per event);
	 * this design complies with the memory-efficiency requirement.
	 * {@code compact()} also rebuilds the index while rebuilding kept.
	 * Randomized property tests in {@code LayoutSourceTest} enforce equivalence with the linear implementation.
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

		/** Whether [fromId, toId] contains any entry (both ends inclusive). */
		boolean anyInRange(final long fromId, final long toId) {
			int low = 0, high = this.size - 1;
			// First position at or above fromId
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

	/** Classifies categories on append/rebuild (the same detection conditions as the containsX family). */
	private void indexEvent(final long id, final Event event) {
		switch (event) {
		case Opaque opaque -> this.opaqueIds.add(id);
		case Start(final BoxRecipe recipe) -> {
			switch (recipe) {
			case BoxRecipe.Caption c -> this.captionIds.add(id);
			case BoxRecipe.Table t -> this.tableIds.add(id);
			case BoxRecipe.PlacedTable t -> {
				this.tableIds.add(id);
				if (t.placement() instanceof BoxRecipe.FloatBlock) this.floatIds.add(id);
				if (t.placement() instanceof BoxRecipe.Absolute) this.absoluteIds.add(id);
			}
			case BoxRecipe.Multicol m -> this.multicolIds.add(id);
			// **Also index auto-height multi-column layout (Flow with column-count) as multi-column**
			// (2026-08-21, sweep seed 615921). Previously, only fixed-size multi-column layout
			// (MulticolumnBlockBox) was indexed, so auto-height columns bypassed SourceReplayer's
			// "do not source-replay ranges containing columns" barrier and MeasuredIntrinsics
			// fallback. M2c measurement did not reproduce the columns in scratch layout,
			// and returned a width inflated by the column count without the columnInflated flag,
			// placing float:right outside the paper in vertical writing within multi-column layout.
			case BoxRecipe.Flow f -> {
				if (f.params().hasMultipleColumns()) this.multicolIds.add(id);
			}
			case BoxRecipe.Grid g -> this.gridIds.add(id);
			case BoxRecipe.Flex f -> this.flexIds.add(id);
			case BoxRecipe.Absolute a -> this.absoluteIds.add(id);
			case BoxRecipe.FloatBlock f -> this.floatIds.add(id);
			case BoxRecipe.Inline inline -> { }
			case BoxRecipe.Marker marker -> { }
			case BoxRecipe.InlineBlock inline -> { }
			case BoxRecipe.InsideMarker marker -> { }
			case BoxRecipe.TableRowGroup group -> { }
			case BoxRecipe.TableRow row -> { }
			case BoxRecipe.TableCell cell -> { }
			case BoxRecipe.TableColumnGroup group -> { }
			case BoxRecipe.TableColumn column -> { }
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
		case AnonymousItemStart start -> { }
		case AnonymousItemEnd end -> { }
		case EndBlock end -> { }
		case Chars chars -> { }
		case Leader leader -> { }
		case Assignment assignment -> { }
		}
	}

	/** Rebuilds the index after {@code compact()} (alongside the kept scan). */
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

	/**
	 * Returns the position of the first retained event at or after id (internal use).
	 * Binary-searches the id sequence made sparse by compaction.
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
	 * A retention lease for an unconsumed replay range (reference-counted; C1c).
	 * Owned by source-only continuation items that carry no boxes, and closed once consumption finishes.
	 * While the lease is alive, compact does not discard events before its fromId. close is idempotent.
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

		public boolean isClosed() {
			return this.closed;
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
	 * Retention leases for events at or after fromId (fromId → reference count).
	 * Reference-counted because multiple continuations may independently own the same fromId
	 * (with a simple set, releasing one would remove the other's pin; noted in external review).
	 */
	private final java.util.TreeMap<Long, Integer> retentionLeases = new java.util.TreeMap<>();
	private long retentionLeaseCount;
	private boolean closed;
	private final java.util.Set<RangeHandle> openRanges =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	void registerRange(final RangeHandle handle) { this.openRanges.add(handle); }
	void releaseRange(final RangeHandle handle) { this.openRanges.remove(handle); }

	private void reportRetention() {
		net.zamasoft.foliojet.layout.builder.impl.TableBuildStats.reportSourceRetention(
				this.retentionLeaseCount, this.entries.size(),
				this.retentionLeases.isEmpty() ? -1 : this.retentionLeases.firstKey(), this.nextId);
	}

	/**
	 * Acquires a lease retaining events at or after fromId.
	 */
	public RetentionLease retainFrom(final long fromId) {
		return this.retainFrom(fromId, true);
	}

	/** Does not register with the current attachment when acquired by an explicit scratch owner. */
	RetentionLease retainFrom(final long fromId, final boolean registerScratch) {
		if (this.closed) throw new IllegalStateException("終了済みソースの保持");
		this.retentionLeases.merge(fromId, 1, Integer::sum);
		++this.retentionLeaseCount;
		this.reportRetention();
		final RetentionLease lease = new RetentionLease(fromId);
		if (registerScratch) ScratchReplayScope.register(lease);
		return lease;
	}

	private void release(final long fromId) {
		if (this.closed) return;
		final Integer count = this.retentionLeases.get(fromId);
		if (count == null) {
			throw new IllegalStateException("リースの対応が壊れています: " + fromId);
		}
		if (count <= 1) {
			this.retentionLeases.remove(fromId);
		} else {
			this.retentionLeases.put(fromId, count - 1);
		}
		--this.retentionLeaseCount;
	}

	/**
	 * Discards events before watermark, preserving open Starts. The ids of open Starts do not change.
	 * The smallest fromId of the live retention leases clamps watermark internally
	 * (callers need not combine the watermark and leases).
	 *
	 * <p>
	 * <b>Consistency with spilled records (E-6 increment 3b-2)</b>:
	 * A {@link TextPayload.Spilled} record removed from entries remains in {@link TextSpill}
	 * (the initial version does not reclaim disk space partway through; codex decision).
	 * Only the heap reference to recordId disappears; this is not a leak, since
	 * {@link #close()} (in the finally block of the conversion termination path) deletes the entire temporary
	 * file. Inline payloads are subtracted from budget accounting ({@code liveInlineTextBytes}) when the last
	 * ownership by the main store and text slices ends, allowing subsequent appends to return to inline
	 * storage.
	 * </p>
	 *
	 * @param watermark events before this value (id &lt; watermark) are eligible for discard
	 */
	public void compact(final long watermark) {
		if (this.compactionCheckpoint != null) this.compactionCheckpoint.request(0, watermark);
		this.compact(0, watermark);
	}

	private CompactionCheckpoint compactionCheckpoint;

	/**
	 * Reapplies C's reclamation ranges blocked by the tee's protective lease after B advances its pin.
	 * Coalesces overlapping requests into intervals and forgets applied intervals. Retains no input or text.
	 */
	public CompactionCheckpoint checkpointCompaction() {
		if (this.compactionCheckpoint != null) throw new IllegalStateException("compact観測の重複");
		return this.compactionCheckpoint = new CompactionCheckpoint();
	}

	public final class CompactionCheckpoint implements AutoCloseable {
		private final java.util.TreeMap<Long, Long> requests = new java.util.TreeMap<>();
		private boolean closed;

		private CompactionCheckpoint() { }

		private void request(final long fromId, final long watermark) {
			if (watermark <= fromId) return;
			long from = fromId, to = watermark;
			final var previous = this.requests.floorEntry(from);
			if (previous != null && previous.getValue() >= from) {
				from = previous.getKey();
				to = Math.max(to, previous.getValue());
				this.requests.remove(previous.getKey());
			}
			for (var next = this.requests.ceilingEntry(from); next != null && next.getKey() <= to;
					next = this.requests.ceilingEntry(from)) {
				to = Math.max(to, next.getValue());
				this.requests.remove(next.getKey());
			}
			this.requests.put(from, to);
		}

		public int pendingRequestCount() {
			return this.requests.size();
		}

		/** Reapplies only intervals requested by C. Does not compact the main log using B's own watermark. */
		public void reapply() {
			final long retained = LayoutSource.this.retentionLeases.isEmpty() ? Long.MAX_VALUE
					: LayoutSource.this.retentionLeases.firstKey();
			final var iterator = this.requests.entrySet().iterator();
			while (iterator.hasNext()) {
				final var request = iterator.next();
				if (retained > request.getKey()) LayoutSource.this.compact(request.getKey(), request.getValue());
				if (retained >= request.getValue()) iterator.remove();
			}
		}

		@Override
		public void close() {
			if (this.closed) return;
			this.closed = true;
			LayoutSource.this.compactionCheckpoint = null;
			try {
				this.reapply();
			} finally {
				this.requests.clear();
			}
		}
	}

	/**
	 * Reclaims only the collected portion of a Retained table placed immediately.
	 * Protects unplaced content preceding the table and never advances past the smallest fromId of OPEN leases.
	 * During recording, called each time the watermark advances by 1024 events; uses force at Pass B/C
	 * boundaries.
	 */
	public boolean compactRetainedTable(final long tableId, final long watermark, final boolean force) {
		if (this.compactionCheckpoint != null && tableId >= 0
				&& (force || watermark - Math.max(tableId, this.retainedTableWatermark) >= 1024)) {
			this.compactionCheckpoint.request(tableId, watermark);
		}
		final long clamped = this.retentionLeases.isEmpty() ? watermark
				: Math.min(watermark, this.retentionLeases.firstKey());
		if (tableId < 0 || clamped <= tableId || clamped <= this.retainedTableWatermark
				|| (!force && clamped - Math.max(tableId, this.retainedTableWatermark) < 1024)) return false;
		this.compact(tableId, clamped);
		this.retainedTableWatermark = clamped;
		return true;
	}

	private long retainedTableWatermark = -1;

	private void releaseEntry(final Entry entry) {
		if (--entry.owners == 0) this.releaseText(entry.event());
	}

	private void releaseText(final Event event) {
		if (event instanceof Chars chars && chars.payload() instanceof TextPayload.Inline inline) {
			this.liveInlineTextBytes -= (long) inline.utf16Length() * 2;
		}
	}

	private void compact(final long fromId, final long watermark) {
		final long clamped = this.retentionLeases.isEmpty() ? watermark
				: Math.min(watermark, this.retentionLeases.firstKey());
		final List<Entry> kept = new ArrayList<Entry>();
		// Find the stack of "unmatched Starts" in the range to discard.
		final List<Entry> open = new ArrayList<Entry>();
		for (final Entry entry : this.entries) {
			if (entry.id() < fromId) {
				kept.add(entry);
				continue;
			}
			if (entry.id() >= clamped) {
				break;
			}
			switch (entry.event()) {
			// Opaque is also a start event paired with EndBlock (startBox pushes Opaque,
			// endBox pushes EndBlock). Without the same handling as Start,
			// Opaque's matching EndBlock incorrectly pops an ancestor Start,
			// and repeated compaction destroys the open chain (fixed 2026-07-17).
			case Start start -> open.add(entry);
			case AnonymousItemStart start -> open.add(entry);
			case Opaque opaque -> open.add(entry);
			case AnonymousItemEnd end -> {
				if (!open.isEmpty()) {
					this.releaseEntry(open.remove(open.size() - 1));
				}
				this.releaseEntry(entry);
			}
			case EndBlock end -> {
				if (!open.isEmpty()) {
					this.releaseEntry(open.remove(open.size() - 1));
				}
				this.releaseEntry(entry);
			}
			case Chars chars -> {
				this.releaseEntry(entry);
			}
			case Replaced replaced -> {
				this.releaseEntry(entry);
			}
			case Assignment assignment -> {
				this.releaseEntry(entry);
			}
			case Leader leader -> {
				this.releaseEntry(entry);
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
		// Live lease ranges remain retained after compact.
		assert this.retentionLeases.isEmpty() || this.indexOf(this.retentionLeases.firstKey()) >= 0
				|| this.retentionLeases.firstKey() >= this.nextId : this.retentionLeases.firstKey();
		final var observer = compactObserver;
		if (observer != null) observer.accept(this);
	}

	/**
	 * Returns the id of the end event matching the Start/AnonymousItemStart with the given id.
	 * PlacedTable also pushes just one Start. Returns -1 if the subtree is still open.
	 */
	public long endOf(final long startId) {
		int index = this.indexOf(startId);
		if (index < 0 || !(this.entries.get(index).event() instanceof Start
				|| this.entries.get(index).event() instanceof AnonymousItemStart)) {
			return -1;
		}
		int depth = 0;
		for (int i = index; i < this.entries.size(); ++i) {
			switch (this.entries.get(i).event()) {
			// Opaque is a start event paired with EndBlock (the same symmetry as compact).
			case Start start -> ++depth;
			case AnonymousItemStart start -> ++depth;
			case Opaque opaque -> ++depth;
			case AnonymousItemEnd end -> {
				if (--depth == 0) {
					return this.entries.get(i).id();
				}
			}
			case EndBlock end -> {
				if (--depth == 0) {
					return this.entries.get(i).id();
				}
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			case Assignment assignment -> {
			}
			case Leader leader -> {
			}
			}
		}
		return -1;
	}

	/**
	 * Returns true if the range [fromId, toId] contains an Opaque (non-replayable) event.
	 */
	public boolean containsOpaque(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.opaqueIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains a table caption ({@link BoxKind#CAPTION}) Start
	 * (caption recipes C1, 2026-08-01; consult-codex-2026-08-01-caption-recipe.txt).
	 *
	 * <p>
	 * Captions are context-dependent kinds (replay requires an enclosing TableBuilder), so C1 rejects the same
	 * ranges using the same conditions as the former Opaque recording (routing unchanged).
	 * C2 replaces this with context-complete validation (establishing the matching TABLE Start within the
	 * range).
	 * </p>
	 */
	public boolean containsCaption(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.captionIds.anyInRange(fromId, toId);
	}


	/**
	 * The blanket caption gate and observation (for replay paths invoked at page-break frequency:
	 * {@code stampRanges} /{@code canReplayChildren}). Always returns true for ranges containing captions
	 * (= routes to box-restyle).
	 *
	 * <p>
	 * <b>Attempted to make this a selective gate in C4, then reverted (measured 2026-08-01)</b>:
	 * A multi-page table with captions (0217, repeated thead/tfoot, 100px pages) replayed the entire table at
	 * every page break, killing conversion with Java heap space (tableReplays 37→3891).
	 * Enabling a path invoked at page-break frequency differs from enabling a bind path that guarantees
	 * one-time replay. Only the TwoPass seal side, which binds once, uses a selective gate
	 * ({@link #captionSealGate}); this side permanently retains blanket rejection plus observation.
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
	 * The selective caption gate (only for TwoPass seal with one-time bind, C4).
	 * Allows absorption and range bind even for ranges containing captions if context-complete validation
	 * passes (the matching TABLE Start is established and fully closed within the range).
	 * Permanently rejects a standalone CAPTION root or a range cut partway through (the G-1 crash shape).
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

	/**
	 * Returns whether [fromId, toId] is self-contained for context-dependent kinds
	 * (caption recipes C2, 2026-08-01; consult-codex-2026-08-01-caption-recipe.txt Q1).
	 * This is stronger than merely checking that the root is not CAPTION, and requires all of the following:
	 *
	 * <ul>
	 * <li>At each CAPTION Start, the stack contains an explicit TABLE opened within the range
	 * (structurally forbids CAPTION as the range root, preventing a recurrence of the G-1 standalone replay
	 * root crash)</li>
	 * <li>At the range end, no box started within the range remains open
	 * (forbids ranges that cut through a CAPTION)</li>
	 * <li>No unmatched EndBlock occurs within the range</li>
	 * </ul>
	 *
	 * <p>
	 * {@link #isIntact} detects sparse ranges (callers combine the checks).
	 * Opaque is pushed as an unknown start event but does not establish a TABLE (fail closed).
	 * C2 uses this only for shadow observation; actual routing retains the blanket rejection by
	 * {@link #containsCaption} . C4 promotes it to a selective gate.
	 * </p>
	 */
	public boolean isContextCompleteRange(final long fromId, final long toId) {
		int index = this.indexOf(fromId);
		if (index < 0) {
			return false;
		}
		// Kinds opened within the range (check that a TABLE is established for CAPTION)
		final java.util.ArrayDeque<Event> stack = new java.util.ArrayDeque<>();
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
					// CAPTION with no TABLE established within the range (standalone root or outside a table)
					return false;
				}
				if (kind == net.zamasoft.foliojet.layout.segment.BoxKind.TABLE) {
					++tableDepth;
				}
				stack.push(entry.event());
			}
			case AnonymousItemStart start -> stack.push(start);
			case AnonymousItemEnd end -> {
				if (stack.isEmpty() || !(stack.pop() instanceof AnonymousItemStart)) {
					return false;
				}
			}
			case Opaque opaque -> {
				// Unknown start events cannot guarantee stack pairing;
				// fail closed as ineligible (containsOpaque rejects Opaque ranges anyway).
				return false;
			}
			case EndBlock end -> {
				if (stack.isEmpty() || !(stack.peek() instanceof Start)) {
					// EndBlock closing a box opened outside the range (the range straddles boxes)
					return false;
				}
				if (((Start) stack.pop()).recipe().kind() == net.zamasoft.foliojet.layout.segment.BoxKind.TABLE) {
					--tableDepth;
				}
			}
			case Chars chars -> {
			}
			case Replaced replaced -> {
			}
			case Assignment assignment -> {
			}
			case Leader leader -> {
			}
			}
		}
		// All boxes started within the range must be closed.
		return stack.isEmpty();
	}

	/** Whether the range contains a Grid Start. Also true if the range start is missing. */
	public boolean containsGrid(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.gridIds.anyInRange(fromId, toId);
	}

	/** Whether the range contains a Flex Start. Also true if the range start is missing. */
	public boolean containsFlex(final long fromId, final long toId) {
		// Fail closed (true) when fromId is missing, as in containsGrid.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.flexIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains a multi-column Start
	 * (M6c: replay of multi-column content falls back because interaction with the column mechanism
	 * (columnBreak/balance) has not been verified).
	 */
	public boolean containsMulticol(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.multicolIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains content with a writing direction different from the specified one
	 * (M6b: replay mixing vertical and horizontal writing falls back because reproduction of the sub-builder
	 * context has not been designed).
	 */
	public boolean containsMixedFlow(final long fromId, final long toId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		// RangeSummary(2026-08-01): Check for flow starts oriented differently from target within the range.
		// The queried index is always for the direction opposite to the document's main direction,
		// which is sparse in typical documents (vertical starts in horizontal documents, and vice versa).
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return (rootFlow.isVertical() ? this.horizontalFlowIds : this.verticalFlowIds).anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains a floating-position Start
	 * (M6c: source replay for balancing falls back because reproduction of float anchoring has not been
	 * verified).
	 */
	public boolean containsFloat(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.floatIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains a table ({@link BoxKind#TABLE}) Start
	 * (G-1, 2026-07-25; restored with user approval of the table-set implementation on 2026-07-30).
	 *
	 * <p>
	 * <b>Why this is needed (G-1 measurements)</b>: Even if recording tables as recipes makes a range
	 * replayable,
	 * <b>whether replay is allowed</b> remains a separate question for each consumer.
	 * Once {@code MeasuredIntrinsics} (measurement through actual layout) accepts ranges containing tables,
	 * intrinsic sizing <b>switches algorithms entirely</b>, from simulated measurement to actual source replay
	 * on a scratch page of infinite width, breaking output. In measurements,
	 * the shrink-to-fit float widths in {@code 0070-table-layout/float-in-auto-4.html} diverged from
	 * 376/414.5/276/216 to 500 pt (= page width) in every case
	 * (because percentage-sized table cells resolve against 1e6 on the infinite-width page).
	 * This gate is analogous to the separation introduced for absolute positioning by
	 * {@link #containsAbsolute} in E-6 increment 4e.
	 * The table replay consumer (T-c) enables only direct replay in {@code restyleItem case TABLE} ;
	 * these indirect consumers reject ranges containing tables until explicitly enabled in stages.
	 * </p>
	 */
	public boolean containsTable(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.tableIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns true if [fromId, toId] contains an absolutely positioned block Start
	 * (E-6 increment 4e, 2026-07-24).
	 *
	 * <p>
	 * Before increment 4e, absolute positioning was recorded as {@link Opaque} , so
	 * {@link #containsOpaque} implicitly made all replay paths fall back.
	 * After switching to recipe recording (eligibility applies to sealing <b>the absolute builder's own
	 * body</b>), replay of ranges <b>containing</b> absolute positioning still falls back.
	 * Absolutely positioned boxes are anchored to the context builder
	 * ({@code addAbsolute} in {@code BlockBuilder.addBound} ) and have deferred bind
	 * ({@code finishLayoutSelf} at page end). Reconstructing them through range replay creates duplicate
	 * registration of the live anchored box and the newly replayed box, or an unbound DeferredBind
	 * (a leftover lease). This is the absolute-positioning counterpart of the re-anchoring gate
	 * in {@link #containsFloat} . Absolutely positioned replaced elements (ABSOLUTE in {@code ReplacedRecipe} )
	 * remain excluded from this check because replay supported them before increment 4e with established
	 * behavior.
	 * </p>
	 */
	public boolean containsAbsolute(final long fromId, final long toId) {
		// RangeSummary(2026-08-01): Replace the linear scan with binary search of the sparse index.
		// Preserve fail-closed behavior (true) when fromId is missing.
		if (this.indexOf(fromId) < 0) {
			return true;
		}
		return this.absoluteIds.anyInRange(fromId, toId);
	}


	/**
	 * Returns whether all absolutely positioned Starts (Absolute and absolute PlacedTable) in [fromId, toId]
	 * <b>exactly match</b> {@code ownedAnchors}
	 * (absolute absorption = codex increment 9, 2026-07-30; no side effects).
	 *
	 * <p>
	 * Replaces {@code containsAbsolute} (any occurrence means false = fail closed) with permission only when
	 * all are owned (the ownership ledger has proved exclusive ownership), rejecting any unmatched occurrence.
	 * An unmatched Start indicates an absolute belonging to an outer context or another execution plan
	 * (such as a table cell); absorbing it duplicates anchoring or orphans a lease.
	 * Also returns false if the start of the range has been lost to compaction.
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
			if (entry.event() instanceof Start(final BoxRecipe recipe) && (recipe instanceof BoxRecipe.Absolute
					|| recipe instanceof BoxRecipe.PlacedTable table && table.placement() instanceof BoxRecipe.Absolute)) {
				if (!ownedAnchors.contains(entry.id())) {
					return false;
				}
				++matched;
			}
		}
		// All IDs in ownedAnchors must be actual Absolute Starts within the range.
		// (Collection already validated ranges and kinds; compare counts as a second barrier.)
		return matched == ownedAnchors.size();
	}

	/**
	 * A streaming view of the event range read by a single replay (E-6 increment 3a, 2026-07-24:
	 * changed from owning a full List copy to a reference view of the store (main contents),
	 * range [fromId, toId], and retention lease).
	 *
	 * <p>
	 * <b>Protection from compact</b>: At capture time, validates range integrity (consecutive ids without gaps)
	 * and acquires its own {@link RetentionLease} (fromId). The watermark clamp in {@link #compact(long)}
	 * keeps compaction caused by nested page breaks within the visitor (clear + addAll on the backing list)
	 * before fromId, structurally preventing discard of events in the range during streaming iteration.
	 * This replaces the isolation guarantee of the former full copy, enforced by
	 * LayoutSourceTest.testReplaySurvivesNestedCompaction.
	 * The numeric index is revalidated against the id for each event, so rebuilding the backing list in nested
	 * compaction cannot shift it (no silent skipping; also preserves the guarantee noted in external review).
	 * </p>
	 * <p>Already extracted text bodies share an immutable array for reading. No lease on the main store is
	 * needed;
	 * the array's share count protects the body and spill lifetimes until replay finishes.</p>
	 *
	 * <p>
	 * <b>consume-once</b>: {@link #replay} may be called only once and releases the lease on completion
	 * (in finally even on exceptions). To abandon without replay, use {@link #close()} (idempotent).
	 * A leftover lease permanently clamps subsequent compaction (a retention leak),
	 * so every consumption path must take one of these two routes.
	 * </p>
	 */
	public final class ReplaySlice implements AutoCloseable {
		private final long fromId;
		private final long toId;
		private final RetentionLease lease;
		private final SealedTextSlice textSlice;
		private boolean consumed = false;

		private ReplaySlice(final long fromId, final long toId) {
			this.fromId = fromId;
			this.toId = toId;
			this.lease = LayoutSource.this.retainFrom(fromId);
			this.textSlice = null;
		}

		private ReplaySlice(final SealedTextSlice textSlice, final long fromId, final long toId) {
			this.fromId = fromId;
			this.toId = toId;
			this.lease = null;
			this.textSlice = textSlice;
			++textSlice.readers;
		}

		public long fromId() {
			return this.fromId;
		}

		public long toId() {
			return this.toId;
		}

		/**
		 * Passes each event in the range to visitor in order (consume-once).
		 * The lease protects the cursor's unread range from compact, making this safe against nested page
		 * breaks
		 * inside the visitor. Releases the lease on completion or exception.
		 */
		public void replay(final java.util.function.Consumer<Event> visitor) {
			if (this.consumed) {
				throw new IllegalStateException(
						"ReplaySlice は consume-once: [" + this.fromId + ", " + this.toId + "]");
			}
			this.consumed = true;
			try {
				if (this.textSlice != null) {
					final int first = Math.toIntExact(this.fromId - this.textSlice.fromId);
					final int length = Math.toIntExact(this.toId - this.fromId + 1);
					for (int i = first; i < first + length; ++i) visitor.accept(this.textSlice.chars[i]);
					return;
				}
				final List<Entry> entries = LayoutSource.this.entries;
				int hint = -1;
				for (long id = this.fromId; id <= this.toId; ++id) {
					// The visitor (nested compact) may have rebuilt the backing list,
					// so validate the index against the id each time.
					if (hint < 0 || hint >= entries.size() || entries.get(hint).id() != id) {
						hint = LayoutSource.this.indexOf(id);
						if (hint < 0) {
							// Cannot happen while the lease protects events from fromId onward.
							throw new IllegalStateException("replay range lost during streaming replay: id=" + id
									+ " of [" + this.fromId + ", " + this.toId + "]");
						}
					}
					final Event event = entries.get(hint).event();
					++hint;
					visitor.accept(event);
				}
			} finally {
				this.release();
			}
		}

		/**
		 * Abandons without consumption (releases the lease; idempotent).
		 */
		@Override
		public void close() {
			if (this.consumed) return;
			this.consumed = true;
			this.release();
		}

		private void release() {
			if (this.lease != null) this.lease.close();
			else this.textSlice.release();
		}
	}

	/**
	 * Text bodies of cells not absorbed into a parent TwoPass. Reconstructs consecutive EventIds from the start
	 * plus array position. Cells containing structural events retain a lease on the main store, so endOf/range
	 * validation in nested seal and parent capture never read structure that has disappeared from the main
	 * store.
	 * ReplaySlice also holds a share count, preserving the payload even if the handle terminates during replay.
	 * Spilled contents survive until LayoutSource.close, which terminates all handles first.
	 */
	final class SealedTextSlice {
		private final long fromId;
		private Chars[] chars;
		private int readers = 1;

		private SealedTextSlice(final long fromId, final Chars[] chars) {
			this.fromId = fromId;
			this.chars = chars;
			LayoutSource.this.textSlices.put(fromId, this);
			LayoutSource.this.slicedEvents += chars.length;
		}

		ReplaySlice capture() {
			if (this.readers == 0) throw new IllegalStateException("終了済みの本文slice");
			return new ReplaySlice(this, this.fromId, this.toId());
		}

		private long toId() { return this.fromId + this.chars.length - 1; }

		void release() {
			if (--this.readers == 0) {
				for (int i = 0; i < this.chars.length; ++i) {
					final int index = LayoutSource.this.indexOf(this.fromId + i);
					// Return budget ownership if the main entry remains; after compaction, the slice is the last owner.
					if (index >= 0) LayoutSource.this.releaseEntry(LayoutSource.this.entries.get(index));
					else LayoutSource.this.releaseText(this.chars[i]);
				}
				LayoutSource.this.textSlices.remove(this.fromId);
				LayoutSource.this.slicedEvents -= this.chars.length;
				this.chars = null;
				if (LayoutSource.this.closed && LayoutSource.this.textSlices.isEmpty()
						&& LayoutSource.this.textSpill != null) LayoutSource.this.textSpill.close();
			}
		}
	}

	private final java.util.TreeMap<Long, SealedTextSlice> textSlices = new java.util.TreeMap<>();
	private int slicedEvents;

	private SealedTextSlice textSliceContaining(final long fromId, final long toId) {
		final var entry = this.textSlices.floorEntry(fromId);
		if (entry == null || toId < fromId) return null;
		final SealedTextSlice slice = entry.getValue();
		return toId <= slice.toId() ? slice : null;
	}

	/** Owners replaying the same body independently also share the array without changing EventIds. */
	SealedTextSlice retainTextSlice(final long fromId, final long toId) {
		final SealedTextSlice slice = this.textSlices.get(fromId);
		if (slice == null || slice.toId() != toId) return null;
		++slice.readers;
		return slice;
	}

	/** Seals a text-only closed interval. Bodies containing structure remain retained by leases. */
	SealedTextSlice sealTextSlice(final long fromId, final long toId) {
		if (!this.isIntact(fromId, toId)) throw new IllegalStateException("seal本文の欠落");
		// Share identical ranges via retainTextSlice. Protect partial overlaps with ordinary leases.
		// At most one slice accounts for each Entry's budget; slices do not retain the Entry itself.
		final var previous = this.textSlices.floorEntry(toId);
		if (previous != null && previous.getValue().toId() >= fromId) return null;
		final int first = this.indexOf(fromId);
		final int length = Math.toIntExact(toId - fromId + 1);
		for (int i = 0; i < length; ++i) {
			if (!(this.entries.get(first + i).event() instanceof Chars)) return null;
		}
		final Chars[] chars = new Chars[length];
		for (int i = 0; i < length; ++i) {
			final Entry entry = this.entries.get(first + i);
			chars[i] = (Chars) entry.event();
			++entry.owners;
		}
		return new SealedTextSlice(fromId, chars);
	}

	/**
	 * Acquires a validated streaming view of [fromId, toId] with a lease
	 * (replaced the immutable snapshot (full copy) in E-6 increment 3a).
	 * Returns null if an endpoint has been discarded, the range contains a discarded gap, or it does not reach
	 * toId (fallback or failure according to the caller's contract).
	 */
	public ReplaySlice capture(final long fromId, final long toId) {
		if (this.closed) throw new IllegalStateException("終了済みソースの再生");
		final SealedTextSlice slice = this.textSliceContaining(fromId, toId);
		if (slice != null) return new ReplaySlice(slice, fromId, toId);
		if (!this.isIntact(fromId, toId)) {
			return null;
		}
		return new ReplaySlice(fromId, toId);
	}

	/**
	 * Returns whether [fromId, toId] is retained without gaps
	 * (checks continuity in the main store; capture can also read extracted text slices).
	 * Queries without acquiring a lease, for the <b>recording side</b> of replay ranges
	 * ({@code RootBuilder.stampRanges}).
	 *
	 * <p>
	 * <b>Why recording also needs this (2026-07-27)</b>:
	 * {@link #compact(long)} preserves only open (unmatched) Starts before the watermark.
	 * An element still open at a break can therefore have <b>only its Start retained, with its contents
	 * gone</b>.
	 * When it later closes, {@link #endOf(long)} traverses the sparse retained sequence and returns a
	 * plausible-looking end, so without checking density the range is incorrectly judged replayable.
	 * </p>
	 */
	public boolean isIntact(final long fromId, final long toId) {
		final int index = this.indexOf(fromId);
		if (index < 0 || toId < fromId) {
			return false;
		}
		// EventIds are assigned consecutively and remain ordered after discard (strictly
		// increasing). Thus, if entries[index].id == fromId and
		// entries[index + n].id == toId == fromId + n, the n+1 entries between them
		// must be consecutive == no gaps (equivalent to the former sequential per-entry validation).
		final long offset = toId - fromId;
		if (offset > this.entries.size() - 1 - index) {
			return false;
		}
		final int last = index + (int) offset;
		return this.entries.get(last).id() == toId;
	}

	/**
	 * Passes events in [fromId, toId] to visitor in order.
	 * Internally acquires a validated streaming view with a lease before replay,
	 * so nested page breaks (compact) inside the visitor are safe.
	 * Fails before execution if the range is incomplete (callers that can fall back should use {@link #capture}
	 * ).
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
