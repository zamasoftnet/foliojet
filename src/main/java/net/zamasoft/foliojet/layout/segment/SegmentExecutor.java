package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;

/**
 * Shared Segment executor (introduced on 2026-07-24, E-6 increment 3b-1:
 * design consultation §2.3 increment 1).
 *
 * <p>
 * Centralizes the "execution" part of the replay switch duplicated in {@code SourceReplayer.drive}:
 * driving {@code DocumentBuilder}, reattaching ordinals ({@code EventId}) as {@code SourceAnchor},
 * and making fresh copies of text. Takes input through a streaming cursor (one event per call)
 * and does not require a List.
 * </p>
 *
 * <p>
 * <b>Single switch (consolidation completed in E-6 increment 3b-6)</b>: the transitional
 * {@code executeLive(LayoutSource.Event)} path was consolidated into {@link #execute(SegmentEvent)}
 * after replaced-element freezing became a total function (duplicate-based freezing of
 * {@code ReplacedBoxImage} in {@code ReplacedParamsTemplate}), removing the live variant
 * ({@code ReplacedLive}). The caller ({@code SourceReplayer}) converts {@code LayoutSource.Event}
 * on the fly with {@code LayoutSourceEventConverter.convert} and executes it.
 * </p>
 *
 * <p>
 * <b>StructureToken interning (E-6 increment 3b-4)</b>: materializing a recipe creates independent
 * {@code Params} for each event. When the same logical element has multiple Start events
 * (e.g., the principal box and marker box of a {@code <li>}), the {@code Params.element} tokens
 * are also separate instances. Preventing duplicate structure-tag openings in Tagged PDF
 * (the identity set in {@code PageBox.beginStruct}) requires "same logical element = same instance."
 * This executor (= one replay session) therefore interns by {@code elementKey} to reproduce the
 * identity maintained by shared {@code CSSElement} instances on the live path
 * (codex decision: development record).
 * </p>
 *
 * <p>
 * <b>Fresh copies of Chars (corrected in 3b-1)</b>: passing a recorded {@code char[]} directly to
 * {@code doc.characters} lets {@code StyledTextUnitizer.characters} modify the array in place,
 * so replaying the same range could transform already transformed text again
 * (recording made a defensive copy, but replay passed the array directly).
 * Execution through {@link SegmentEvent.Text} is structurally safe because
 * {@code String#toCharArray()} returns a fresh array each time.
 * </p>
 */
public final class SegmentExecutor {
	/** Specifies whether to attach main-source lineage or lay out independent repeated content. */
	public enum AnchorMode { SOURCE, NONE }

	private final DocumentBuilder doc;
	private final AnchorMode anchorMode;

	/**
	 * Ordinal (= EventId) of the next event to execute. Reattaches it to the replay instance as a
	 * {@code SourceAnchor} (P0: anchors belong to individual boxes, providing lineage for replay at the
	 * next break. Only the "ordinal→SourceAnchor" mapping must be preserved, not the identity of the
	 * original params/pos/box; codex design §1.3).
	 */
	private long eventId;

	/**
	 * StructureToken interning within a replay session (E-6 increment 3b-4; see
	 * "StructureToken interning" in the class Javadoc). Maps a key ({@code elementKey})
	 * to the first materialized token instance.
	 */
	private final java.util.HashMap<Long, StructureToken> structureTokens = new java.util.HashMap<>();

	/**
	 * Sequence of kinds of boxes open in this replay session (the final safeguard in caption recipe
	 * conversion C2, 2026-08-01: consult-codex-2026-08-01-caption-recipe.txt Q1).
	 * Stops a context-dependent kind (CAPTION) that reaches {@code doc.startBox()} without an established
	 * TABLE by throwing a typed exception, even if it evades range eligibility checks (context-complete
	 * validation). Turns the G-1 standalone replay-root crash (ClassCastException) into a specified failure.
	 */
	private final java.util.ArrayDeque<SegmentEvent> openKinds = new java.util.ArrayDeque<>();

	/** Number of TABLE entries in {@link #openKinds} (for checking CAPTION context). */
	private int openTables;

	/**
	 * @param doc    Execution target (a fresh {@code DocumentBuilder})
	 * @param fromId EventId at the start of the range (maps 1:1 to the slice ordinal)
	 */
	public SegmentExecutor(final DocumentBuilder doc, final long fromId) {
		this(doc, fromId, AnchorMode.SOURCE);
	}

	public SegmentExecutor(final DocumentBuilder doc, final AnchorMode anchorMode) {
		this(doc, 0, anchorMode);
	}

	private SegmentExecutor(final DocumentBuilder doc, final long fromId, final AnchorMode anchorMode) {
		this.doc = doc;
		this.eventId = fromId;
		this.anchorMode = java.util.Objects.requireNonNull(anchorMode);
	}

	/** Executes an independent sequence of events in order. Only execute updates the event number. */
	public void drive(final Iterable<? extends SegmentEvent> events) {
		for (final SegmentEvent event : events) {
			this.execute(event);
		}
	}

	/**
	 * Replaces {@code element} in materialized params with the canonical token for this replay session
	 * (E-6 increment 3b-4). {@code StructureToken.freeze} creates tokens only for real elements with
	 * elementKey&gt;=0 ({@code elementKey<0} is excluded because the static singleton {@code CSSElement}
	 * is stored directly; the shared singleton already preserves identity).
	 * Package-private to allow direct verification from unit tests ({@code StructureTokenTest}).
	 */
	void internStructureToken(final net.zamasoft.foliojet.layout.box.params.Params params) {
		if (params.element instanceof StructureToken token && token.elementKey() >= 0) {
			params.element = this.structureTokens.computeIfAbsent(token.elementKey(), key -> token);
		}
	}

	/**
	 * Executes one canonical event ({@link SegmentEvent}).
	 * {@link SegmentEvent.Barrier} cannot be executed; the caller must validate range eligibility
	 * in advance, so encountering one here is a failure.
	 */
	public void execute(final SegmentEvent event) {
		if (this.anchorMode == AnchorMode.NONE) {
			this.doc.startReplayOnlyEvent(event, this.eventId);
		}
		try {
			this.dispatch(event);
		} finally {
			if (this.anchorMode == AnchorMode.NONE) {
				this.doc.finishReplayOnlyEvent();
			}
		}
		++this.eventId;
	}

	private void dispatch(final SegmentEvent event) {
		switch (event) {
		case SegmentEvent.Assignment assignment -> {
			// Rebuild placement only. Run each assignment once from the original anchor on the finalized page.
		}
		case SegmentEvent.BeginBox(final BoxRecipe recipe) -> {
			final BoxKind kind = recipe.kind();
			if (kind == BoxKind.CAPTION && this.openTables == 0) {
				// Final safeguard (C2): range eligibility must reject CAPTION without
				// table context; reaching this point indicates a defect in eligibility checking.
				throw new IllegalStateException(
						"表文脈(TABLE Start)の確立なしにCAPTIONを再生しようとしました: eventId=" + this.eventId);
			}
			if (kind == BoxKind.TABLE) {
				++this.openTables;
			}
			this.openKinds.push(event);
			// Recorded values, including strictLineBox, are shared by initial bind, measurement, page breaks, and repeated-content replay.
			final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
			// Pass PlacedTable as a single TableBox too. TableBuilderLifecycle builds its host
			// with the same placement as the live path, so do not create extra BeginBox/EndBox events here.
			this.internStructureToken(box.getParams());
			if (this.anchorMode == AnchorMode.SOURCE) {
				box.setSourceAnchor(this.eventId);
			}
			this.doc.startBox(box);
		}
		case SegmentEvent.EndBox end -> {
			if (!this.openKinds.isEmpty()) {
				if (!(this.openKinds.pop() instanceof SegmentEvent.BeginBox begin)) {
					throw new IllegalStateException("匿名項目をEndBoxで閉じようとしました: eventId=" + this.eventId);
				}
				if (begin.recipe().kind() == BoxKind.TABLE) {
					--this.openTables;
				}
			}
			this.doc.endBox();
		}
		case SegmentEvent.AnonymousItemStart(final long anchor) -> {
			this.openKinds.push(event);
			this.doc.startAnonymousItem(this.anchorMode == AnchorMode.SOURCE ? anchor : -1);
		}
		case SegmentEvent.AnonymousItemEnd end -> {
			if (this.openKinds.isEmpty() || !(this.openKinds.pop() instanceof SegmentEvent.AnonymousItemStart)) {
				throw new IllegalStateException("対応する匿名項目Startがありません: eventId=" + this.eventId);
			}
			this.doc.endAnonymousItem();
		}
		case SegmentEvent.Text(final int sourceOffset, final String text, final boolean fixed) -> {
			// toCharArray() returns a fresh array each time (safe for downstream in-place transformations).
			final char[] ch = text.toCharArray();
			this.doc.characters(sourceOffset, ch, 0, ch.length, fixed);
		}
		case SegmentEvent.Replaced(final ReplacedRecipe recipe) -> {
			final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
			this.internStructureToken(box.getParams());
			if (this.anchorMode == AnchorMode.SOURCE) {
				box.setSourceAnchor(this.eventId);
			}
			this.doc.addReplacedBox(box);
		}
		case SegmentEvent.Barrier barrier -> throw new IllegalStateException("barrier event in replay range: " + barrier);
		// leader() L1: reshape and reallocate on each execution (do not share mutable state
		// across replays; addLeader creates a new LeaderQuad).
		case SegmentEvent.Leader(final String pattern) -> this.doc.addLeader(pattern);
		}
	}

}
