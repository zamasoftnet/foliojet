package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.FloatMeasurement;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * Builds the entire document.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: RootBuilder.java 1555 2018-04-26 04:15:29Z miyabe $
 */
public class RootBuilder extends BreakableBuilder {
	private static final Logger LOG = Logger.getLogger(RootBuilder.class.getName());

	/**
	 * State for detecting automatic page breaks without progress (livelock; added 2026-07-27).
	 * Stores the state fingerprints seen within a single resume chain and the occurrence count of each.
	 * Comparing only the immediately preceding state cannot detect livelocks with periods of two or more,
	 * such as A/B/A/B. For details, see
	 * {@link net.zamasoft.foliojet.layout.fragment.ContinuationStats#STALLED_AUTO_BREAK_LIMIT}.
	 */
	private record BreakFingerprint(long ingest, long boundTableRows, long emittedTableFragments,
			int depth, long pageAxisBits, int target) {
	}

	/** FIFO-prefix placement plan for top page floats. */
	static final class TopFloatPlan {
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes;
		final double dy;

		TopFloatPlan(final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes,
				final double dy) {
			this.boxes = java.util.List.copyOf(boxes);
			this.dy = dy;
		}
	}

	private final java.util.Map<BreakFingerprint, Integer> breakFingerprintCounts = new java.util.HashMap<>();
	/**
	 * Counts a second fingerprint that excludes depth (2026-08-23). In a livelock that repeatedly reenters the same
	 * page break during resumption (wild seed 1490848), each iteration adds an unfinished ResumeSession and an
	 * open-tail flow, changing the physical depth. The first fingerprint, which includes depth, therefore sees every
	 * iteration as a different state and never fires.
	 * Conversely, normalizing depth uniformly breaks detection of a period-two livelock with stable depth (seed
	 * 44749). Count both fingerprints and stop when either reaches its threshold. Nesting 33 page breaks with no input
	 * progress and the same cursor and target element is not progress, even if their depths differ.
	 */
	private final java.util.Map<BreakFingerprint, Integer> depthFreeBreakCounts = new java.util.HashMap<>();
	/**
	 * Detects page breaks that keep growing through nesting (2026-10-07, sweep strict seed 12453214). Within
	 * resumption of the previous page break (one level deeper in the resume nesting), a page break with the same
	 * input, target, and depth occurs with the cursor farther ahead each time. The remainder sent to the next page
	 * does not shrink; each iteration lays out more than that remainder and breaks again (continuation fragments of
	 * floats in vertical multi-column layout were rebuilt at the same size, and moved fragments accumulated).
	 * Because the cursor differs each time, the fingerprints do not identify the same state; nesting reached 53,000
	 * levels and overflowed the stack. In ordinary nested page breaks, the cursor stops near the page limit and does
	 * not keep advancing.
	 */
	private BreakFingerprint nestedBreakKey = null;
	private int nestedBreakSessions = -1;
	private double nestedBreakPageAxis;
	private int nestedGrowthRun = 0;
	private long breakHistoryIngest = Long.MIN_VALUE;
	private long boundTableRows, emittedTableFragments;
	private long breakHistoryTableRows, breakHistoryTableFragments;

	/**
	 * Counts only execution consumption of Pass C emission targets, excluding input collection, MEASURE, and
	 * append notifications.
	 */
	final void noteRetainedTableRowsBound(final int rows) {
		this.boundTableRows += rows;
	}

	/** Actual fragments detached by the parent onto the previous page. Moving an entire table does not advance this. */
	final void noteRetainedTableFragmentEmitted() {
		++this.emittedTableFragments;
	}
	private int stalledBreakRun = 0;
	/** Automatic page-break termination state for scratch builders without a LayoutSource. */
	private boolean autoBreaksAbandoned = false;

	/** Discards automatic page-break stall history after a forced page break or confirmed actual progress. */
	private void clearBreakProgressHistory() {
		this.stalledBreakRun = 0;
		this.breakFingerprintCounts.clear();
		this.depthFreeBreakCounts.clear();
		this.clearNestedGrowth();
		this.breakHistoryIngest = Long.MIN_VALUE;
	}

	private void clearNestedGrowth() {
		this.nestedBreakKey = null;
		this.nestedBreakSessions = -1;
		this.nestedGrowthRun = 0;
	}

	/**
	 * Checks whether a full automatic page-break cycle leaves the state completely unchanged.
	 *
	 * <p>
	 * <b>Forced page breaks are excluded</b>: page breaks whose count the author specifies are valid even without
	 * consuming content (97 consecutive breaks were observed in practice).
	 * </p>
	 *
	 * @param mode the mode of this page break
	 * @return true if a confirmed livelock requires abandoning the page break
	 */
	private boolean guardBreakProgress(final BreakMode mode) {
		if (!(mode instanceof BreakMode.AutoBreakMode auto)) {
			// Do not measure forced page breaks by progress. Do not carry fingerprints
			// over to the next resume chain either.
			this.clearBreakProgressHistory();
			return false;
		}
		final net.zamasoft.foliojet.layout.fragment.LayoutSource source = this.pageGenerator.getLayoutSource();
		// Input advances while C consumes the queue, too. Fingerprinting the log tail appended by B
		// would misidentify bulk delivery at EOF as a stall.
		final long ingest = (source == null) ? -1L : Math.min(source.nextId(), this.pageGenerator.getDeliveredEventEnd());
		final int depth = this.flowStack.size();
		// Both box and params.element become new instances on every continuation rebuild.
		// identityHashCode treated the logically identical tbody as different on each page,
		// so it failed to detect empty page breaks with the same input position, depth, and cursor
		// (wild seed 7662 produced 1,475 pages). Use an invariant element description as the fingerprint.
		final Object element = auto.box == null || auto.box.getParams() == null ? null
				: auto.box.getParams().element;
		final int target;
		if (element instanceof net.zamasoft.foliojet.css.StructureElement structure) {
			// Use document-order elementKey for real elements and element names for anonymous/pseudo-elements.
			// CSSElement.toString() is unsuitable because it starts with Object.toString().
			target = 31 * Long.hashCode(structure.elementKey())
					+ java.util.Objects.hashCode(structure.lName());
		} else {
			target = element == null ? 0 : element.getClass().getName().hashCode();
		}
		// Continuation rebuilding can alternately cut tbody and its ancestors to reach a new input
		// position, then start the same cycle again. Once any livelock is confirmed in a document,
		// stop all remaining automatic page breaks and let content overflow
		// the current page. Forced page breaks are already excluded at the start of this method.
		if (source != null ? source.areAutoBreaksAbandoned() : this.autoBreaksAbandoned) {
			return true;
		}
		// Discard earlier iterations when input events or Pass C execution consumption advance.
		// Without table emission, both counters stay at 0, giving the same decision as the original fingerprint.
		// Keep the history even when sessions temporarily becomes empty. The period-one loop of seed 7662
		// finishes each page's resumption before proceeding to the next identical page break;
		// resetting there would miss a livelock that the previous detection caught.
		if (ingest != this.breakHistoryIngest || this.boundTableRows != this.breakHistoryTableRows
				|| this.emittedTableFragments != this.breakHistoryTableFragments) {
			this.breakFingerprintCounts.clear();
			this.depthFreeBreakCounts.clear();
			this.clearNestedGrowth();
			this.breakHistoryIngest = ingest;
			this.breakHistoryTableRows = this.boundTableRows;
			this.breakHistoryTableFragments = this.emittedTableFragments;
		}
		final BreakFingerprint fingerprint = new BreakFingerprint(ingest, this.boundTableRows,
				this.emittedTableFragments, depth,
				Double.doubleToLongBits(this.pageAxis), target);
		final int occurrences = this.breakFingerprintCounts.merge(fingerprint, 1, Integer::sum);
		// Depth-independent second fingerprint (depth fixed at -1; see the field comment).
		final BreakFingerprint depthFree = new BreakFingerprint(ingest, this.boundTableRows,
				this.emittedTableFragments, -1,
				Double.doubleToLongBits(this.pageAxis), target);
		final int depthFreeOccurrences = this.depthFreeBreakCounts.merge(depthFree, 1, Integer::sum);
		// Third detection: the same page break occurs farther along the cursor while resuming the previous break
		// (see the nestedBreakKey comment). Compare fingerprints with the cursor excluded.
		final BreakFingerprint nestedKey = new BreakFingerprint(ingest, this.boundTableRows,
				this.emittedTableFragments, depth, 0L, target);
		final int sessionDepth = this.sessions.size();
		if (nestedKey.equals(this.nestedBreakKey) && sessionDepth == this.nestedBreakSessions + 1
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(this.pageAxis, this.nestedBreakPageAxis) > 0) {
			++this.nestedGrowthRun;
		} else {
			this.nestedGrowthRun = 0;
		}
		this.nestedBreakKey = nestedKey;
		this.nestedBreakSessions = sessionDepth;
		this.nestedBreakPageAxis = this.pageAxis;
		this.stalledBreakRun = Math.max(Math.max(occurrences, depthFreeOccurrences) - 1, this.nestedGrowthRun);
		if (DebugFlags.BREAK_FINGERPRINT) {
			System.out.println("[fp] ingest=" + ingest + " depth=" + depth + " pageAxis=" + this.pageAxis
					+ " boundTableRows=" + this.boundTableRows + " emittedTableFragments=" + this.emittedTableFragments
					+ " target=" + target + " resumeDepth=" + this.sessions.size() + " stalled="
					+ this.stalledBreakRun);
		}
		if (net.zamasoft.foliojet.layout.fragment.ContinuationStats.guardBreakProgress(this.stalledBreakRun)) {
			// Repeating the same split here would only duplicate the same fragment
			// onto the next page. On false, the caller exits the loop and
			// places the content with overflow in the current fragment (seed 7662).
			// All iteration sites, including breakByClear, exit on false.
			this.stalledBreakRun = 0;
			this.breakFingerprintCounts.clear();
			this.depthFreeBreakCounts.clear();
			this.clearNestedGrowth();
			this.breakHistoryIngest = Long.MIN_VALUE;
			if (source != null) {
				source.abandonAutoBreaks();
			} else {
				this.autoBreaksAbandoned = true;
			}
			return true;
		}
		return false;
	}

	/**
	 * Stack of scopes for rebuilding the remainder after a break (page or column; M6b).
	 * Each entry holds replay ranges of closed subtrees recorded together at the break = C2.
	 * Breaks always occur at the construction head, so the resume context is "head = ancestor chain".
	 * If replayed content overflows the new page, page breaks nest within resumption. A single field would let an
	 * inner break destroy the outer resume context (external review finding); the top is the current context.
	 */
	private final java.util.ArrayDeque<java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> resumeScopes = new java.util.ArrayDeque<>();

	/**
	 * Starts a scope for rebuilding the remainder after a break, with the recorded replay ranges (C2).
	 */
	public final void beginBreakRestyle(
			final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges) {
		this.resumeScopes.push(ranges);
	}

	/**
	 * Ends the scope for rebuilding the remainder after a break (M6b).
	 */
	public final void endBreakRestyle() {
		if (this.resumeScopes.isEmpty()) {
			throw new IllegalStateException("再開スコープの対応が壊れています");
		}
		this.resumeScopes.pop();
	}

	/**
	 * A session for consuming a continuation exactly once (P1; external review design).
	 *
	 * <p>
	 * Owns the resume scope and leases for absorbed replay ranges, and cleans them up symmetrically, including on
	 * exceptions. Lease ownership is per occurrence (SourceRange instance), so nested continuations independently
	 * holding the same fromId do not interfere. Enforces NEW → RESUMING → CONSUMED / FAILED → CLOSED transitions,
	 * making consume-once explicit through types and runtime checks. A future extension for M6c iterative probes will
	 * create fresh sessions from a ContinuationTemplate (reusing a session is forbidden).
	 * </p>
	 */
	final class ResumeSession implements AutoCloseable, net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession {
		enum State {
			NEW, RESUMING, CONSUMED, FAILED, CLOSED
		}

		private final net.zamasoft.foliojet.layout.fragment.Continuation continuation;

		/**
		 * Snapshot at the break (used for direct comparison of actual fragment signatures in E-3 increment 2 and for
		 * deriving tail policy from the validated open path shape in E-3 increment 3).
		 */
		private final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot;

		/**
		 * Leases for absorbed replay ranges, per occurrence. Absorbed ranges carry no boxes (no fallback), so protect
		 * them from compaction until consumed (LayoutSource clamps the watermark). Replay through the map (resumeScopes)
		 * retains boxes and can fall back to box-restyle, so it needs no lease.
		 */
		private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> leases = new java.util.IdentityHashMap<>();

		private State state = State.NEW;

		ResumeSession(final net.zamasoft.foliojet.layout.fragment.Continuation continuation,
				final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
			this.continuation = continuation;
			this.snapshot = snapshot;
			// 2026-07-30 (legacy recursion removal = increment 4d): retired the tail policy
			// (WorklistTailGate). The worklist executor became the sole driver,
			// so the routing decision itself disappeared.
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log = RootBuilder.this.pageGenerator
					.getLayoutSource();
			if (log != null) {
				for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = continuation
						.root(); f != null;) {
					for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : f.prefixItems()) {
						this.leases.put(r, log.retainFrom(r.fromId()));
					}
					f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child
									: null;
				}
			}
		}

		/**
		 * Consumes the continuation and resumes builder state and content on the next page (§5.7).
		 * Reconstructs root frames from outside in (fragment boxes are first created here). May be called only once.
		 */
		void resume() {
			if (this.state != State.NEW) {
				throw new IllegalStateException("継続は一度だけ消費できる: " + this.state);
			}
			this.state = State.RESUMING;
			RootBuilder.this.sessions.push(this);
			RootBuilder.this.enterTranslateBlockScope();
			try {
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("PAGE");
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.beginContinuationPath(false);
				RootBuilder.this.beginBreakRestyle(this.continuation.ranges());
				try {
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(0, "root-fragment",
							"depth=" + this.continuation.depth());
					RootBuilder.this.resumeFrame(this.continuation.root(), 0, this.continuation.depth(), this.snapshot);
					this.state = State.CONSUMED;
				} catch (RuntimeException | Error e) {
					this.state = State.FAILED;
					throw e;
				} finally {
					RootBuilder.this.endBreakRestyle();
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.endContinuationPath();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
					RootBuilder.this.sessions.pop();
				}
			} finally {
				RootBuilder.this.exitTranslateBlockScope();
			}
		}

		/**
		 * Marks consumption of an absorbed range as complete (from replaySubtree's finally block).
		 */
		public void releaseLease(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange occurrence) {
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.leases
					.remove(occurrence);
			if (lease != null) {
				lease.close();
			}
		}

		public boolean hasUnconsumedLeases() {
			return !this.leases.isEmpty();
		}

		@Override
		public void close() {
			if (this.state == State.CLOSED) {
				return;
			}
			// Normal consumption has already released all leases. Clean up any remaining after an exception here
			// (leaving them behind would clamp all subsequent compaction permanently).
			for (final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease : this.leases
					.values()) {
				lease.close();
			}
			this.leases.clear();
			this.state = State.CLOSED;
		}
	}

	/**
	 * Builds and validates the canonical COLUMN continuation token ({@link
	 * net.zamasoft.foliojet.layout.fragment.ColumnContinuation}) from the {@link
	 * net.zamasoft.foliojet.layout.fragment.PreparedColumnCut} returned by {@code
	 * AbstractContainerBox.prepareColumnCut()} (added 2026-07-21, M6b Phase B4-Step4; E-3 increment 5 on 2026-07-24
	 * removed program (ColumnResumeProgram) generation and replaced it with canonical token construction).
	 * Does not yet commit to the owner or execute a session, allowing the caller to preserve the order "validate →
	 * column commit → start executor" (see the ChatGPT Pro consultation and design consultation).
	 * Duplicates PAGE's {@code pageBreak()} prefix absorption logic (stampRanges+extractReplayable) for COLUMN. The
	 * implementations are deliberately parallel rather than shared to leave the existing PAGE path entirely untouched.
	 * {@code ranges} travels in {@code ColumnContinuation} as a mutable map for consume-once ({@code
	 * replayFromSource()} directly calls remove() during consumption, so it must not be read-only; this contract was
	 * discovered and corrected through observation).
	 */
	final net.zamasoft.foliojet.layout.fragment.ColumnContinuation prepareColumnContinuation(
			final net.zamasoft.foliojet.layout.box.params.WritingMode ownerFlow,
			final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared,
			final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
		final net.zamasoft.foliojet.layout.box.content.Container ownerRemainder = prepared.ownerRemainder();

		final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame> innerFrames = new java.util.ArrayList<>();
		for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = prepared.childFrame(); f != null;) {
			innerFrames.add(f);
			f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;
		}

		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = this
				.stampRanges(ownerRemainder, ownerFlow);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
			ranges.putAll(this.stampRanges(f.container(), ownerFlow));
		}

		final boolean vertical = ownerFlow.isVertical();
		java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> anchorPrefix = java.util.List
				.of();
		final java.util.List<java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> framePrefixes = new java.util.ArrayList<>(
				innerFrames.size());
		if (!innerFrames.isEmpty()) {
			if (ownerRemainder instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
				anchorPrefix = fc.extractReplayable(ranges, vertical, 0);
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
				// Walk depth is Child=0 (this frame continues inward),
				// OpenTailShape=actual remaining depth (using 0 could absorb the trailing moved flow
				// into the prefix as a closed subtree, leading to duplicate replay
				// or lost content).
				final int walkDepth = switch (f.tail()) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child -> 0;
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				};
				framePrefixes
						.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
								? fc.extractReplayable(ranges, vertical, walkDepth)
								: java.util.List.of());
			}
		}

		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = null;
		for (int i = innerFrames.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = innerFrames.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.recipe(), f.state(),
							f.container(), f.crossExtent(), framePrefixes.get(i), tail == null ? f.tail() : tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame childFrame = tail instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;

		final net.zamasoft.foliojet.layout.fragment.ColumnAnchor anchor = new net.zamasoft.foliojet.layout.fragment.ColumnAnchor(
				ownerRemainder, anchorPrefix);
		// 2026-07-24 (E-3 increments 1/5): validate the canonical COLUMN input directly
		// (the old compiler/verifier invariants were ported to ContinuationValidator).
		// This precedes commitPreparedColumn in the caller (BreakableBuilder.columnBreak),
		// so validation failure stops safely without committing to the owner.
		final net.zamasoft.foliojet.layout.fragment.ContinuationValidator.PathShape pathShape = net.zamasoft.foliojet.layout.fragment.ContinuationValidator
				.validateColumn(anchor, snapshot, childFrame);
		return new net.zamasoft.foliojet.layout.fragment.ColumnContinuation(snapshot, anchor, childFrame, ranges,
				pathShape);
	}

	/**
	 * Consumes a validated {@link net.zamasoft.foliojet.layout.fragment.ColumnContinuation} and resumes builder state
	 * and content in the new column (added 2026-07-21, M6b Phase B4-Step4). After {@link #prepareColumnContinuation},
	 * the caller must already have executed {@code owner.commitPreparedColumn()}.
	 *
	 * @param target builder to receive state mutations (the actual BreakableBuilder driving the column break,
	 *               possibly a nested {@code ColumnBuilder})
	 */
	final void resumeColumn(final BreakableBuilder target,
			final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation) {
		try (ColumnResumeSession session = new ColumnResumeSession(target, continuation)) {
			session.resume();
			assert !session.hasUnconsumedLeases() : "未消費の吸収済み再生範囲が残っています";
		}
	}

	/**
	 * A session for consuming a COLUMN continuation exactly once (added 2026-07-21, M6b Phase B4-Step4).
	 * The COLUMN counterpart of {@link ResumeSession}, with the same design (state transitions, lease ownership, and
	 * symmetric exception cleanup).
	 */
	final class ColumnResumeSession implements AutoCloseable, net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession {
		enum State {
			NEW, RESUMING, CONSUMED, FAILED, CLOSED
		}

		private final BreakableBuilder target;
		/** Canonical COLUMN continuation token (replaced the program in E-3 increment 5). */
		private final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation;
		private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> leases = new java.util.IdentityHashMap<>();
		private State state = State.NEW;

		ColumnResumeSession(final BreakableBuilder target,
				final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation) {
			this.target = target;
			this.continuation = continuation;
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log = RootBuilder.this.pageGenerator
					.getLayoutSource();
			if (log != null) {
				for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : continuation.anchor()
						.prefixItems()) {
					this.leases.put(r, log.retainFrom(r.fromId()));
				}
				for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = continuation
						.childFrame(); f != null;) {
					for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : f.prefixItems()) {
						this.leases.put(r, log.retainFrom(r.fromId()));
					}
					f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child
									: null;
				}
			}
		}

		void resume() {
			if (this.state != State.NEW) {
				throw new IllegalStateException("継続は一度だけ消費できる: " + this.state);
			}
			this.state = State.RESUMING;
			RootBuilder.this.sessions.push(this);
			RootBuilder.this.enterTranslateBlockScope();
			try {
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("COLUMN");
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.beginContinuationPath(true);
				this.target.beginRestyling();
				RootBuilder.this.beginBreakRestyle(this.continuation.ranges());
				try {
					if (this.continuation.childFrame() != null) {
						RootBuilder.this.restyleFrame(this.target, this.continuation.anchor().remainder(),
								this.continuation.anchor().prefixItems(),
								net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
						RootBuilder.this.resumeFragmentChain(this.continuation.childFrame(), 1,
								this.continuation.snapshot().depth(), this.continuation.snapshot(), this.target);
					} else {
						assert this.continuation.anchor().prefixItems().isEmpty();
						// E-3 increment 5: pathShape.terminalShape() is the canonical terminal open shape
						// (equivalent to the old program.tail().openDepth():
						// when childFrame==null, validateColumn returns
						// OpenShape.of(snapshot.depth()), matching the old compiler's
						// OpenText(1)/LegacyOpen(1, snapshotDepth)).
						// 2026-07-30 (increment 4d): retired worklist eligibility checks and overrides.
						// restyle() itself is now unconditionally driven by the worklist executor.
						this.continuation.anchor().remainder().restyle(this.target,
								this.continuation.pathShape().terminalShape(), false);
					}
					this.state = State.CONSUMED;
				} catch (RuntimeException | Error e) {
					this.state = State.FAILED;
					throw e;
				} finally {
					RootBuilder.this.endBreakRestyle();
					this.target.endRestyling();
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.endContinuationPath();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
					RootBuilder.this.sessions.pop();
				}
			} finally {
				RootBuilder.this.exitTranslateBlockScope();
			}
		}

		public void releaseLease(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange occurrence) {
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.leases
					.remove(occurrence);
			if (lease != null) {
				lease.close();
			}
		}

		public boolean hasUnconsumedLeases() {
			return !this.leases.isEmpty();
		}

		@Override
		public void close() {
			if (this.state == State.CLOSED) {
				return;
			}
			for (final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease : this.leases
					.values()) {
				lease.close();
			}
			this.leases.clear();
			this.state = State.CLOSED;
		}
	}

	/**
	 * Stack of active resume sessions (nested by page/column breaks caused by replay overflow; the top is the current
	 * session).
	 * 2026-07-21 (M6b Phase B4-Step4): generalized the PAGE-only {@code ResumeSession} to {@link
	 * net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession}. Managing COLUMN's {@link ColumnResumeSession} on the
	 * same stack lets {@link #replaySubtree} inspect only the current top session, even when a PAGE break nests within
	 * COLUMN resumption or vice versa (see the ChatGPT Pro consultation and design consultation).
	 */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession> sessions = new java.util.ArrayDeque<>();

	/**
	 * Determines replay eligibility and ranges for all closed subtrees in the remainder at the break (C2: decision at
	 * recording time). The restyle traversal only consumes these records; it does not recompute the gates. The
	 * decisions are identical to those replayFromSource previously made during resumption (valid anchor, closed within
	 * the window, no Opaque/multi-column/mixed writing directions).
	 *
	 * @param container the remainder container
	 * @param rootFlow  the root writing direction
	 * @return box → replay range (only for replayable boxes)
	 */
	final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> stampRanges(
			final net.zamasoft.foliojet.layout.box.content.Container container,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = new java.util.IdentityHashMap<>();
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return ranges;
		}
		this.stampRanges(container, rootFlow, log, ranges);
		return ranges;
	}

	private void stampRanges(final net.zamasoft.foliojet.layout.box.content.Container container,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
			final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges) {
		container.eachFlowBox(box -> {
			// isSourceReplayable (2026-07-28): a preceding fragment already cut off retains its anchor,
			// but that range also includes the remainder held by the continuation fragment.
			// Stamping it replays the entire element on resumption, duplicating the continuation
			// fragment's resumption (observed in nested column balancing). Fall back to box replay.
			final long startId = box.isSourceReplayable() ? box.getSourceAnchor() : -1;
			if (startId >= 0) {
				final long endId = log.endOf(startId);
				// containsAbsolute (E-6 increment 4e): before increment 4e, absolute positioning used
				// Opaque records and containsOpaque caught it. Even after switching to recipe recording,
				// substituting source replay for a subtree containing absolute positioning duplicates
				// anchoring and deferred bind, so retain the box-restyle fallback
				// (see the Javadoc for LayoutSource.containsAbsolute).
				// isIntact (2026-07-27): compact retains only open Start events from before the watermark,
				// so an element still open at the break may retain only its Start
				// while its contents disappear. When the element later closes,
				// endOf() returns its end over the sparse retained sequence,
				// incorrectly stamping a range with gaps as replayable. Absorbed ranges
				// (prefixItems) carry no boxes, so fallback is impossible.
				// Check density when stamping; otherwise
				// replaySubtree stops the entire conversion with "Absorbed replay range was lost"
				// (observed in 15 of 100,000 sweep cases).
				// containsFloat (2026-07-28): the assumption that floats in a subtree
				// are anchored to the nearest block ancestor's container and thus move
				// with the subtree breaks in multi-column layout. Aggregation
				// ({@code aggregateFloatings}) **lifts** floats into the column container,
				// so even if the entire subtree moves, <b>the floats stay in the
				// original column</b>. Replaying the subtree from source lays them out
				// <b>twice</b>, including the lifted copies (observed in
				// local/shrink/strict-29708-min.html and others:
				// "T3 T4" inside the float is drawn twice on the same page).
				// {@code SourceReplayer.canReplayChildren} has had this gate from the start,
				// avoiding the risk of repeated (duplicate) anchoring for the same reason.
				// Only this site lacked it. Fall back to box replay.
				// containsTable (table set, 2026-07-30): recipe recording made tables
				// non-Opaque. Allow stamping <b>only ranges rooted at TABLE itself</b>
				// (T-b; consumed by direct replay in restyleItem case TABLE = T-c).
				// Table rebuilding by replaySubtree of a BLOCK subtree containing a table
				// is unverified, so retain box-restyle (consider enabling in codex increment 11).
				// For a table root, its own Start (startId) naturally belongs to the range,
				// so inspect only the contents (startId+1 onward),
				// and continue rejecting nested tables within cells on a fail-closed basis.
				final long tableCheckFrom = box instanceof net.zamasoft.foliojet.layout.box.impl.TableBox
						? startId + 1
						: startId;
				// containsCaption (caption recipe conversion C1, 2026-08-01): captions moved
				// from Opaque to recipe records, but their kind depends on context
				// (requires an enclosing TableBuilder), so containing ranges still use box-restyle.
				// Even ranges rooted at a table (tableCheckFrom=startId+1) reject
				// captions in their contents. Routing stays unchanged until C2's
				// context-complete validation enables them.
				if (endId >= 0 && endId < this.pageGenerator.getDeliveredEventEnd()
						&& log.isIntact(startId, endId) && !log.containsOpaque(startId, endId)
						&& !log.observeCaptionGate(startId, endId)
						&& !log.containsTable(tableCheckFrom, endId)
						&& !log.containsAbsolute(startId, endId)
						&& !log.containsFloat(startId, endId)
						&& !log.containsMulticol(startId, endId)
						&& !log.containsMixedFlow(startId, endId, rootFlow)) {
					ranges.put(box,
							new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(-1, startId, endId));
					// Do not traverse inside a replayed subtree (replay it as a whole).
					return;
				}
			}
			if (box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox) {
				this.stampRanges(containerBox.getContainer(), rootFlow, log, ranges);
			}
		});
	}

	private final PageGenerator pageGenerator;

	private PageBox pageBox;

	/** Depth of child builder, replay, and continuation processing scopes open outside Root. */
	private int translateBlockDepth = 0;

	/** True after the current PageBox enters finalization before drawing. */
	private boolean pageFinished = false;

	/**
	 * Enters a child scope that prohibits translation of the current page. Shared by nested DocumentBuilder, TwoPass
	 * bind, source replay, and continuation resumption.
	 */
	public final void enterTranslateBlockScope() {
		++this.translateBlockDepth;
	}

	/** Exits a child scope that prohibits translation of the current page. */
	public final void exitTranslateBlockScope() {
		assert this.translateBlockDepth > 0 : "translate block scope depth became negative";
		--this.translateBlockDepth;
	}

	/** Top-float exclusion space for this page, recreated for each new PageBox. */
	private java.util.List<FloatExclusion> topPageFloatExclusions;

	/** Page-axis end of the top prefix placed on this page in FIFO order. */
	private double topPageFloatStackEnd = 0;

	/** Immutable snapshot containing only top floats. */
	private ExclusionSpace topPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** Immutable snapshot containing only bottom floats, including future pageSpan values. */
	private ExclusionSpace bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** Immutable snapshot combining top/bottom page floats for line scanning. */
	private ExclusionSpace pageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** Generation per PageBox creation; also gives page floats a separate order namespace from ordinary floats. */
	private long pageGeneration = 0;
	/** Column-break commit history for this page; retained until page end even after the multi-column owner closes. */
	private int committedColumnsOnPage;
	/**
	 * Maximum position reached by body text in columns that broke on this page (page block axis). Used to check
	 * whether the placement area for a bottom float registered in a later column overlaps lines already laid out in an
	 * earlier column (2026-10-05, jigensha report 4).
	 */
	private double committedColumnsEndOnPage;
	private final boolean debugFootnote = Boolean.getBoolean("net.zamasoft.foliojet.debug.footnote");

	/** Called only after a successful BreakableBuilder column-break commit, not during local balancing replay. */
	final void columnCommitted(final BreakableBuilder builder, final Flow flow,
			final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared) {
		++this.committedColumnsOnPage;
		// Column page coordinates match this builder's cursor, so the position before the break is the column end.
		// For another builder's column break, coordinates cannot be verified; assume layout reached the type area edge.
		this.committedColumnsEndOnPage = Math.max(this.committedColumnsEndOnPage,
				builder == this ? this.currentPagePosition() : super.getPageLimit());
		this.traceFootnote("column-commit", null, 0, java.util.Set.of());
		final FootnoteHost previous = this.columnFootnoteHost;
		if (previous == null || previous.owner != prepared.owner()
				|| previous.container.get() != prepared.expectedActiveColumn()) return;
		if (!previous.pendingFootnotes.isEmpty()) {
			this.attachColumnFootnotes(previous, prepared.newPageExtent());
		}
		this.columnFootnoteHost = null;
		this.openFootnoteColumn(builder, flow);
		if (this.columnFootnoteHost != null) {
			this.columnFootnoteHost.pendingFootnotes.addAll(previous.pendingFootnotes);
			previous.pendingFootnotes.clear();
			this.reserveColumnFootnotes(this.columnFootnoteHost);
		} else {
			this.transferColumnFootnotes(previous);
		}
	}

	public long getPageGeneration() {
		return this.pageGeneration;
	}

	/** Stable sequence number for page floats within the same page. */
	private int pageFloatSequence = 0;

	/** Box registered in pending and the page generation when it was registered. */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> pendingTopFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** A top float registered on this page that is still eligible for placement at its top. */
	private record CurrentTopFloat(net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box, long generation) {
	}

	/** Generation-tagged ledger of top floats for this page, preserving box registration order. */
	private final java.util.List<CurrentTopFloat> pendingCurrentTopFloats = new java.util.ArrayList<>();

	/**
	 * Boxes already placed on this page and their generations; prevents addPageFloat replay duplicates and
	 * re-placement.
	 */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> placedTopFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** Pending/placed generations of bottom floats; prevents duplicate registration during TwoPass replay. */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> pendingBottomFloatGenerations =
			new java.util.IdentityHashMap<>();
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> placedBottomFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** Stable order of bottom floats within this page, unchanged even when footnotes move. */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> bottomFloatOrders =
			new java.util.IdentityHashMap<>();

	/**
	 * Maximum root page-axis end occupied by unsplittable floats placed in the current PageBox (2026-09-04).
	 *
	 * <p>
	 * The first version covers only normal flows placed by RootBuilder itself, with the same WritingMode in every flow
	 * up to the root. Excludes child context builders with local coordinates (nested BFC, relative/absolute, TwoPass),
	 * multi-column layout, and orthogonal flows. Converting offsets from child contexts to parents and propagating
	 * them is left for a later version.
	 * </p>
	 */
	private double atomicFloatFloor = 0;

	public RootBuilder(PageGenerator pageGenerator, byte mode) {
		super(null, null, mode);
		this.pageGenerator = pageGenerator;
		this.pageBox = this.nextPage();
		this.beginPage();

		this.pageSide = this.pageGenerator.getPageSide();
		this.contextFlow = new Flow(this.pageBox, 0, 0);
	}

	/** Creates a PageBox and its page-float exclusion space together. */
	private PageBox nextPage() {
		if (this.pageGeneration == 0x7fff_ffffL) {
			throw new IllegalStateException("page float generation exhausted");
		}
		final PageBox next = this.pageGenerator.nextPage();
		++this.pageGeneration;
		this.committedColumnsOnPage = 0;
		this.committedColumnsEndOnPage = 0;
		this.pendingCurrentTopFloats.removeIf(entry -> entry.generation() != this.pageGeneration);
		this.pageFinished = false;
		this.pageFloatSequence = 0;
		this.topPageFloatExclusions = new java.util.ArrayList<>();
		this.topPageFloatStackEnd = 0;
		this.topPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.pageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.placedTopFloatGenerations.clear();
		this.placedBottomFloatGenerations.clear();
		this.bottomFloatOrders.clear();
		this.bottomFloatOneDimensionalFallback = false;
		this.bottomFloatsDeferredOnPage = false;
		this.atomicFloatFloor = 0;
		this.narrowTopPlacedWithTextBeside = false;
		return next;
	}

	/** True if all open flows up to the root have exactly the same WritingMode. */
	private boolean hasRootWritingModePath() {
		final WritingMode rootFlow = this.pageBox.getBlockParams().flow;
		for (int i = 0; i < this.getFlowCount(); ++i) {
			if (this.getFlow(i).box.getBlockParams().flow != rootFlow) {
				return false;
			}
		}
		return true;
	}

	private void refreshPageFloatExclusionSnapshot() {
		this.pageFloatExclusionSnapshot = this.topPageFloatExclusionSnapshot
				.mergedWith(this.bottomPageFloatExclusionSnapshot);
	}

	@Override
	protected ExclusionSpace pageFloatExclusionsForLineLayout() {
		if (!this.hasRootWritingModePath()) {
			// Orthogonal flows share the same flowStack even inside RootBuilder. If any ancestor
			// transforms axes, leave placement to the outer frame instead of passing page coordinates inside.
			return ExclusionSpace.EMPTY;
		}
		return this.pageFloatExclusionSnapshot;
	}

	/**
	 * Reports the occupied end of an unsplittable float committed by {@link
	 * BlockBuilder#commitFloatPlacement(FloatPlacementDelta)} (2026-09-04). Restricting callers to commit and updating
	 * by max makes TwoPass renotification idempotent.
	 */
	final void reportAtomicFloatPlacement(final net.zamasoft.foliojet.layout.box.IFloatBox box,
			final WritingMode ownerFlow, final double pageStart) {
		if (this.columnFootnoteHost != null && ownerFlow == this.columnFootnoteHost.owner.getBlockParams().flow
				&& this.isEligibleFootnoteColumnOwner(this, this.columnFootnoteHost.owner)) {
			final FootnoteHost host = this.columnFootnoteHost;
			host.atomicFloatFloor = Math.max(host.atomicFloatFloor,
					pageStart - host.pageOrigin + FloatMeasurement.occupiedPageExtent(box, ownerFlow));
			return;
		}
		if (this.getMulticolumnBox() != null || ownerFlow != this.pageBox.getBlockParams().flow
				|| !this.hasRootWritingModePath()) {
			return;
		}
		final double floor = pageStart + FloatMeasurement.occupiedPageExtent(box, ownerFlow);
		if (net.zamasoft.foliojet.layout.util.LayoutUtils.compare(floor, this.atomicFloatFloor) <= 0) {
			return;
		}
		this.atomicFloatFloor = floor;
		// Existing bottom reservations are committed to this page. The floor blocks only additional reservations.
		this.reserveBottomFloats();
		this.updateBottomFloatFallbackForCurrentPosition();
	}

	/** Returns page generation + stable sequence with the sign bit set, avoiding ordinary floats' nonnegative order. */
	private long nextPageFloatOrder() {
		if (this.pageFloatSequence == Integer.MAX_VALUE) {
			throw new IllegalStateException("too many page floats on one page");
		}
		return Long.MIN_VALUE | (this.pageGeneration << 32) | Integer.toUnsignedLong(this.pageFloatSequence++);
	}

	public final boolean isMain() {
		return true;
	}

	@Override
	protected final boolean supportsNamedPages() {
		return true;
	}

	@Override
	protected final String currentPageName() {
		return this.pageGenerator.getPageName();
	}

	@Override
	protected final void setNextPageName(final String pageName) {
		this.pageGenerator.setPageName(pageName);
		final var observer = pageNameObserver;
		if (observer != null) observer.accept(this, pageName);
	}

	/** Test hook to observe name-transition arbitration counts separately for C/B. Normally null. */
	static volatile java.util.function.BiConsumer<RootBuilder, String> pageNameObserver;

	public final RootBuilder getPageContext() {
		return this;
	}

	/** Page currently being laid out; used before drawing, such as for local glyph-outline measurements. */
	public final PageBox getCurrentPageBox() {
		return this.pageBox;
	}

	/**
	 * Returns the page generator (M6c: source replay for balancing).
	 */
	public final PageGenerator getPageGenerator() {
		return this.pageGenerator;
	}

	/**
	 * Executes a page break.
	 *
	 * @param mode
	 * @param flags
	 */
	protected boolean pageBreak(BreakMode mode, byte flags) {
		this.beginBreak();
		if (this.flowStack.isEmpty()) {
			return false;
		}
		if (mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode auto
				&& !(mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode)) {
			final double slack = this.uncommittedFootnoteReservation();
			if (slack > 0) {
				mode = auto.withFootnoteSlack(slack);
			}
		}
		if (this.guardBreakProgress(mode)) {
			return false;
		}

		// Calculate the box height.
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		}

		// C1b/C1d-C preflight: create a read-only plan containing only the collectable prefix
		// of consecutive plain FlowBlockBox entries (no multi-column/table/mixed writing directions)
		// from the start of the ancestor chain (flowStack[1..]). Convert fragments at levels
		// traversed by the cut into continuations without constructing boxes. Stop scanning
		// at the first ineligible level (2026-07-20). Previously, one ineligible level sent
		// the entire chain to the old path on an all-or-nothing basis, so a single multicol
		// or similar outside many plain wrappers sent the entire ancestor chain to
		// OpenChain recursion that had not been made iterative; depth 74 was observed.
		// Always pass flowStack.size() unchanged as BreakPlan.depth,
		// not the prefix length. Keep BreakPlan.openTailDepth()
		// = depth - index - 1 available from this depth without traversal,
		// so only the remainder outside the prefix (the ineligible level and its interior)
		// becomes the actual OpenChain depth. Shortening depth itself would make
		// OpenShape nesting disagree with the actual box tree's open structure
		// and could incorrectly process still-open boxes as closed.
		// Never change it (confirmed in external review;
		// see design consultation*.md).
		// Fragments propagate outward in the split return value (SplitResult.Frame → ContainerCut.WithFrame),
		// with no side channel.
		//
		// 2026-07-21 (B2): delegated the scan itself to OpenPathScan.capture()
		// (behavior unchanged; uses B1's ContinuationCapability classification as is).
		// The snapshot is also used later for ContinuationValidator validation
		// (no reclassification; confirmed in the ChatGPT Pro consultation,
		// design consultation).
		//
		// 2026-07-21 (B3a): made MULTICOL collectable only for automatic PAGE breaks
		// (excluding ForceBreakMode). For forced page breaks,
		// FlowContainer.splitPageAxis has a path that unconditionally turns KEEP/MOVE
		// into AssertionError("force break failed"),
		// so safety is not yet confirmed (deferred as B3b; identified and verified
		// in the ChatGPT Pro consultation,
		// design consultation).
		final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot;
		final net.zamasoft.foliojet.layout.fragment.BreakPlan plan;
		{
			final java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> openBoxes = new java.util.ArrayList<>(
					this.flowStack.size());
			for (int i = 0; i < this.flowStack.size(); ++i) {
				openBoxes.add(((Flow) this.flowStack.get(i)).box);
			}
			final net.zamasoft.foliojet.layout.fragment.OpenPathScan scan = net.zamasoft.foliojet.layout.fragment.OpenPathScan
					.capture(openBoxes, mode);
			scan.snapshot().firstBarrier().ifPresent(barrier -> net.zamasoft.foliojet.layout.fragment.ContinuationStats
					.recordCapabilityScanStop(barrier.reason()));
			snapshot = scan.snapshot();
			plan = this.columnFootnoteHost == null || this.columnFootnoteHost.footnoteReservation == 0 ? scan.toBreakPlan()
					: scan.toBreakPlan().withColumnLimit(new net.zamasoft.foliojet.layout.fragment.BreakPlan.ColumnLimit(
							this.columnFootnoteHost.owner, this.columnFootnoteHost.footnoteReservation));
			// Increment 5 (grok review requirement 1): cutting truncates the root's inner size and changes
			// `getPageOwnerLimit()`, so fix the last column's capacity before the cut.
			this.columnFootnoteCutCapacity = this.columnFootnoteHost == null ? Double.NaN
					: this.columnFootnoteHost.capacityBase.getAsDouble();
			this.columnFootnoteCarryChainIndex = -1;
			if (this.columnFootnoteHost != null) {
				for (int i = 0; i < this.flowStack.size(); ++i) {
					if (((Flow) this.flowStack.get(i)).box == this.columnFootnoteHost.owner) {
						this.columnFootnoteCarryChainIndex = i;
						break;
					}
				}
			}
		}

		// Split the root block (C1a: split does not construct fragment boxes.
		// Continuation carries the container cut and fragment state; resume reconstructs them.
		// Construct the root frame after watermark calculation and prefix absorption (C1c)).
		final FlowBlockBox prevRootBox;
		final net.zamasoft.foliojet.layout.box.content.Container nextRootContainer;
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootChildFrame;
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe rootRecipe;
		final net.zamasoft.foliojet.layout.fragment.FragmentState rootState;
		final double rootCrossExtent;
		{
			final Flow root = (Flow) this.flowStack.get(0);

			// Calculate the frame for multi-column layout.
			double lastFrame = 0;
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					lastFrame = this.lastFrame(root, this.flowStack.size() - i);
					mode = net.zamasoft.foliojet.layout.box.content.BreakMode.column(mode);
					break;
				}
			}

			prevRootBox = (FlowBlockBox) root.box;
			final double pageAxis = this.getPageOwnerLimit() - root.pageAxis - lastFrame;
			// Same preprocessing as the old AbstractContainerBox.split (inner-edge basis and column-break absorption).
			final double innerLimit = pageAxis
					- prevRootBox.getFrame().getFramePageStart(prevRootBox.getBlockParams().flow);
			final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
					.absorbColumn(mode, prevRootBox.getColumnCount());
			final net.zamasoft.foliojet.layout.fragment.ContainerCut cut;
			// Capture open boxes during the cut (do not rescue open boxes absent from the plan either; OpenBoxes).
			try (var open = net.zamasoft.foliojet.layout.fragment.OpenBoxes.scope(this.openFlowBoxes())) {
				cut = prevRootBox.getContainer().splitPageAxis(innerLimit, xmode, flags, plan);
			}
			if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(
					final net.zamasoft.foliojet.layout.box.content.Container chainStopContainer,
					final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
				// Same reason as AbstractBlockBox.splitForContinuation
				// (risk of content loss). Return false for "no page-break point"
				// only when the container is empty. When actual content exists,
				// join the common root-frame construction logic below
				// (the dedicated MovedOpen type was removed on 2026-07-22;
				// see the development records
				// -consultation.md). For details, see the development records
				// -chainstop-content-loss-safety-net.md.
				final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						&& (fc.hasFlows() || fc.hasFloatings());
				if (!hasContent) {
					// KEEP/MOVE: no page-break point.
					return false;
				}
				nextRootContainer = chainStopContainer;
				rootChildFrame = null;
			} else if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(
					final net.zamasoft.foliojet.layout.box.content.Container c,
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f)) {
				nextRootContainer = c;
				rootChildFrame = f;
			} else {
				nextRootContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) cut).container();
				rootChildFrame = null;
			}
			if (nextRootContainer == null || nextRootContainer == prevRootBox.getContainer()) {
				// KEEP/MOVE: no page-break point.
				return false;
			}
			final boolean vertical = prevRootBox.getBlockParams().flow.isVertical();
			rootCrossExtent = vertical ? prevRootBox.getInnerHeight() : prevRootBox.getInnerWidth();
			// Obtain the recipe before splitPageState invalidates anchors (C1d-B).
			rootRecipe = prevRootBox.fragmentRecipe();
			rootState = prevRootBox.splitPageState(plan.contentLimit(prevRootBox, innerLimit), innerLimit,
					mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode);
		}

		// C1d-C: frames traversed by the cut (outside in). Each level's container
		// is detached from the root frame's container, so watermark and replay-range
		// decisions must also traverse the frame containers.
		final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame> innerFrames = new java.util.ArrayList<>();
		for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = rootChildFrame; f != null;) {
			innerFrames.add(f);
			f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;
		}

		// 2026-07-21: the terminal OpenTailShape depth is already fixed here
		// (splitForContinuation computed it at the break). 2026-07-30 (increment 4c):
		// unifying on the worklist made OpenChain descent nonrecursive, so the typed
		// exception guard at depth 64 was retired; only maximum-depth observation remains.
		{
			final int terminalOpenDepth;
			if (rootChildFrame == null) {
				terminalOpenDepth = this.flowStack.size();
			} else {
				final net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail lastTail = innerFrames
						.get(innerFrames.size() - 1).tail();
				// The innerFrames traversal contract makes it structurally impossible
				// for lastTail to be Child.
				terminalOpenDepth = switch (lastTail) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child ->
					throw new IllegalStateException("innerFrames walk must terminate on a non-Child tail");
				};
			}
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenDepth(terminalOpenDepth, false);
		}

		// Source-log watermark = minimum EventId of closed items in the remainder (M6b v3).
		// Earlier events have been consumed by finalized pages and can be discarded.
		// Compaction always retains StartBlock events of the open chain.
		// Prefix absorption (C1c) removes items from containers, so measure the watermark
		// before absorption.
		long watermark = this.sourceWatermark(nextRootContainer);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
			watermark = Math.min(watermark, this.sourceWatermark(f.container()));
		}

		final PageBox pageBox = this.turnPage(mode);
		this.beginRestyling();

		// Continuation description (§5.7). Reconstruct the root fragment on resumption (C1a),
		// and determine and record all closed-subtree replay ranges at the break
		// (C2; includes containers of frames traversed by the cut).
		final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow = prevRootBox.getBlockParams().flow;
		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = this
				.stampRanges(nextRootContainer, rootFlow);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
			ranges.putAll(this.stampRanges(f.container(), rootFlow));
		}

		// C1c: the continuation path absorbs top-level replayable closed subtrees in each frame container,
		// including their boxes, and carries them as replay ranges with serials (prefixItems).
		// resume merges them with the remaining items in serial order and drives them again.
		// Derive walk depth from the frame tail (Child=0, OpenTailShape=d).
		final int depth = this.flowStack.size();
		// Record what was stacked at the break (used only when an invariant fails).
		final String flowsAtBreak = this.describeFlowStack();
		java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> rootPrefix = java.util.List
				.of();
		final java.util.List<java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> framePrefixes = new java.util.ArrayList<>(
				innerFrames.size());
		if (rootChildFrame != null) {
			final boolean rootVertical = rootFlow.isVertical();
			if (nextRootContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
				// Traverse the root container with depth=0 when creating the continuation.
				rootPrefix = fc.extractReplayable(ranges, rootVertical, 0);
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
				// Walk depth is Child=0, OpenTailShape=actual remaining depth
				// (same reason as in prepareColumnContinuation above).
				final int walkDepth = switch (f.tail()) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child -> 0;
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				};
				framePrefixes.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						? fc.extractReplayable(ranges, rootVertical, walkDepth)
						: java.util.List.of());
			}
		}

		// C1d-C: reconstruct the frame tree with embedded prefixes from inside out.
		// The innermost frame retains the OpenTailShape determined by the cascade.
		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = null;
		for (int i = innerFrames.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = innerFrames.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.recipe(), f.state(),
							f.container(), f.crossExtent(), framePrefixes.get(i), tail == null ? f.tail() : tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootFrame = new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(
				rootRecipe, rootState, nextRootContainer, rootCrossExtent, rootPrefix,
				tail == null ? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						net.zamasoft.foliojet.layout.fragment.OpenShape.of(depth)) : tail);
		final net.zamasoft.foliojet.layout.fragment.Continuation continuation = new net.zamasoft.foliojet.layout.fragment.Continuation(
				depth, rootFrame, ranges);

		// 2026-07-24 (E-3 increment 4): validate the canonical Continuation directly
		// (the old ResumeProgramCompiler/ContinuationVerifier invariants were ported
		// to ContinuationValidator; no program is generated anymore).
		// A malformed continuation throws here, before flowStack.clear() or resume-side
		// state mutations, and stops safely. 2026-07-30 (increment 4d):
		// the returned PathShape was used only to derive tail policy (WorklistTailGate),
		// so with the gate retired, discard it and retain only structural validation.
		net.zamasoft.foliojet.layout.fragment.ContinuationValidator.validatePage(snapshot, continuation);

		this.flowStack.clear();
		// 2026-07-23 (exclusion space P1 increment 1): discard the old fragment's hidden-scope ledger
		// (resume's startFlowBlock() re-registers resumed hidden flows).
		this.rebuildNoOverflowFloatingScopes();
		pageBox.restyle(this, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
		// P1: the session owns leases (per occurrence) and scopes,
		// symmetrically guaranteeing consume-once and exception cleanup.
		try (ResumeSession session = new ResumeSession(continuation, snapshot)) {
			session.resume();
			assert !session.hasUnconsumedLeases() : "未消費の吸収済み再生範囲が残っています";
		}
		this.pageGenerator.compactLayoutSource(watermark);
		// 2026-07-21: previously only an assert checked this (unchecked in production). The ChatGPT Pro
		// consultation identified tables with orthogonal writing-mode (page breaks through
		// IncrementalTableBuilder, with BreakableBuilder.forceBreak() bypassing the breakDepth barrier)
		// as an existing reachable path that violates this invariant, and observation confirmed it
		// (an existing bug unrelated to this session's changes). Disabling this check in production
		// could let processing continue with flowStack inconsistent across the break,
		// leading to undetected content corruption,
		// so throw an exception in both tests and production.
		if (this.flowStack.size() != continuation.depth()) {
			// **Include what was stacked and restacked.** Depth numbers alone do not reveal which flow was lost,
			// and diagnosis took hours (2026-08-03).
			// **Also include the level where capability scanning stopped** (2026-09-16). The depth difference
			// is a mismatch between continuation.depth() = flowStack at the break and the frame chain =
			// BreakPlan's approvedBoxes. firstBarrier marks their divergence;
			// without it, the reason for the shallow chain (which box and capability stopped it) is unknown.
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"break flow failed (flowStack.size()=" + this.flowStack.size() + ", continuation.depth()="
							+ continuation.depth() + ")\n  破断時: " + flowsAtBreak + "\n  再開後: "
							+ this.describeFlowStack() + "\n  改ページ種別: " + mode + "\n  能力スキャン: 承認 "
							+ plan.chain().size() + " 段 / 開き " + snapshot.levels().size() + " 段, 障壁 "
							+ snapshot.firstBarrier()
									.map(b -> "index=" + b.openPathIndex() + " reason=" + b.reason())
									.orElse("なし")
							+ "\n  開き鎖の分類: " + this.describeOpenPathCapabilities(snapshot)
							+ "\n  継続の構造: " + describeContinuationShape(continuation));
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("restyled");
		}

		// Left/right page breaks.
		if (mode instanceof BreakMode.ForceBreakMode) {
			ForceBreakMode force = (ForceBreakMode) mode;
			if ((force.breakType == PageBreakMode.VERSO || force.breakType == PageBreakMode.RECTO)
					&& (this.pageSide == PageBreakMode.VERSO || this.pageSide == PageBreakMode.RECTO)) {
				if (force.breakType != this.pageSide) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("white page: " + force);
					}
					this.forceBreak(force.breakType);
				}
			}
		}
		this.endRestyling();
		// Increment 5: if the owner's continuation did not receive the carry-over during replay (multi-column
		// layout does not continue, another multi-column layout opened elsewhere, or nested continuation became
		// ineligible), return it to the page host before laying out body text (codex review 2026-09-08 requirement 2).
		this.flushColumnFootnoteCarry();

		return true;
	}

	/**
	 * Describes the continuation frame-chain depth and terminal open shape (2026-09-16, for diagnostics).
	 *
	 * <p>
	 * {@code continuation.depth()} comes from {@code flowStack} at the break, whereas this frame chain determines the
	 * number of levels restacked during resumption. Diagnosing a mismatch requires both.
	 * </p>
	 */
	private static String describeContinuationShape(
			final net.zamasoft.foliojet.layout.fragment.Continuation continuation) {
		int frames = 0;
		net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame = continuation.root();
		net.zamasoft.foliojet.layout.fragment.OpenShape terminal = null;
		while (frame != null) {
			++frames;
			switch (frame.tail()) {
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(final var child) -> frame = child;
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(final var shape) -> {
				terminal = shape;
				frame = null;
			}
			}
		}
		return "フレーム " + frames + " 段, 終端の開き形 " + (terminal == null ? "なし" : terminal + " (depth=" + terminal.depth() + ")")
				+ ", depth フィールド " + continuation.depth();
	}

	/**
	 * Describes each open-chain level's box and continuation capability on one line (2026-09-16, for diagnostics).
	 *
	 * <p>
	 * When continuation depth differs from depth after resumption, the divergence is the level where {@link
	 * net.zamasoft.foliojet.layout.fragment.ContinuationCapability} was not approved. List the box type and
	 * classification at each level.
	 * </p>
	 */
	private String describeOpenPathCapabilities(
			final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
		final StringBuilder out = new StringBuilder();
		for (final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.OpenLevelDescriptor level : snapshot
				.levels()) {
			if (level.index() > 0) {
				out.append(" / ");
			}
			out.append('[').append(level.index()).append(']').append(level.boxClass().getSimpleName()).append(':');
			switch (level.role()) {
			case net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.OpenLevelRole.Anchor(final var kind) ->
				out.append("anchor(").append(kind).append(')');
			case net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.OpenLevelRole.Ancestor(
					final var capability) ->
				out.append(capability);
			}
		}
		return out.toString();
	}

	/**
	 * Executes a page break: finalizes and outputs the current page, opens the next page, places carried-over
	 * footnotes and page floats again, and resets the flow position to the page start (extracted from {@link
	 * #pageBreak} on 2026-10-05; the body was merely moved). Footnotes must be re-reserved before rebuilding the
	 * continuing body text.
	 *
	 * @param mode the mode of this page break
	 * @return the closed page
	 */
	private PageBox turnPage(final BreakMode mode) {
		this.finishLayout();
		// Pages with nothing drawn are not output (css-break-3 §4.4). A dropped page
		// does not consume a side (recto/verso), so do not advance our side tracking
		// either; doing so would invert every subsequent left/right page break.
		if (mode instanceof ForceBreakMode force && force.namedTransition) {
			// Drop pages closed by a name transition if blank (N2b; drawPage decides).
			this.pageBox.markNamedTransitionClosed();
		}
		final boolean emitted = this.pageGenerator.drawPage(this.pageBox, false,
				mode instanceof BreakMode.ForceBreakMode);
		final PageBox pageBox = this.pageBox;
		this.pageBox = this.nextPage();
		this.beginPage();
		this.resetPageMarginNoteCursors();
		if (mode instanceof BreakMode.ForceBreakMode) {
			// Keep pages started by a forced page break even if blank, as the author's intent.
			this.pageBox.markForcedBreakOrigin();
		}
		// Footnotes F4: re-reserve carried-over footnotes (carry-in) against the new page's
		// capacity first, before restyling or constructing the continuing body text;
		// otherwise, it would be laid out using capacity without reservations.
		this.reserveFootnotes();
		if (emitted && this.pageSide != PageBreakMode.AUTO) {
			this.pageSide = (this.pageSide == PageBreakMode.VERSO) ? PageBreakMode.RECTO : PageBreakMode.VERSO;
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("breaked: " + mode + "/pageSide=" + this.pageSide);
		}

		// Resume the context. Place top page floats at the new page start,
		// and apply two-dimensional exclusion to body text from the page start.
		this.contextFlow = new Flow(this.pageBox, 0, 0);
		this.reserveBottomFloats();
		this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
				super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
		this.resetFragmentCursor(0, 0);
		return pageBox;
	}

	/** Formats the flow stack contents for humans (for invariant diagnostics). */
	private String describeFlowStack() {
		final StringBuilder out = new StringBuilder();
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			if (i > 0) {
				out.append(" / ");
			}
			out.append(flow.box.getClass().getSimpleName());
			if (flow.box.getParams() != null && flow.box.getParams().element != null) {
				out.append('<').append(flow.box.getParams().element).append('>');
			}
		}
		return out.toString();
	}

	/**
	 * Consumes continuation frames from outside in (C1d-A). Constructs each frame's fragment box for the first time
	 * here, then traverses its container merged with the absorbed prefix. For a Child tail, depth=0 (the chain child
	 * is not in the container); for OpenTailShape, use the existing depth convention (continuation of the innermost
	 * moved-open box or open text).
	 *
	 * <p>
	 * 2026-07-20: converted self-recursion in the {@code Child} branch (once per chain-fragment level) into an
	 * explicit loop (ARCHITECTURE.md invariant 6). {@code DeepNestingRestyleTest} (depth 200) confirmed over 1000
	 * actual {@code ContinuationStats.CHILD_FRAMES} hits; leaving recursion could cause StackOverflowError in deeply
	 * nested documents. The recursive call was the sole, final statement in the switch branch (tail recursion with no
	 * work afterward), so updating `frame`/`index` and returning to the loop start makes it iterative without changing
	 * behavior.
	 * </p>
	 *
	 * @param frame starting frame
	 * @param index position from outside (0=root; chain-fragment number in the trace)
	 * @param depth depth of the entire continuation (for trace output)
	 * @param snapshot snapshot at the break (direct comparison of actual fragment signatures, E-3 increment 2)
	 */
	private void resumeFrame(net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame, int index,
			final int depth, final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
		this.resumeFragmentChain(frame, index, depth, snapshot, this);
	}

	/**
	 * Fragment chain executor shared by PAGE/COLUMN (2026-07-21; renamed from the PAGE-only {@code resumeFrame} and
	 * explicitly extracted as a shared method in the remaining M6b Phase B4 work). The {@code index==0} branch for
	 * whole-box restyle (uncollectable break, no chain) appears PAGE-root-specific, but this method is the entry for
	 * both PAGE and COLUMN. COLUMN (execution of the fragment chain inside the owner) always calls with {@code
	 * index=1}, making this branch structurally unreachable from COLUMN (index increases monotonically, so once
	 * index&gt;0 it never returns to index==0). Splitting it into a wholly separate method risks duplicating fragment
	 * reconstruction by {@code continueFragment}, so retain the conditional within a single loop.
	 *
	 * @param target builder to receive state mutations (startFlowBlock/restyle; added 2026-07-21,
	 *               M6b Phase B4-Step4). PAGE always uses {@code RootBuilder.this}, as before.
	 *               COLUMN passes the actual {@code BreakableBuilder} driving the column break,
	 *               possibly a nested {@code ColumnBuilder} when the contents of an M6c column-balancing
	 *               probe themselves require another column break.
	 */
	private void resumeFragmentChain(net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame, int index,
			final int depth, final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot,
			final BlockBuilder target) {
		while (true) {
			this.checkAbort();
			assert !this.resumeScopes.isEmpty();
			final net.zamasoft.foliojet.layout.box.AbstractBlockBox block = net.zamasoft.foliojet.layout.box.AbstractBlockBox
					.continueFragment(frame.recipe(), frame.state(), frame.container(), frame.crossExtent());
			// P1: consumption with type checking (explicitly add new kinds such as table frames;
			// groundwork for the FrameRemainder sum type, avoiding failures from blind casts).
			if (!(block instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox box)) {
				throw new IllegalStateException("未対応のフレーム種別: " + block.getClass().getName());
			}
			// 2026-07-24 (E-3 increment 2): directly compare the actual fragment signature
			// against the snapshot at the break immediately after instantiate, before builder
			// state mutations (startFlowBlock/restyle). This directly implements the sole
			// independent value of the shadow Instantiate comparison. A mismatch stops with
			// a typed exception; do not retry through legacy.
			final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.FragmentSignature signature = net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.FragmentSignature
					.from(box);
			net.zamasoft.foliojet.layout.fragment.ContinuationValidator.checkFragmentSignature(snapshot, index,
					signature);
			switch (frame.tail()) {
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) -> {
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordChildFrame();
				target.startFlowBlock(box);
				this.restyleFrame(target, box.getContainer(), frame.prefixItems(),
						net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(index + 1, "chain-fragment",
						"depth=" + (depth - (index + 1)));
				frame = child;
				++index;
			}
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
					final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> {
				// 2026-07-30 (increments 4c/4d): retired duplicate depth-guard checks and the
				// B6a1 worklist eligibility check + override (previously drove terminal restyle
				// with the worklist only for WORKLIST_ELIGIBLE). restyle() itself
				// is now unconditionally driven by the worklist executor.
				if (index == 0) {
					// Uncollectable break (no chain): the existing whole-box restyle.
					// No prefix absorption has occurred on this path.
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordUnchainedRestyle();
					assert frame.prefixItems().isEmpty();
					box.restyle(target, shape);
				} else {
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenTail();
					target.startFlowBlock(box);
					this.restyleFrame(target, box.getContainer(), frame.prefixItems(), shape);
				}
				return;
			}
			}
		}
	}

	/**
	 * Resumes a frame container (C1c). Merges absorbed replay ranges (prefix) with remaining items in serial order.
	 *
	 * @param target builder to receive state mutations (generalized from fixed {@code this}
	 *               on 2026-07-21, B4-Step4).
	 */
	private void restyleFrame(final BlockBuilder target, final net.zamasoft.foliojet.layout.box.content.Container container,
			final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix,
			final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
			fc.restyle(target, shape, false, prefix);
		} else {
			assert prefix.isEmpty();
			container.restyle(target, shape, false);
		}
	}

	/**
	 * Redrives an absorbed closed subtree from source (C1c). Unconditional because replay eligibility was determined
	 * at the break (stampRanges).
	 */
	public void replaySubtree(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range,
			final BlockBuilder target) {
		final net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession session = this.sessions.peek();
		try {
			if (!net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(),
					range.fromId(), range.toId(), target, this.pageGenerator)) {
				// Absorbed ranges carry no boxes (no fallback).
				// Missing events that the lease should protect are an implementation bug; fail.
				throw new IllegalStateException("吸収済み再生範囲が失われました: [" + range.fromId() + ", " + range.toId() + "]");
			}
			net.zamasoft.foliojet.layout.SourceReplayer.PREFIX_REPLAYS.incrementAndGet();
		} finally {
			// Consumption complete. Even if a nested page break occurs during replay,
			// the lease survives until finally, so remaining events are not compacted.
			if (session != null) {
				session.releaseLease(range);
			}
		}
	}

	/**
	 * Attempts to redrive a moved closed subtree from source (M6b). Redrives only while rebuilding the remainder after
	 * a page break, when the anchor belongs to the current generation and is closed within the window. On false, fall
	 * back to box replay.
	 */
	public boolean replayFromSource(final net.zamasoft.foliojet.layout.box.IBox box, final BlockBuilder target) {
		if (this.resumeScopes.isEmpty()) {
			return false;
		}
		// C2: all decisions were recorded at the break (stampRanges). Only consume here
		// (records of the current, innermost resume scope). Consume-once: never replay
		// the same range twice (P0; makes an external review finding explicit).
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = this.resumeScopes.peek()
				.remove(box);
		if (range == null) {
			return false;
		}
		// If a range has gaps (e.g. due to nested compaction), return false before execution
		// and fall back to box-restyle, since the boxes remain.
		return net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(), range.fromId(),
				range.toId(), target, this.pageGenerator);
	}

	/**
	 * Returns the minimum EventId of remainder items closed within the window (M6b v3 compaction watermark), or
	 * Long.MAX_VALUE if none.
	 */
	private long sourceWatermark(final net.zamasoft.foliojet.layout.box.content.Container container) {
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return Long.MAX_VALUE;
		}
		final long[] min = { Long.MAX_VALUE };
		container.eachFlowBox(box -> {
			final long id = box.getSourceAnchor();
			if (id >= 0 && log.endOf(id) >= 0) {
				min[0] = Math.min(min[0], id);
			}
		});
		return min[0];
	}

	protected void finishLayout() {
		// Increment 5: for the last column closed by a page split (after a successful cut), scan
		// the container left on the old page, attach notes at column block-end, and carry unplaced notes forward.
		// Return remaining carry-over to the page host even if no column is open.
		if (this.recoveredColumnFootnotes != null) this.settleRecoveredFootnotes(this.pageAxis);
		if (this.columnFootnoteHost != null) {
			final FootnoteHost host = this.columnFootnoteHost;
			this.columnFootnoteHost = null;
			if (!host.pendingFootnotes.isEmpty()) {
				final double capacity = Double.isNaN(this.columnFootnoteCutCapacity) ? host.capacityBase.getAsDouble()
						: this.columnFootnoteCutCapacity;
				this.attachColumnFootnotes(host, capacity);
				this.columnFootnoteCarry.addAll(host.pendingFootnotes);
				host.pendingFootnotes.clear();
			}
		} else {
			this.flushColumnFootnoteCarry();
		}
		this.columnFootnoteCutCapacity = Double.NaN;
		this.pageFinished = true;
		// **Footnotes that remain reserved but unplaced generate pages indefinitely**
		// (2026-08-21, sweep seed 439857 and others). Footnotes go on the page where their call is finalized,
		// but if the call is in content repeatedly sent to the next page within nested multi-column layout,
		// only the reservation (which narrows the type area) remains. The narrowed area sends the content
		// forward again, accumulating identically shaped pages up to the limit.
		// After two consecutive pages with no content but retained reservations, remove the first
		// note's reservation (deferred) and wait for the page where its call is finalized.
		//
		// **Changed on 2026-09-03** (cti.li report, [[2026-09-02-cti-li-footnote-
		// numbering.md]]). Previously, this switched to the same forced placement
		// (forceFootnoteAttach) as EOF draining, but ① the flag never reset,
		// so all later notes went on their registration pages without calls, falling back to document-wide numbering.
		// ② It also fired while accumulated large figures that avoided page breaks were split across several
		// pages (each with content), placing a note two pages before its call.
		// The "page without content" condition retains the sweep seed 439857 case (accumulating
		// pages containing only empty multi-column frames) and excludes sequential figure placement.
		// Removing the reservation lets content fit and finalizes the call; F4 carry-in places
		// the note first on the next page (with the number from the call's page).
		final boolean hadReservation = this.pageFootnoteHost.footnoteReservedCount > 0 || !this.footnotePlan.isEmpty();
		this.pageFootnoteHost.footnoteProgressed = false;
		this.pageHadContent = false;
		final double notesExtent = this.attachFootnotes();
		this.columnPageEntries.clear();
		this.columnPageLabels.clear();
		if (hadReservation && !this.pageFootnoteHost.footnoteProgressed && !this.pageHadContent) {
			if (++this.footnoteStallPages >= 2) {
				final FootnoteEntry head = this.pageFootnoteHost.pendingFootnotes.peekFirst();
				if (head != null && !head.committed) {
					head.deferred = true;
				}
				this.footnoteStallPages = 0;
			}
		} else {
			this.footnoteStallPages = 0;
		}
		this.attachBottomPageFloats(notesExtent);
		this.pageBox.finishLayout(this.pageBox);
	}

	/** Consecutive pages with retained reservations but no progress in footnote placement. */
	private int footnoteStallPages = 0;

	/** Whether the most recent scan found lines or replaced elements on the page (input to the stall safety guard). */
	private boolean pageHadContent = false;

	public void finish() {
		this.requireNoIncompleteTable();
		this.finishLayout();
		// Footnotes F4: if footnotes deferred for capacity remain, generate note-only pages
		// until pending is empty. An iteration with no progress (no note placed)
		// is a typed invariant failure for a lost call or missed scan
		// (do not keep forwarding and generating infinite pages).
		while (!this.pageFootnoteHost.pendingFootnotes.isEmpty() || !this.columnFootnoteCarry.isEmpty()
				|| this.hasPendingPageFloats()) {
			this.pageFootnoteHost.footnoteProgressed = false;
			this.pageFloatProgressed = false;
			this.pageGenerator.drawPage(this.pageBox, false, false);
			this.pageBox = this.nextPage();
			this.beginPage();
			this.pendingCurrentTopFloats.removeIf(entry -> entry.generation() != this.pageGeneration);
			this.resetPageMarginNoteCursors();
			this.contextFlow = new Flow(this.pageBox, 0, 0);
			this.reserveFootnotes();
			this.reserveBottomFloats();
			this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
					super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
			this.resetFragmentCursor(0, 0);
			this.finishLayout();
			// An iteration without progress means a footnote's call remained on no page
			// (calls inside table cells or absolute positioning are outside the scan).
			// **Do not fail conversion** (ARCHITECTURE.md §5.13). On the next iteration,
			// place notes from the front regardless of calls; if that still makes no progress,
			// discard the remainder and warn (to avoid generating infinite pages).
			if (!this.pageFootnoteHost.footnoteProgressed && !this.pageFloatProgressed) {
				if (this.forceFootnoteAttach) {
					LOG.warning("giving up on footnotes whose calls were never found: "
							+ this.pageFootnoteHost.pendingFootnotes.size() + " pending at EOF");
					this.pageFootnoteHost.pendingFootnotes.clear();
					this.pendingTopFloats.clear();
					this.pendingBottomFloats.clear();
					break;
				}
				this.forceFootnoteAttach = true;
			}
		}
		this.pageGenerator.drawPage(this.pageBox, true, false);
		this.traceFootnote("finish", null, 0, java.util.Set.of());
	}

	// ------------------------------------------------------------------
	// Footnotes (F2–F4, 2026-07-31; consult-codex-2026-07-31-footnote.txt §3 and
	// the corresponding -f4.txt. Owner-approved initial subset: document-wide numbering, conservative reservation,
	// typed errors for oversized notes that cannot fit even on an empty page; vertical writing,
	// multi-column layout, @footnote, and splitting deferred to later increments).

	/**
	 * One unplaced footnote. {@code committed} means its call remained on a previously finalized page. A footnote
	 * carried forward for capacity (carry-in) must be placed first on the next page even with zero calls (the key
	 * point of the F4 recommendation).
	 */
	private static final class FootnoteEntry {
		final long id;

		net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox;
		/** Can retain B's measured height and the call-page number even before the body arrives. */
		double measuredHeight = Double.NaN;

		boolean committed = false;
		/** The call remains in a finalized column on this page. Wait until page finalization to number and commit it. */
		boolean columnCallRetained = false;
		/** Host where the note was finally attached at column end (used for collection before balancing; increment 6). */
		FootnoteHost attachedColumnHost;
		/**
		 * Do not reserve in this page generation (collected note that cannot fit after balancing; carry into the next
		 * page).
		 */
		long holdReservationUntil = -1;

		/**
		 * Page-local footnote number (F5, starting at 1; -1 if unnumbered). The scope is <b>the page retaining the
		 * call</b>, not the page where the note is placed. A carried-in note retains its call-page number on later pages.
		 */
		int assignedNumber = -1;

		/**
		 * A note whose reservation is removed while waiting for its call's page (2026-09-03). Set on a stall where
		 * reservation keeps pushing content out and prevents the call from being finalized. No reservation exists on the
		 * page that finalizes the call, so the note cannot be placed there; carry-in (committed) places it first on the
		 * next page.
		 */
		boolean deferred = false;

		FootnoteEntry(final long id, final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
			this.id = id;
			this.noteBox = noteBox;
		}
	}

	/**
	 * State belonging to a footnote host. Reservation, numbering, and rescue decisions stay in Root.
	 * Look up the page container and capacity base when used; do not freeze values from before a page break or cut.
	 */
	private static final class FootnoteHost {
		/** Unplaced footnotes (document order is canonical; bidi and similar processing can disrupt box-tree scan order). */
		final java.util.ArrayDeque<FootnoteEntry> pendingFootnotes = new java.util.ArrayDeque<>();

		/**
		 * Count of the leading pending prefix reserved on this page and its reserved extent (including gap, in the page
		 * direction). Reservations never decrease within a page: conservative reservation does not return space even when
		 * a call moves to the next page (may leave space at the previous page's bottom; an explicit specification
		 * deviation).
		 */
		int footnoteReservedCount = 0;
		double footnoteReservation = 0;
		/** Distinguishes explicitly reserved minimum space from the extent actually used by notes. */
		double footnoteUsed = 0;
		/** Whether placement advanced in the most recent attach (finish() progress guard). */
		boolean footnoteProgressed = false;
		double atomicFloatFloor = 0;

		final java.util.function.Supplier<net.zamasoft.foliojet.layout.box.content.Container> container;
		final java.util.function.DoubleSupplier capacityBase;
		final java.util.function.DoubleSupplier lineSize;
		final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner;
		final double lineOrigin;
		final double pageOrigin;

		FootnoteHost(final java.util.function.Supplier<net.zamasoft.foliojet.layout.box.content.Container> container,
				final java.util.function.DoubleSupplier capacityBase, final java.util.function.DoubleSupplier lineSize,
				final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner,
				final double lineOrigin, final double pageOrigin) {
			this.container = container;
			this.capacityBase = capacityBase;
			this.lineSize = lineSize;
			this.owner = owner;
			this.lineOrigin = lineOrigin;
			this.pageOrigin = pageOrigin;
		}

		void addFloating(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox, final double pageAxis) {
			// For the page host (origin 0), pass the existing coordinates unchanged, without an extra addition.
			this.container.get().addFloating(noteBox, 0, pageAxis);
		}
	}

	/** One page host per document. Bottom floats and fixed bands share only state and use the existing paths. */
	private final FootnoteHost pageFootnoteHost = new FootnoteHost(
			() -> this.pageBox.getContainer(), super::getPageLimit, () -> this.pageBox.getLineSize(), null, 0, 0);
	/** Current column. Do not reserve space or perform additional scans until a relevant note arrives. */
	private FootnoteHost columnFootnoteHost;
	/**
	 * Notes unplaced in the old page's last column when a page split closes it (call moved to the next page, or note
	 * did not fit). Pass them to the first column host opened on the next page; return them to the page host if a note
	 * arrives or the page ends without a column opening (increment 5).
	 */
	private final java.util.ArrayDeque<FootnoteEntry> columnFootnoteCarry = new java.util.ArrayDeque<>();
	/** Last column's capacity fixed before the page split (independent of the root's inner size after cutting). */
	private double columnFootnoteCutCapacity = Double.NaN;
	/**
	 * Identifies the continuing owner that receives carry-over by its position in the open chain (flowStack) at the
	 * cut. Continuation replay rebuilds boxes in the same order, so only multi-column layout opened at that position
	 * during replay counts as the owner's continuation (optional grok review item, 2026-09-07).
	 */
	private int columnFootnoteCarryChainIndex = -1;
	/** Column notes collected before balancing; check fit afterward before moving them to the page host (increment 6). */
	private FootnoteHost recoveredColumnFootnotes;
	/** Retain entries removed from FIFO by column attachment until document-order numbering for the page finishes. */
	private final java.util.SortedMap<Long, FootnoteEntry> columnPageEntries = new java.util.TreeMap<>();
	private final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> columnPageLabels = new java.util.ArrayList<>();

	/** Observe only values of cut columns. Do not retain trees or entries in tests. */
	public record ColumnFootnotePlacement(long generation, double lineOrigin, double pageOrigin,
			double capacity, double lineSize, double reservation, double attachedExtent,
			java.util.List<Long> attachedIds, java.util.Set<Long> retainedIds) { }
	static volatile java.util.function.Consumer<ColumnFootnotePlacement> columnFootnoteObserver;

	final void openFootnoteColumn(final BreakableBuilder builder, final Flow flow) {
		if (this.isPageBandFootnoteArea() || this.footnoteArea().isHeightFixed()
				|| !this.isEligibleFootnoteColumnOwner(builder, flow.box)) return;
		final var owner = flow.box;
		final var column = owner.getContainer() instanceof net.zamasoft.foliojet.layout.box.content.ColumnsContainer columns
				? columns.getLastColumn() : owner.getContainer();
		if (!(column instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer)) return;
		if (this.columnFootnoteHost != null && this.columnFootnoteHost.owner == owner
				&& this.columnFootnoteHost.container.get() == column) return;
		int depth = 1;
		for (int i = builder.getFlowCount() - 1; i >= 0 && builder.getFlow(i) != flow; --i) ++depth;
		final double lastFrame = builder.lastFrame(flow, depth);
		final double lineOrigin = flow.lineAxis
				+ (owner.getActualColumnCount() - 1) * (owner.getLineSize() + owner.getBlockParams().columns.gap);
		this.columnFootnoteHost = new FootnoteHost(() -> column,
				() -> this.getPageOwnerLimit() - flow.pageAxis - lastFrame, owner::getLineSize,
				owner, lineOrigin, flow.pageAxis);
		if (!this.columnFootnoteCarry.isEmpty() && builder == this && this.isRestyling()
				&& this.flowStack.size() - 1 == this.columnFootnoteCarryChainIndex) {
			// Notes carried from the previous page's last column go to the first column of the owner's
			// continuation opened at the same position during replay (before replaying continuing body text,
			// so the column uses reserved capacity). If not a continuation, return them to the page host at replay end.
			for (final FootnoteEntry entry : this.columnFootnoteCarry) {
				this.columnPageEntries.put(entry.id, entry);
				this.traceFootnote("column-carry", entry, 0, java.util.Set.of());
			}
			this.columnFootnoteHost.pendingFootnotes.addAll(this.columnFootnoteCarry);
			this.columnFootnoteCarry.clear();
			this.reserveColumnFootnotes(this.columnFootnoteHost);
		}
	}

	/** Returns carry-over to the page host when a note arrives or the page ends without a column opening. */
	private void flushColumnFootnoteCarry() {
		if (this.columnFootnoteCarry.isEmpty()) return;
		final FootnoteHost carrier = new FootnoteHost(() -> null, () -> 0, () -> 0, null, 0, 0);
		carrier.pendingFootnotes.addAll(this.columnFootnoteCarry);
		this.columnFootnoteCarry.clear();
		this.transferColumnFootnotes(carrier);
	}

	/** endFlowBlock also handles span-all boundaries and auto endings, and calls this before balancing. */
	final void closeFootnoteColumn(final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		final FootnoteHost host = this.columnFootnoteHost;
		if (host == null || host.owner != owner) return;
		this.columnFootnoteHost = null;
		if (owner.getBlockParams().columns.fill == net.zamasoft.foliojet.layout.box.params.Columns.FILL_BALANCE) {
			// Increment 6: balancing replays column containers and does not retain notes finally attached
			// at column ends (absent in source replay; ordinary floats in box replay).
			// Before replay, detach attached notes from every column and move them to the page host in document order.
			for (final FootnoteEntry entry : this.columnPageEntries.values()) {
				final FootnoteHost attached = entry.attachedColumnHost;
				if (attached == null || attached.owner != owner) continue;
				if (attached.container.get() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer column) {
					column.removeFloating(entry.noteBox);
				}
				entry.attachedColumnHost = null;
				host.pendingFootnotes.addLast(entry);
				this.traceFootnote("column-recover", entry, 0, java.util.Set.of());
			}
			this.pageBox.removeColumnFootnoteSeparators(owner);
			// The page's remaining capacity depends on the post-balance multi-column height, so transfer afterward.
			this.recoveredColumnFootnotes = host;
			return;
		}
		this.transferColumnFootnotes(host);
	}

	/**
	 * After balancing (once the multi-column height is fixed), checks whether collected notes fit the remaining
	 * capacity after the columns, then moves them to the page host. If they do not fit, do not reserve on this page;
	 * retain the call-page number and carry them into the next page (F4). Closed multi-column layout can be cut only
	 * in the last column, so pushing body text out with a reservation would overlap earlier columns with notes (grok
	 * review requirement 3).
	 */
	final void settleRecoveredFootnotes(final double pageAxisAfterOwner) {
		final FootnoteHost host = this.recoveredColumnFootnotes;
		if (host == null) return;
		this.recoveredColumnFootnotes = null;
		double needed = FOOTNOTE_GAP;
		for (final FootnoteEntry entry : host.pendingFootnotes) needed += this.footnoteExtent(entry.noteBox);
		final double available = this.getPageLimit() - pageAxisAfterOwner;
		if (needed > available) {
			for (final FootnoteEntry entry : host.pendingFootnotes) {
				entry.holdReservationUntil = this.pageGeneration;
				this.traceFootnote("column-hold", entry, needed - available, java.util.Set.of());
			}
		}
		this.transferColumnFootnotes(host);
	}

	private void transferColumnFootnotes(final FootnoteHost host) {
		if (host.pendingFootnotes.isEmpty()) return;
		final java.util.SortedMap<Long, FootnoteEntry> entries = new java.util.TreeMap<>();
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) entries.put(entry.id, entry);
		for (final FootnoteEntry entry : host.pendingFootnotes) entries.put(entry.id, entry);
		this.pageFootnoteHost.pendingFootnotes.clear();
		this.pageFootnoteHost.pendingFootnotes.addAll(entries.values());
		host.pendingFootnotes.clear();
		// Re-reserve in document order even when inserting into the prefix. Do not return existing conservative reservations.
		final double reserved = this.pageFootnoteHost.footnoteReservation;
		this.pageFootnoteHost.footnoteReservedCount = 0;
		this.pageFootnoteHost.footnoteReservation = 0;
		this.pageFootnoteHost.footnoteUsed = 0;
		this.reserveFootnotes();
		this.pageFootnoteHost.footnoteReservation = Math.max(reserved, this.pageFootnoteHost.footnoteReservation);
	}

	private double columnFootnoteExtent(final FootnoteHost host, final FootnoteEntry entry) {
		return entry.noteBox.getPageExtent(host.owner.getBlockParams().flow);
	}

	private void reserveColumnFootnotes(final FootnoteHost host) {
		if (host.pendingFootnotes.isEmpty()) return;
		final double maxArea = host.capacityBase.getAsDouble() - Math.max(MIN_PAGE_LIMIT, host.atomicFloatFloor);
		int i = 0;
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (i++ < host.footnoteReservedCount) continue;
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) break;
			final double cost = (host.footnoteUsed == 0 ? FOOTNOTE_GAP : 0) + this.columnFootnoteExtent(host, entry);
			if (host.footnoteUsed + cost > maxArea) {
				if (host.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					host.footnoteReservation = maxArea;
					host.footnoteUsed = maxArea;
					host.footnoteReservedCount = 1;
				}
				break;
			}
			host.footnoteUsed += cost;
			host.footnoteReservation = Math.max(host.footnoteUsed, Math.min(maxArea, this.footnoteArea().minHeight));
			++host.footnoteReservedCount;
		}
	}

	/** Scans and finally attaches only in the committed old column. Does not resolve numbers here. */
	private void attachColumnFootnotes(final FootnoteHost host, final double capacity) {
		final FootnoteCallScan scan = scanFootnoteCalls(host.container.get(), host.owner, true);
		this.columnPageLabels.addAll(scan.labels());
		int count = 0;
		double extent = 0;
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (scan.ids().contains(entry.id)) entry.columnCallRetained = true;
		}
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (count >= host.footnoteReservedCount || (!entry.committed && !entry.columnCallRetained)) break;
			final double nextExtent = extent + this.columnFootnoteExtent(host, entry);
			if (FOOTNOTE_GAP + nextExtent > capacity - MIN_PAGE_LIMIT && !entry.committed) break;
			extent = nextExtent;
			++count;
		}
		double pageAxis = capacity - extent;
		final var observer = columnFootnoteObserver;
		final java.util.List<Long> attachedIds = observer == null ? null : new java.util.ArrayList<>();
		for (int i = 0; i < count; ++i) {
			final FootnoteEntry entry = host.pendingFootnotes.removeFirst();
			if (attachedIds != null) attachedIds.add(entry.id);
			host.addFloating(entry.noteBox, pageAxis);
			entry.attachedColumnHost = host;
			pageAxis += this.columnFootnoteExtent(host, entry);
			this.columnPageLabels.addAll(this.scanFootnoteCalls(entry.noteBox).labels());
			host.footnoteProgressed = true;
		}
		if (count > 0) {
			this.pageBox.addColumnFootnoteSeparator(host.owner, host.owner.getBlockParams().flow, host.lineOrigin,
					host.pageOrigin, host.lineSize.getAsDouble(), capacity - extent - FOOTNOTE_GAP / 2);
		}
		if (observer != null) observer.accept(new ColumnFootnotePlacement(this.pageGeneration, host.lineOrigin,
				host.pageOrigin, capacity, host.lineSize.getAsDouble(), host.footnoteReservation, extent,
				java.util.List.copyOf(attachedIds), java.util.Set.copyOf(scan.ids())));
	}

	/**
	 * Finds the nearest multi-column owner from the arrival source, preferring the inner one across local builders
	 * too.
	 */
	public static net.zamasoft.foliojet.layout.box.AbstractContainerBox footnoteColumnOwner(
			final net.zamasoft.foliojet.layout.builder.LayoutStack parent) {
		for (net.zamasoft.foliojet.layout.builder.LayoutStack stack = parent; stack != null;
				stack = stack.getParentBuilder()) {
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner = stack.getMulticolumnBox();
			if (owner != null) return owner;
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox context = stack.getRootBox();
			if (context != null && context.getColumnCount() > 1) return context;
		}
		return null;
	}

	/** Accepts only variable-height owners in Root's normal flow with no outer multi-column layout. */
	public boolean isEligibleFootnoteColumnOwner(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (owner == null || owner.getColumnCount() <= 1 || owner.isFixedMulticolumn()
				|| footnoteColumnOwner(parent) != owner) return false;
		boolean reachesRoot = false;
		for (net.zamasoft.foliojet.layout.builder.LayoutStack stack = parent; stack != null;
				stack = stack.getParentBuilder()) {
			if (stack instanceof ColumnBuilder) return false;
			if (stack == this) {
				reachesRoot = true;
				break;
			}
			if (stack.getMulticolumnBox() != null || stack.getRootBox().getColumnCount() > 1) return false;
		}
		if (!reachesRoot) return false;
		for (int i = 0; i < this.getFlowCount(); ++i) {
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box = this.getFlow(i).box;
			if (box.getColumnCount() <= 1) continue;
			return box == owner;
		}
		return false;
	}

	private FootnoteHost selectFootnoteHost(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (this.isPageBandFootnoteArea() || this.footnoteArea().isHeightFixed()) return this.pageFootnoteHost;
		return this.columnFootnoteHost != null && this.columnFootnoteHost.owner == owner
				&& this.isEligibleFootnoteColumnOwner(parent, owner) ? this.columnFootnoteHost : this.pageFootnoteHost;
	}

	/**
	 * Page notes use existing measurement (NONE); only column notes use host line length as containing-block
	 * inline size.
	 */
	public double getFootnoteLineSize(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		final FootnoteHost host = this.selectFootnoteHost(parent, owner);
		return host == this.pageFootnoteHost ? net.zamasoft.foliojet.layout.util.LayoutUtils.NONE : host.lineSize.getAsDouble();
	}
	/** Existing observation hook for FootnoteSamePageTest. Only the page host owns and updates the actual FIFO. */
	private final java.util.ArrayDeque<FootnoteEntry> pendingFootnotes = this.pageFootnoteHost.pendingFootnotes;

	/** Passes only values to debugging and tests. Conversion runs on another thread in DirectSession. */
	public record FootnoteTrace(String event, long generation, int committedColumns, long id,
			double delta, double reservation,
			double pageLimit, int reservedCount, int pendingCount, boolean committed, boolean deferred,
			int number, java.util.Set<Long> retainedIds) { }
	static volatile java.util.function.Consumer<FootnoteTrace> footnoteTraceObserver;

	private void traceFootnote(final String event, final FootnoteEntry entry, final double delta,
			final java.util.Set<Long> retained) {
		final var observer = footnoteTraceObserver;
		if (!this.debugFootnote && observer == null) return;
		final FootnoteTrace trace = new FootnoteTrace(event, this.pageGeneration, this.committedColumnsOnPage,
				entry == null ? -1 : entry.id, delta, this.pageFootnoteHost.footnoteReservation,
				this.getPageOwnerLimit(), this.pageFootnoteHost.footnoteReservedCount, this.pageFootnoteHost.pendingFootnotes.size(),
				entry != null && entry.committed, entry != null && entry.deferred,
				entry == null ? -1 : entry.assignedNumber, java.util.Set.copyOf(retained));
		if (this.debugFootnote) System.err.println("[footnote] " + trace);
		if (observer != null) observer.accept(trace);
	}

	/** Logical IDs of footnotes received by the ledger, to avoid receiving the same note twice (2026-09-02). */
	private final java.util.Set<Long> registeredFootnotes = new java.util.HashSet<>();
	/** B-only numbering of late-arriving bodies and lifetime of replayable registrations. Not shared with MAIN. */
	private java.util.Map<Long, Integer> probeCallNumbers;
	private java.util.Map<Long, Long> probeFootnoteAnchors;
	/** C's ledger of unplaced IDs. FIFO includes reservation-only IDs and those awaiting late-arriving bodies. */
	private final java.util.Map<Long, FootnoteEntry> bottomFootnotes = new java.util.HashMap<>();
	private record FootnoteReservation(double height, boolean oversized) { }
	/** Reservation eligibility per ID, independent of the leading pending-prefix count. */
	private final java.util.Map<Long, FootnoteReservation> footnotePlan = new java.util.HashMap<>();
	private boolean initialFootnotePagePending;

	private boolean hasFootnotePlan() {
		return this.isPageBandFootnoteArea() && this.pageGenerator.isFootnotePageProbeEnabled();
	}

	private FootnoteEntry bottomFootnote(final long id) {
		FootnoteEntry entry = this.bottomFootnotes.get(id);
		if (entry == null) {
			entry = new FootnoteEntry(id, null);
			this.bottomFootnotes.put(id, entry);
			this.pageFootnoteHost.pendingFootnotes.addLast(entry);
			// TwoPass body completion order differs from document order. Merge ID-only advance registrations into the same FIFO.
			final java.util.List<FootnoteEntry> sorted = new java.util.ArrayList<>(this.pageFootnoteHost.pendingFootnotes);
			sorted.sort(java.util.Comparator.comparingLong(value -> value.id));
			this.pageFootnoteHost.pendingFootnotes.clear();
			this.pageFootnoteHost.pendingFootnotes.addAll(sorted);
		}
		return entry;
	}

	private boolean isFootnoteProbe() {
		return this.pageGenerator instanceof net.zamasoft.foliojet.layout.MeasurePageGenerator measure
				&& measure.isFootnoteProbe();
	}

	/** Releases registrations and measured heights already placed and before B's replay watermark. */
	public void reclaimProbeFootnotes(final long fromId) {
		if (this.probeFootnoteAnchors == null) return;
		final java.util.Set<Long> pending = new java.util.HashSet<>();
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) pending.add(entry.id);
		// Continuing tables reuse calls in already placed headers. Retain registrations until they disappear
		// from the current tree, preventing re-registration of late-body numbers on the next finalized page.
		final java.util.Set<Long> retained = collectFootnoteCalls(this.pageBox);
		final var iterator = this.probeFootnoteAnchors.entrySet().iterator();
		while (iterator.hasNext()) {
			final var entry = iterator.next();
			if (entry.getValue() < fromId && !pending.contains(entry.getKey()) && !retained.contains(entry.getKey())) {
				this.registeredFootnotes.remove(entry.getKey());
				if (this.probeCallNumbers != null) this.probeCallNumbers.remove(entry.getKey());
				((net.zamasoft.foliojet.layout.MeasurePageGenerator) this.pageGenerator).forgetFootnote(entry.getKey());
				iterator.remove();
			}
		}
	}

	/** For B's long-document tests: counts after collecting late-arrival numbers and replayable registrations. */
	public int probeFootnoteLedgerSize() {
		return (this.probeCallNumbers == null ? 0 : this.probeCallNumbers.size())
				+ (this.probeFootnoteAnchors == null ? 0 : this.probeFootnoteAnchors.size());
	}

	private boolean warnedFootnoteAreaLimit;

	private net.zamasoft.foliojet.ua.FootnoteArea footnoteArea() {
		// Do not reserve document bands on mini-pages measuring headers, running elements, or partial ranges.
		if (this.pageGenerator instanceof net.zamasoft.foliojet.layout.MeasurePageGenerator measure
				&& !measure.isFootnoteProbe()) return net.zamasoft.foliojet.ua.FootnoteArea.DEFAULT;
		return this.pageBox.getUserAgent().getUAContext().getFootnoteArea();
	}

	private double requestedFootnoteArea(final double maxArea) {
		final var area = this.footnoteArea();
		final double requested = Math.max(area.minHeight, area.height == null ? 0 : area.height);
		// Warn only in production (C). Trial layout (B) uses a separate RootBuilder and would warn twice per document.
		if (requested > maxArea && !this.warnedFootnoteAreaLimit && !this.isFootnoteProbe()) {
			this.warnedFootnoteAreaLimit = true;
			LOG.warning("footnote area limited to " + maxArea + "pt (requested " + requested + "pt)");
		}
		return Math.min(requested, maxArea);
	}

	private double blockFootnoteMaxArea() {
		return Math.max(0, this.pageBox.getInnerPageExtent(this.pageBox.getBlockParams().flow) - MIN_PAGE_LIMIT);
	}

	/** Gap between body text and footnote area (UA-fixed; the separator rule sits at its center). */
	private static final double FOOTNOTE_GAP = 6;

	/**
	 * The part of the page's footnote reservation that belongs to calls not yet committed to a page (2026-10-08,
	 * {@code AutoBreakMode.footnoteSlack}). A note is reserved as soon as its call is laid out, which can shorten the page
	 * below content already on it; if the break then moves the call, the note goes with it. Fixed, minimum-height and
	 * page-band footnote areas reserve space of their own and are left out.
	 */
	private double uncommittedFootnoteReservation() {
		final double reservation = this.pageFootnoteHost.footnoteReservation;
		if (reservation <= 0 || this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0
				|| this.isPageBandFootnoteArea()) {
			return 0;
		}
		double committed = 0;
		int i = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (i++ >= this.pageFootnoteHost.footnoteReservedCount) {
				break;
			}
			if (entry.committed || entry.noteBox == null) {
				committed += (committed == 0 ? FOOTNOTE_GAP : 0)
						+ (entry.noteBox == null ? 0 : this.footnoteExtent(entry.noteBox));
			}
		}
		return Math.max(0, reservation - committed);
	}

	/**
	 * Footnote extent in the page direction (axis-neutral; F6/F7 recommendation ②). The larger of box geometry and
	 * measured drawing extent (same as the existing float occupied-page-extent rule).
	 */
	private double footnoteExtent(final net.zamasoft.foliojet.layout.box.IBox box) {
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		return Math.max(box.getPageExtent(flow), box.paintedPageExtent(flow));
	}

	/**
	 * Whether placement reserves a band at a paper edge (top or bottom).
	 *
	 * <p>
	 * A band reduces the type area in the <b>line direction</b> (the paper's vertical direction for a vertical-writing
	 * page), so it works only on vertical-writing pages. On horizontal-writing pages, {@code bottom} is the same as
	 * block-end and passes through the existing path. There is no {@code top} path yet for horizontal-writing pages (a
	 * band at block-start), so it too falls back to block-end, with a single warning.
	 * </p>
	 */
	private boolean isPageBandFootnoteArea() {
		final net.zamasoft.foliojet.ua.FootnoteArea area = this.footnoteArea();
		if (!area.isPageBand()) {
			return false;
		}
		if (this.pageBox.getBlockParams().flow.isVertical()) {
			return true;
		}
		if (area.isHeadBand() && !this.warnedHeadBandHorizontal) {
			this.warnedHeadBandHorizontal = true;
			LOG.warning("@footnote { float: top } works on vertical pages only; falling back to block-end");
		}
		return false;
	}

	private boolean warnedHeadBandHorizontal;

	/**
	 * The footnote band's extent in the paper's vertical direction. Uses the TB axis, which returns physical height,
	 * to measure the block direction for horizontal-writing areas and the inline direction for vertical-writing areas.
	 * Separate from the measure for top/bottom floats.
	 */
	public static double footnoteBandExtent(final net.zamasoft.foliojet.layout.box.IBox box) {
		return Math.max(box.getPageExtent(WritingMode.TB), box.paintedPageExtent(WritingMode.TB));
	}

	/**
	 * Whether the bottom band itself also uses vertical writing (2026-09-11).
	 *
	 * <p>
	 * In a vertical-writing book, reserves a bottom band and flows notes into it **in vertical writing as well**. All
	 * axes swap relative to a horizontal band (F-1, {@code writing-mode: horizontal-tb}).
	 * </p>
	 *
	 * <table>
	 * <caption>Band axes</caption>
	 * <tr><th></th><th>Horizontal band</th><th>Vertical band</th></tr>
	 * <tr><td>Note line length</td><td>Paper inner width</td><td>Band height (descriptor)</td></tr>
	 * <tr><td>Note stacking direction</td><td>Paper vertical direction (top to bottom)</td><td>Paper horizontal
	 * direction (right to left)</td></tr>
	 * <tr><td>Band capacity</td><td>Band height</td><td>Paper inner width</td></tr>
	 * </table>
	 *
	 * <p>
	 * Line length comes from band height, so a vertical band requires an explicit {@code height} (otherwise, line
	 * length comes from the host or type area and notes overflow below the type area). Without it, fall back to the
	 * existing horizontal-band calculation and warn once.
	 * </p>
	 */
	private boolean isVerticalFootnoteBand() {
		if (!this.isPageBandFootnoteArea()) {
			return false;
		}
		final net.zamasoft.foliojet.ua.FootnoteArea area = this.footnoteArea();
		final WritingMode band = area.flow == null ? this.pageBox.getBlockParams().flow : area.flow;
		if (!band.isVertical()) {
			return false;
		}
		if (!area.isHeightFixed()) {
			if (!this.warnedVerticalBandHeight) {
				this.warnedVerticalBandHeight = true;
				LOG.warning("@footnote { float: bottom } with a vertical writing-mode needs an explicit height;"
						+ " the note line length is the band height");
			}
			return false;
		}
		return true;
	}

	private boolean warnedVerticalBandHeight;

	/**
	 * Extent occupied by notes within the band: paper vertical direction for horizontal bands, paper horizontal
	 * direction for vertical bands. Measures the note stacking direction, on the same axis as band capacity.
	 */
	private double footnoteBandCost(final net.zamasoft.foliojet.layout.box.IBox box) {
		if (!this.isVerticalFootnoteBand()) {
			return footnoteBandExtent(box);
		}
		final WritingMode pageFlow = this.pageBox.getBlockParams().flow;
		return Math.max(box.getPageExtent(pageFlow), box.paintedPageExtent(pageFlow));
	}

	/** Band capacity: band height for horizontal bands, paper inner width for vertical bands. */
	private double footnoteBandCapacity() {
		return this.isVerticalFootnoteBand()
				? this.pageBox.getInnerPageExtent(this.pageBox.getBlockParams().flow)
				: this.pageBandInset();
	}

	private static final double MAX_FOOT_AREA_RATIO = 0.6;

	/**
	 * B reserves only carry-over; C also includes measured IDs from the corresponding B report, reserving once. Fixed
	 * height reserves every page without B; min-height is the reservation floor. Unreserved notes or notes exceeding
	 * actual height keep the line length unchanged, finalize their calls, and move to the next band (F4). Block-axis
	 * footnoteReservation stays 0, so top/bottom float capacity also stays unchanged.
	 */
	private void beginPage() {
		if (!this.isPageBandFootnoteArea()) {
			if (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0) {
				this.pageFootnoteHost.footnoteUsed = 0;
				this.pageFootnoteHost.footnoteReservation = this.requestedFootnoteArea(this.blockFootnoteMaxArea());
				if (this.footnoteArea().isHeightFixed()) this.reserveFixedFootnotes();
			}
			return;
		}
		final double innerWidth = this.pageBox.getInnerWidth();
		final double innerHeight = this.pageBox.getInnerHeight();
		if (this.pageGeneration == 1 && this.hasFootnotePlan()) {
			// Only initially, pass geometry first to start B and wait for C's first raw input before reserving.
			this.initialFootnotePagePending = true;
			this.pageGenerator.pageStarted(this.pageBox, innerWidth, innerHeight);
			return;
		}
		this.reserveBottomFootnotes();
		this.pageGenerator.pageStarted(this.pageBox, innerWidth, innerHeight);
	}

	public void startFootnoteInput() {
		if (!this.initialFootnotePagePending) return;
		this.initialFootnotePagePending = false;
		this.reserveBottomFootnotes();
	}

	private void reserveBottomFootnotes() {
		if (this.footnoteArea().isHeightFixed()) {
			final double maxArea = Math.max(0, this.pageBox.getInnerHeight()) * MAX_FOOT_AREA_RATIO;
			this.reservePageBand(this.requestedFootnoteArea(maxArea));
			this.reserveFixedFootnotes();
			return;
		}
		this.footnotePlan.clear();
		this.pageFootnoteHost.footnoteReservedCount = 0;
		final boolean planned = this.hasFootnotePlan();
		final double maxArea = Math.max(0, this.pageBox.getInnerLineExtent(this.pageBox.getBlockParams().flow))
				* MAX_FOOT_AREA_RATIO;
		double inset = 0;
		boolean blocked = false;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
				blocked = true;
				break;
			}
			final double height = entry.noteBox == null ? entry.measuredHeight : footnoteBandExtent(entry.noteBox);
			if (!Double.isFinite(height)) {
				blocked = true;
				break;
			}
			final double cost = (this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + height;
			if (inset + cost > maxArea) {
				// Oversized notes wait for call finalization; at the carry-over head, reserve up to the limit and allow overflow.
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					inset = maxArea;
					this.pageFootnoteHost.footnoteReservedCount = 1;
					if (planned) this.footnotePlan.put(entry.id, new FootnoteReservation(height, true));
				}
				blocked = true;
				break;
			}
			inset += cost;
			++this.pageFootnoteHost.footnoteReservedCount;
			if (planned) this.footnotePlan.put(entry.id, new FootnoteReservation(height, false));
		}
		final double minimum = this.requestedFootnoteArea(maxArea);
		if (planned) {
			final var report = this.pageGenerator.getFootnotePageProbeReport(this.pageGeneration);
			// Fix using only carry-over both before finalization and after B ends normally. Late reports do not update H.
			final boolean finished = this.pageGenerator.isFootnotePageProbeFinished();
			final boolean usable = report != null && report.generation() == this.pageGeneration && report.emitted()
					&& java.util.Objects.equals(report.pageName(), this.pageGenerator.getPageName())
					&& report.flow() == this.pageBox.getBlockParams().flow
					&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(report.innerWidth(), this.pageBox.getInnerWidth()) == 0
					&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(report.innerHeight(), this.pageBox.getInnerHeight()) == 0;
			if (!blocked && usable) {
				for (final long id : new java.util.TreeSet<>(report.callIds())) {
					if (this.registeredFootnotes.contains(id) || this.bottomFootnotes.containsKey(id)
							|| this.footnotePlan.containsKey(id)) continue;
					final Double height = report.measuredHeights().get(id);
					if (height == null || report.unmeasuredIds().contains(id) || !Double.isFinite(height) || height < 0) continue;
					final double cost = (this.footnotePlan.isEmpty() ? FOOTNOTE_GAP : 0) + height;
					if (inset + cost > maxArea) break;
					inset += cost;
					this.bottomFootnote(id).measuredHeight = height;
					this.footnotePlan.put(id, new FootnoteReservation(height, false));
				}
			}
			if (minimum > 0) inset = Math.max(minimum, inset);
			this.updateFootnotePrefix();
			final var observer = footnotePlanObserver;
			if (observer != null) observer.accept(new FootnotePlanSnapshot(this.pageGeneration, report != null, usable, finished,
					inset, java.util.Set.copyOf(this.footnotePlan.keySet())));
		}
		this.reservePageBand(minimum > 0 ? Math.max(minimum, inset) : inset);
	}

	/**
	 * Reserves a band at a paper edge. Bottom bands only reduce type-area height; top bands also lower the body
	 * content origin by the band extent ({@code PageBox.reserveHeadArea}).
	 */
	private void reservePageBand(final double inset) {
		if (this.footnoteArea().isHeadBand()) {
			this.pageBox.reserveHeadArea(inset);
		} else {
			this.pageBox.reserveFootArea(inset);
		}
	}

	/** Band reservation extent, abstracting whether the space is taken at the top or bottom. */
	private double pageBandInset() {
		return this.footnoteArea().isHeadBand() ? this.pageBox.getHeadInset() : this.pageBox.getFootInset();
	}

	/** Keeps the fixed band size and grants reservation eligibility only to completed notes, in FIFO order. */
	private void reserveFixedFootnotes() {
		this.footnotePlan.clear();
		this.pageFootnoteHost.footnoteReservedCount = 0;
		// Notes in a vertical band stack horizontally across the paper; use that axis for capacity and measurement.
		// The gap from body text lies in the paper's vertical direction; do not subtract it from horizontal capacity.
		final boolean verticalBand = this.isVerticalFootnoteBand();
		final double capacity = this.isPageBandFootnoteArea() ? this.footnoteBandCapacity() : this.pageFootnoteHost.footnoteReservation;
		double used = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.noteBox == null || (entry.deferred && !entry.committed && !this.forceFootnoteAttach)) break;
			final double extent = this.isPageBandFootnoteArea() ? this.footnoteBandCost(entry.noteBox) : this.footnoteExtent(entry.noteBox);
			final double cost = (!verticalBand && this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + extent;
			if (used + cost > capacity) {
				// For notes that cannot fit even alone, finalize the call first, then allow overflow on the next page.
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					this.footnotePlan.put(entry.id, new FootnoteReservation(extent, true));
					this.pageFootnoteHost.footnoteReservedCount = 1;
					if (!this.warnedOversizedFootnote) {
						this.warnedOversizedFootnote = true;
						LOG.warning("footnote larger than the fixed band; placing it anyway: " + extent + "pt");
					}
				}
				break;
			}
			used += cost;
			this.footnotePlan.put(entry.id, new FootnoteReservation(extent, false));
			++this.pageFootnoteHost.footnoteReservedCount;
		}
	}

	/** Passes tests only the plan fixed at the start, never a mutable ledger or page tree. */
	public record FootnotePlanSnapshot(long generation, boolean reported, boolean usable, boolean inputFinished,
			double inset, java.util.Set<Long> reservedIds) { }
	static volatile java.util.function.Consumer<FootnotePlanSnapshot> footnotePlanObserver;

	private void updateFootnotePrefix() {
		this.pageFootnoteHost.footnoteReservedCount = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.noteBox == null || !this.footnotePlan.containsKey(entry.id)) break;
			++this.pageFootnoteHost.footnoteReservedCount;
		}
	}

	/**
	 * Adds a completed footnote body to the ledger (from the FLOAT branch of {@code DocumentBuilder.endBox}).
	 * Reservations grow only as far as the current page's capacity permits, reducing body capacity ({@link
	 * #getPageLimit()}) so subsequent overflow checks and page breaks use the new capacity. Excess is not reserved and
	 * moves to the next page (F4).
	 */
	public void addFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
		this.addFootnote(noteBox, this, footnoteColumnOwner(this));
	}

	public void addFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox,
			final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (!this.registeredFootnotes.add(noteBox.getParams().footnoteId)) {
			// The same note arrived twice (from both two-pass recording and source replay).
			// Keep only one ledger entry.
			return;
		}
		if (this.isFootnoteProbe()) {
			if (this.probeFootnoteAnchors == null) this.probeFootnoteAnchors = new java.util.HashMap<>();
			this.probeFootnoteAnchors.put(noteBox.getParams().footnoteId, noteBox.getSourceAnchor());
		}
		if (this.footnoteArea().isHeightFixed()) {
			// Retain the call-page number even if the call crossed a page break before its body.
			this.bottomFootnote(noteBox.getParams().footnoteId).noteBox = noteBox;
			this.reserveFixedFootnotes();
			return;
		}
		if (this.isPageBandFootnoteArea()) {
			final double noteExtent = this.footnoteBandExtent(noteBox);
			final double maxArea = Math.max(0, this.pageBox.getInnerHeight() + this.pageBandInset())
					* MAX_FOOT_AREA_RATIO;
			if (FOOTNOTE_GAP + noteExtent > maxArea && !this.warnedOversizedFootnote) {
				this.warnedOversizedFootnote = true;
				LOG.warning("footnote larger than the bottom band; placing it anyway: " + noteExtent
						+ "pt (max footnote area " + maxArea + "pt)");
			}
			if (this.hasFootnotePlan()) {
				final FootnoteEntry entry = this.bottomFootnote(noteBox.getParams().footnoteId);
				entry.noteBox = noteBox;
				entry.measuredHeight = noteExtent;
				final FootnoteReservation reservation = this.footnotePlan.get(entry.id);
				if (reservation != null && !reservation.oversized() && net.zamasoft.foliojet.layout.util.LayoutUtils.compare(noteExtent, reservation.height()) > 0) {
					// Do not change H. Actual-height overflow only removes reservation eligibility and goes to F4.
					this.footnotePlan.remove(entry.id);
				}
				this.updateFootnotePrefix();
				return;
			}
			final FootnoteEntry entry = new FootnoteEntry(noteBox.getParams().footnoteId, noteBox);
			if (this.probeCallNumbers != null) {
				final Integer number = this.probeCallNumbers.remove(entry.id);
				if (number != null) {
					entry.committed = true;
					entry.assignedNumber = number;
				}
			}
			this.pageFootnoteHost.pendingFootnotes.addLast(entry);
			return;
		}
		if (this.columnFootnoteHost == null) this.flushColumnFootnoteCarry();
		final FootnoteHost host = this.selectFootnoteHost(parent, owner);
		final double noteExtent = this.footnoteExtent(noteBox);
		final double maxArea = host.capacityBase.getAsDouble() - MIN_PAGE_LIMIT;
		if (FOOTNOTE_GAP + noteExtent > maxArea && !this.warnedOversizedFootnote) {
			// A footnote that cannot fit even in an empty page's maximum footnote area
			// (one note occupying over 90% of the type area). **Do not fail conversion**:
			// ARCHITECTURE.md §5.13 (user decision on 2026-07-26/27) states that conversion failure
			// is always an engine defect, and excluding documents with broken layout
			// does not apply to conversion failures. Place with overflow and warn
			// (2026-08-02; previously threw FootnoteOverflowException).
			this.warnedOversizedFootnote = true;
			LOG.warning("footnote larger than the page area; placing it anyway: " + noteExtent
					+ "pt (max footnote area " + maxArea + "pt)");
		}
		final FootnoteEntry entry = new FootnoteEntry(noteBox.getParams().footnoteId, noteBox);
		host.pendingFootnotes.addLast(entry);
		this.traceFootnote("arrival", entry, 0, java.util.Set.of());
		if (host == this.pageFootnoteHost) {
			this.reserveFootnotes();
		} else {
			this.columnPageEntries.put(entry.id, entry);
			this.reserveColumnFootnotes(host);
		}
	}

	/**
	 * Extends reservations through the leading pending prefix that fits the current page's maximum footnote area
	 * (FIFO; never skip entries). New reservations use only space after the end of placed atomic floats; existing
	 * reservations never shrink later (2026-09-04).
	 */
	private void reserveFootnotes() {
		if (this.footnoteArea().isHeightFixed()) {
			this.reserveFixedFootnotes();
			return;
		}
		if (this.isPageBandFootnoteArea()) {
			// The bottom band is fixed in beginPage. Do not reserve notes arriving midway through a page.
			return;
		}
		if (this.footnoteArea().minHeight > 0) {
			this.reserveMinimumFootnotes();
			return;
		}
		final double previousReservation = this.pageFootnoteHost.footnoteReservation;
		final double maxArea = this.pageFootnoteHost.capacityBase.getAsDouble() - Math.max(MIN_PAGE_LIMIT, this.atomicFloatFloor);
		int i = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (i >= this.pageFootnoteHost.footnoteReservedCount) {
				if (entry.holdReservationUntil >= this.pageGeneration) break;
				if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
					// Note awaiting its call with its reservation removed (FIFO makes later notes wait too).
					this.traceFootnote("reserve-stop-deferred", entry, 0, java.util.Set.of());
					break;
				}
				final double cost = (this.pageFootnoteHost.footnoteReservation == 0 ? FOOTNOTE_GAP : 0)
						+ this.footnoteExtent(entry.noteBox);
				if (this.pageFootnoteHost.footnoteReservation + cost > maxArea) {
					// For a footnote that cannot fit even alone on its call page, do not
					// reserve the maximum extent there. That would shrink body capacity to MIN_PAGE_LIMIT
					// and repeatedly send pre-call content (especially empty multi-column frames)
					// forward unchanged across hundreds of pages (seed 7676). First finalize the call
					// on the current page and mark committed, then place the note with overflow
					// on the next note-only page. Already carried-in notes always reserve via the existing path below.
					if (this.pageFootnoteHost.footnoteReservedCount == 0 && i == 0 && !entry.committed
							&& !this.forceFootnoteAttach) {
						this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
						break;
					}
					// **Always reserve at least the first entry** (2026-08-02).
					// A footnote larger than the type area never fits, however many pages it is sent forward,
					// so giving up here prevents progress and fails conversion (violating §5.13).
					// Cap the reservation at the type area limit and place the actual note with overflow.
					if (this.pageFootnoteHost.footnoteReservedCount == 0 && i == 0) {
						final double before = this.pageFootnoteHost.footnoteReservation;
						this.pageFootnoteHost.footnoteReservation = maxArea;
						this.pageFootnoteHost.footnoteReservedCount = 1;
						this.traceFootnote("reserve-oversized", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
					}
					// Send the excess forward in F4 FIFO order (re-reserve on the next page).
					this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
					break;
				}
				this.pageFootnoteHost.footnoteReservation += cost;
				++this.pageFootnoteHost.footnoteReservedCount;
				this.traceFootnote("reserve", entry, cost, java.util.Set.of());
			}
			++i;
		}
		if (this.pageFootnoteHost.footnoteReservation != previousReservation) {
			this.footnoteReservationChangedAfterBottomRegistration();
		}
	}

	/** Uses the minimum reservation first, extending to the existing capacity only when needed. */
	private void reserveMinimumFootnotes() {
		final double previousReservation = this.pageFootnoteHost.footnoteReservation;
		final double maxArea = Math.max(0, this.blockFootnoteMaxArea()
				- Math.max(0, this.atomicFloatFloor - MIN_PAGE_LIMIT));
		int i = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (i++ < this.pageFootnoteHost.footnoteReservedCount) continue;
			if (entry.holdReservationUntil >= this.pageGeneration) break;
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
				this.traceFootnote("reserve-stop-deferred", entry, 0, java.util.Set.of());
				break;
			}
			final double before = this.pageFootnoteHost.footnoteReservation;
			final double cost = (this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + this.footnoteExtent(entry.noteBox);
			if (this.pageFootnoteHost.footnoteUsed + cost > maxArea) {
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					this.pageFootnoteHost.footnoteReservation = Math.max(this.pageFootnoteHost.footnoteReservation, maxArea);
					this.pageFootnoteHost.footnoteUsed = maxArea;
					this.pageFootnoteHost.footnoteReservedCount = 1;
					this.traceFootnote("reserve-oversized", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
				}
				this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
				break;
			}
			this.pageFootnoteHost.footnoteUsed += cost;
			this.pageFootnoteHost.footnoteReservation = Math.max(this.pageFootnoteHost.footnoteReservation, this.pageFootnoteHost.footnoteUsed);
			++this.pageFootnoteHost.footnoteReservedCount;
			this.traceFootnote("reserve", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
		}
		if (this.pageFootnoteHost.footnoteReservation != previousReservation) this.footnoteReservationChangedAfterBottomRegistration();
	}

	@Override
	public double getPageLimit() {
		final double pageLimit = this.getPageOwnerLimit();
		return this.columnFootnoteHost == null || this.columnFootnoteHost.footnoteReservation == 0 ? pageLimit
				: pageLimit - this.columnFootnoteHost.footnoteReservation;
	}

	/**
	 * Includes only page-owned reservations. Bottom one-dimensional reservation, lower bounds, and operation order
	 * remain unchanged.
	 *
	 * <p>
	 * Reserve bottom floats one-dimensionally inside multi-column layout (2026-10-05, jigensha report 4). This cuts
	 * column fragments before the placement area, preventing column rules and equalized heights from extending over
	 * figures. Lines already avoided the area with two-dimensional exclusion, but fragments extended to the page
	 * bottom (the band for unsplittable floats is also unused in multi-column layout; {@link
	 * #hasTwoDimensionalBottomFloatLimit}).
	 * </p>
	 */
	@Override
	public double getPageOwnerLimit() {
		final double base = super.getPageLimit();
		// Scan multi-column layout only on pages with reserved bottom floats (avoid tracing deeply nested stacks every time).
		final double reserved = this.pageFootnoteHost.footnoteReservation + (this.bottomFloatReservation != 0
				&& (this.bottomFloatOneDimensionalFallback || this.getMulticolumnBox() != null)
						? this.bottomFloatReservation
						: 0);
		if (reserved == 0) {
			return base;
		}
		return Math.max(MIN_PAGE_LIMIT, base - reserved);
	}

	@Override
	protected double getUnsplittableFloatPageLimit() {
		final double pageLimit = this.getPageLimit();
		if (!this.hasTwoDimensionalBottomFloatLimit()) {
			return pageLimit;
		}
		// Two-dimensional bottom floats do not reduce body capacity, but atomic floats cannot split midway.
		// If they enter the reserved band at all, use the first bottom float's actual placement start as the end.
		return Math.min(pageLimit, this.firstReservedBottomPlacedStart());
	}

	/** True when the 2-D bottom band serves as the effective end for unsplittable floats. */
	final boolean hasTwoDimensionalBottomFloatLimit() {
		return !this.bottomFloatOneDimensionalFallback && this.bottomFloatReservedCount > 0
				&& this.getMulticolumnBox() == null && this.hasRootWritingModePath();
	}

	// ------------------------------------------------------------------
	// Page floats (float: top / float: bottom, 2026-08-02; priority 1 in PLAN §2).
	// Move figures and tables to page edges in book typesetting. Reuse the footnote
	// reservation and settlement mechanism unchanged.

	/**
	 * Queue for placement at the top. If the page has no body text or placed objects yet, place immediately in the
	 * current PageBox; otherwise, place at <b>the start of the next page</b> (2026-09-04, B-1). Do not rebuild already
	 * laid-out content on the current page.
	 */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> pendingTopFloats =
			new java.util.ArrayDeque<>();

	/** Queue for placement at the type area bottom (above footnotes, if any). */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> pendingBottomFloats =
			new java.util.ArrayDeque<>();

	/** Extent reserved for bottom floats on the current page. */
	private double bottomFloatReservation = 0;

	/** Number of bottom floats reserved on the current page (FIFO prefix length). */
	private int bottomFloatReservedCount = 0;

	/**
	 * True when bottom floats on this page use the existing one-dimensional reservation.
	 *
	 * <p>
	 * Bottom floats are actually placed at page block-end, so overlap with already placed lines cannot be resolved
	 * retroactively only when the current position at registration has passed the first bottom float's actual
	 * placement start, {@code placedStart}. Only then revert the rest of this page to shrinking {@link
	 * #getPageLimit()}. If the current position is at or before {@code placedStart}, use two-dimensional exclusion
	 * even with existing body text. If footnote reservations grow after two-dimensional registration, apply the same
	 * check to the new, shifted {@code placedStart}. Reset on the next PageBox; carried-in bottom floats register
	 * two-dimensionally before body text. At page start there is no placed range yet, so exclude the whole area
	 * two-dimensionally even when an oversized bottom float's {@code placedStart} is negative.
	 * </p>
	 */
	private boolean bottomFloatOneDimensionalFallback = false;

	/**
	 * True once a bottom float has been sent to the next page from this page (2026-10-04, TECH-20261003-004 item ⑱).
	 * FIFO means later bottom floats also receive no reservation on this page.
	 */
	private boolean bottomFloatsDeferredOnPage = false;

	// ------------------------------------------------------------------
	// JLREQ 4.2.7 parallel notes (sidenotes in horizontal writing; headnotes/footnotes in vertical writing).
	// Standard CSS has no corresponding declaration, so float:-cssj-note-start/end places them outside
	// the type area in the logical line direction. The author reserves body space with @page margin.

	/** Default gap between the type area and parallel notes. */
	private static final double PAGE_MARGIN_NOTE_GAP = 6.0;

	/** Page-axis position for the next note in each note area on this page. */
	private double pageMarginNoteStartCursor = 0, pageMarginNoteEndCursor = 0;

	/** Depth of body binding before deferred placement of a row subgrid. */
	private int rowSubgridBindDepth = 0;

	void beginRowSubgridBind() {
		++this.rowSubgridBindDepth;
	}

	void endRowSubgridBind() {
		if (this.rowSubgridBindDepth <= 0) {
			throw new IllegalStateException("row subgrid bind scopeの不整合");
		}
		--this.rowSubgridBindDepth;
	}

	private void resetPageMarginNoteCursors() {
		this.pageMarginNoteStartCursor = 0;
		this.pageMarginNoteEndCursor = 0;
	}

	/**
	 * Places parallel notes outside the type area near the current body position. Notes on the same side follow FIFO
	 * without overlap; shift them upward to keep them on the same page if they fit at the page end.
	 */
	public void addPageMarginNote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox,
			final boolean start) {
		if (this.rowSubgridBindDepth > 0) {
			final String detailKey = "2823.subgrid-rows-margin-note";
			final net.zamasoft.foliojet.ua.UserAgent ua = this.pageGenerator.getUserAgent();
			if (ua.getUAContext().getReportedIneffectiveCombinationDetails().add(detailKey)) {
				ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
						"float", net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
			}
		}
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		final double pageLimit = super.getPageLimit();
		final double extent = this.footnoteExtent(noteBox);
		final double cursor = start ? this.pageMarginNoteStartCursor : this.pageMarginNoteEndCursor;
		double pageAxis = Math.max(cursor, Math.max(0, Math.min(this.pageAxis, pageLimit)));
		if (extent <= pageLimit && pageAxis + extent > pageLimit) {
			// Move back inside the page without straying farther than necessary from the corresponding body position.
			pageAxis = Math.max(cursor, pageLimit - extent);
		}
		final double lineAxis = start
				? -PAGE_MARGIN_NOTE_GAP - noteBox.getLineExtent(flow)
				: this.pageBox.getLineSize() + PAGE_MARGIN_NOTE_GAP;
		this.pageBox.getContainer().addFloating(noteBox, lineAxis, pageAxis);
		final double next = pageAxis + extent + PAGE_MARGIN_NOTE_GAP;
		if (start) {
			this.pageMarginNoteStartCursor = next;
		} else {
			this.pageMarginNoteEndCursor = next;
		}
	}

	/**
	 * Adds a page float to the ledger (from the end of FLOAT in {@code DocumentBuilder}).
	 */
	public void addPageFloat(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox,
			final boolean top) {
		if (top) {
			// TwoPass/continuation replay passes here again. Do not enqueue the same box twice
			// if it is pending or already placed on this page.
			final Long placedGeneration = this.placedTopFloatGenerations.get(floatBox);
			if (this.pendingTopFloatGenerations.containsKey(floatBox)
					|| (placedGeneration != null && placedGeneration.longValue() == this.pageGeneration)) {
				return;
			}
			final boolean placeOnCurrentPage = this.isCurrentPageEmptyForTopFloat();
			this.pendingTopFloats.addLast(floatBox);
			this.pendingTopFloatGenerations.put(floatBox, this.pageGeneration);
			if (placeOnCurrentPage) {
				this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
						super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
			} else {
				this.pendingCurrentTopFloats.add(new CurrentTopFloat(floatBox, this.pageGeneration));
				this.tryTranslateForTopFloats();
			}
		} else {
			final Long placedGeneration = this.placedBottomFloatGenerations.get(floatBox);
			if (this.pendingBottomFloatGenerations.containsKey(floatBox)
					|| (placedGeneration != null && placedGeneration.longValue() == this.pageGeneration)) {
				return;
			}
			this.pendingBottomFloats.addLast(floatBox);
			this.pendingBottomFloatGenerations.put(floatBox, this.pageGeneration);
			final int reservedBefore = this.bottomFloatReservedCount;
			final double reservationBefore = this.bottomFloatReservation;
			this.reserveBottomFloats();
			if (this.bottomFloatReservedCount > reservedBefore && (this.currentPositionPastFirstReservedBottom()
					|| this.earlierColumnPastFirstReservedBottom())) {
				// Body text has already reached the placement band. Reserving here would shrink the remaining page
				// one-dimensionally, pushing body text below the placement area (including lines before the anchor)
				// onto the next page and leaving only the float at the bottom of the page before its anchor
				// (⑱; in chapter 1 of 時限暗号, an illustration appeared on the page before its section heading).
				// Do not reserve on this page; send it to the next page's bottom (carry-in reserves before body text).
				// When registered in a later column, earlier columns' lines are already laid out (in the same book's
				// two-column layout, an illustration overlapped the bottom few lines of the left column).
				this.bottomFloatReservedCount = reservedBefore;
				this.bottomFloatReservation = reservationBefore;
				this.bottomFloatsDeferredOnPage = true;
				this.rebuildBottomPageFloatExclusions();
			}
			this.updateBottomFloatFallbackForCurrentPosition();
		}
	}

	/** Whether page floats remain unplaced in the type area (condition driving finish()). */
	private boolean hasPendingPageFloats() {
		return !this.pendingTopFloats.isEmpty() || !this.pendingBottomFloats.isEmpty();
	}

	/** Whether a top float registered on this page is still eligible for placement at its top. */
	public final boolean hasCurrentTopFloats() {
		for (final CurrentTopFloat entry : this.pendingCurrentTopFloats) {
			if (entry.generation() == this.pageGeneration) {
				return true;
			}
		}
		return false;
	}

	/**
	 * At flow-block end, after child scopes and break-prohibition depth unwind, attempts translation to the current
	 * page top before interflow overflow checks.
	 */
	@Override
	protected void afterFlowBlockClosed() {
		this.tryTranslateForTopFloats();
	}

	/**
	 * Places full-line-width top floats registered on this page at its top, as far as they fit together with placed
	 * content. Completes all checks before mutation; if conditions fail, carries the queue unchanged to the next page.
	 */
	public final void tryTranslateForTopFloats() {
		if (!this.hasCurrentTopFloats() || !this.canTranslateNow()) {
			return;
		}

		final double used = this.currentTranslateUsedPageEnd();
		final double limit = this.getUnsplittableFloatPageLimit();
		final double maxArea = limit - (used - this.topPageFloatStackEnd);
		final TopFloatPlan plan = this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
				maxArea, false);
		if (plan.boxes.isEmpty()) {
			this.logTranslateSkip("capacity: used=" + used + " limit=" + limit);
			return;
		}
		if (!this.hasFullWidthTopFloatPlan(plan)) {
			return;
		}
		this.translateForTopFloats(plan);
	}

	/** Determines, without mutation, whether Root is at a quiescent point where translation is safe. */
	private boolean canTranslateNow() {
		if (this.textBuilder != null) {
			return this.logTranslateSkip("text builder is open");
		}
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			return this.logTranslateSkip("no-break scope: mode=" + this.mode + " breakDepth=" + this.breakDepth);
		}
		if (this.breakAfter != null) {
			// The preceding block's page-break-after is pending: this float belongs to the next page.
			return this.logTranslateSkip("forced break pending: " + this.breakAfter);
		}
		if (this.isRestyling()) {
			return this.logTranslateSkip("restyling");
		}
		if (!this.sessions.isEmpty()) {
			return this.logTranslateSkip("resume session");
		}
		if (!this.resumeScopes.isEmpty()) {
			return this.logTranslateSkip("resume scope");
		}
		if (this.rowSubgridBindDepth != 0) {
			return this.logTranslateSkip("row subgrid bind");
		}
		if (this.translateBlockDepth != 0) {
			return this.logTranslateSkip("child builder/replay scope depth=" + this.translateBlockDepth);
		}
		if (this.findColumnBreak() != null) {
			return this.logTranslateSkip("column break path");
		}
		if (this.getMulticolumnBox() != null) {
			return this.logTranslateSkip("multicolumn flow");
		}
		if (!this.hasRootWritingModePath()) {
			return this.logTranslateSkip("mixed writing-mode path");
		}
		if (this.pageFinished) {
			return this.logTranslateSkip("page already finished");
		}
		return true;
	}

	/** Logs the fallback reason at FINE and returns false for direct use in a condition. */
	private boolean logTranslateSkip(final String reason) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("top float translate skipped: " + reason);
		}
		return false;
	}

	/**
	 * Page-axis end that must be occupied after adding top floats. Includes end frames of open flows,
	 * ordinary/independent-BFC floats, unsplittable floats, and placed parallel notes.
	 */
	private double currentTranslateUsedPageEnd() {
		final double normalEnd = this.pageAxis - (this.poLastMargin + this.neLastMargin);
		double used = Math.max(normalEnd, this.maxActiveFloatingPageEnd());
		if (this.atomicFloatFloor > 0) {
			used = Math.max(used, this.atomicFloatFloor);
		}
		final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer =
				(net.zamasoft.foliojet.layout.box.content.FlowContainer) this.pageBox.getContainer();
		used = Math.max(used,
				pageContainer.maxPageMarginNotePageEnd(this.pageBox.getBlockParams().flow));
		if (this.hasOpenFlow()) {
			used = Math.max(used, normalEnd + this.lastFrame(this.getFlow(), 1));
		}
		return used;
	}

	/**
	 * True if already placed top floats all span the full line width and have no shape.
	 *
	 * <p>
	 * New top floats may have any width: translation shifts existing content by the figure's block size, so narrow
	 * figures occupy a band with no text wrapping beside them (css-page-floats §3: content flows toward block-end).
	 * This is an intentional approximation distinct from two-dimensional exclusion at page start. Most real cases,
	 * such as vertical-writing photos on cti.li, contain figures shorter than the page height; restricting to full
	 * line width would not address the motivating examples (2026-09-05). Existing top floats may have text beside
	 * them, so allow only full-line-width ones (otherwise shifted lines overlap the new band).
	 * </p>
	 */
	private boolean hasFullWidthTopFloatPlan(final TopFloatPlan plan) {
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box : plan.boxes) {
			if (box.getFloatPos().shapeOutside != null) {
				return this.logTranslateSkip("shape-outside top float");
			}
		}
		if (this.narrowTopPlacedWithTextBeside) {
			return this.logTranslateSkip("partial-width top placed at page start (text may sit beside it)");
		}
		return true;
	}

	/** Places planned top floats and translates existing page-local state by the same extent. */
	private void translateForTopFloats(final TopFloatPlan plan) {
		final int flowDepth = this.flowStack == null ? 0 : this.flowStack.size();
		this.placingTopByTranslate = true;
		try {
			this.placeTopPageFloats(plan);
		} finally {
			this.placingTopByTranslate = false;
		}
		final double dy = plan.dy;
		final java.util.Set<net.zamasoft.foliojet.layout.box.IBox> keep = java.util.Collections
				.newSetFromMap(new java.util.IdentityHashMap<>());
		keep.addAll(this.placedTopFloatGenerations.keySet());
		final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer =
				(net.zamasoft.foliojet.layout.box.content.FlowContainer) this.pageBox.getContainer();
		pageContainer.shiftPageAxis(dy, keep);
		this.shiftFlowStack(dy);
		this.shiftFloatLedgers(dy);
		if (this.atomicFloatFloor > 0) {
			this.atomicFloatFloor += dy;
		}
		if (this.pageMarginNoteStartCursor > 0) {
			this.pageMarginNoteStartCursor += dy;
		}
		if (this.pageMarginNoteEndCursor > 0) {
			this.pageMarginNoteEndCursor += dy;
		}
		this.pageBox.setPageAxis(this.maxNormalFlowPageEnd(pageContainer));
		this.clearBreakProgressHistory();

		final java.util.Set<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> placed = java.util.Collections
				.newSetFromMap(new java.util.IdentityHashMap<>());
		placed.addAll(plan.boxes);
		this.pendingCurrentTopFloats.removeIf(entry -> placed.contains(entry.box()));
		assert flowDepth == (this.flowStack == null ? 0 : this.flowStack.size())
				: "top float translate changed flowStack depth";
	}

	/** Normal-flow end indicated by the shifted cursor, end frames, and flows directly under PageBox. */
	private double maxNormalFlowPageEnd(
			final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer) {
		double pageEnd = this.pageAxis - (this.poLastMargin + this.neLastMargin);
		if (this.hasOpenFlow()) {
			pageEnd += this.lastFrame(this.getFlow(), 1);
		}
		return Math.max(pageEnd, pageContainer.maxNormalFlowPageEnd(this.pageBox.getBlockParams().flow));
	}

	/**
	 * Extends bottom-float reservations through the leading pending prefix that fits this page (same FIFO as
	 * footnotes; never skip entries). On pages supporting two-dimensional exclusion, use reserved extent only for the
	 * placement plan, without shrinking body capacity. Normally reserve the first entry even if larger than the type
	 * area, so EOF draining advances. On pages with an atomic floor, however, send even the first entry to the next
	 * page if it violates that floor (2026-09-04). Progress is preserved because the next page reserves it before
	 * registering the floor.
	 */
	private void reserveBottomFloats() {
		final double bottomMaxArea = super.getPageLimit() - Math.max(MIN_PAGE_LIMIT, this.atomicFloatFloor)
				- this.pageFootnoteHost.footnoteReservation;
		int i = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
			if (i++ < this.bottomFloatReservedCount) {
				// Do not remove the FIFO prefix already reserved on this page, even for later floors or footnotes.
				continue;
			}
			if (this.bottomFloatsDeferredOnPage) {
				// Do not reserve anything after a bottom float sent to the next page on this page (FIFO).
				break;
			}
			final double cost = this.footnoteExtent(floatBox);
			if (this.bottomFloatReservation + cost > bottomMaxArea) {
				if (this.bottomFloatReservedCount == 0 && i == 1 && this.atomicFloatFloor <= 0) {
					this.bottomFloatReservation = cost;
					this.bottomFloatReservedCount = 1;
					LOG.warning("bottom page float too large for the remaining page area: " + cost + "pt");
				}
				break;
			}
			this.bottomFloatReservation += cost;
			++this.bottomFloatReservedCount;
		}
		this.rebuildBottomPageFloatExclusions();
	}

	/** Current position farther toward block-end of the body cursor and the open line's actual extent. */
	private double currentPagePosition() {
		return this.textBuilder == null ? this.pageAxis
				: Math.max(this.pageAxis, this.textBuilder.getActualPageAxis());
	}

	/**
	 * Whether there is no body text or placed object, allowing B-1 to place top floats directly in this PageBox
	 * (2026-09-04).
	 */
	private boolean isCurrentPageEmptyForTopFloat() {
		return this.textBuilder == null
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(this.currentPagePosition(), 0) == 0
				// Exclude top floats already placed immediately from the empty check. Exclude only box identities
				// in the typed placement-generation ledger, never ordinary floats or body text.
				&& !this.pageBox.getContainer().hasNonDecorationContentExcludingFloatings(
						this.placedTopFloatGenerations.keySet());
	}

	/** Actual block-axis start of the first currently reserved bottom float. */
	private double firstReservedBottomPlacedStart() {
		return super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation;
	}

	/** Whether placed content reaches the actual placement band of the first currently reserved bottom float. */
	private boolean currentPositionPastFirstReservedBottom() {
		if (this.bottomFloatReservedCount == 0) {
			return false;
		}
		final double currentPosition = this.currentPagePosition();
		// An oversized bottom float can have a negative placedStart, but page start has no
		// already placed range to roll back, so two-dimensional exclusion is available.
		return net.zamasoft.foliojet.layout.util.LayoutUtils.compare(currentPosition, 0) > 0
				&& net.zamasoft.foliojet.layout.util.LayoutUtils
						.compare(currentPosition, this.firstReservedBottomPlacedStart()) > 0;
	}

	/**
	 * Whether body text in earlier columns on this page reaches the first reserved bottom float's actual placement
	 * band.
	 */
	private boolean earlierColumnPastFirstReservedBottom() {
		return this.bottomFloatReservedCount > 0 && net.zamasoft.foliojet.layout.util.LayoutUtils
				.compare(this.committedColumnsEndOnPage, this.firstReservedBottomPlacedStart()) > 0;
	}

	@Override
	public void addTable(final net.zamasoft.foliojet.layout.builder.RetainedTable tableBuilder) {
		final double start = this.pageAxis;
		super.addTable(tableBuilder);
		this.exclusionBlindBoxPlaced(start);
	}

	@Override
	public void addGrid(final net.zamasoft.foliojet.layout.builder.RetainedGrid gridBuilder) {
		final double start = this.getFlow().pageAxis;
		final int narrowings = this.pageFloatNarrowings;
		super.addGrid(gridBuilder);
		if (this.pageFloatNarrowings == narrowings) {
			this.exclusionBlindBoxPlaced(start);
		}
	}

	@Override
	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		final double start = this.getFlow().pageAxis;
		final int narrowings = this.pageFloatNarrowings;
		super.addFlex(flexBuilder);
		if (this.pageFloatNarrowings == narrowings) {
			this.exclusionBlindBoxPlaced(start);
		}
	}

	/**
	 * Called immediately after placing a table, grid, or flex (2026-10-05). Lines in their cells/items use another
	 * builder and do not see page exclusions, so text overlapped figures when a box entered a bottom float's placement
	 * area (the speech-balloon grid after a figure in jigensha's vertical-writing book). If a box starts before the
	 * placement area and reaches it, switch this page to one-dimensional reservation and split before the area
	 * (remainder on the next page). Content preceding the box ends before its start and is not pushed out. A grid/flex
	 * starting inside the placement area already has its width reduced by the figure at its start ({@code
	 * BlockBuilder.startFlowBlock}).
	 *
	 * @param start box start (page block axis)
	 */
	private void exclusionBlindBoxPlaced(final double start) {
		if (!this.bottomFloatOneDimensionalFallback && this.hasRootWritingModePath()
				&& this.currentPositionPastFirstReservedBottom() && net.zamasoft.foliojet.layout.util.LayoutUtils
						.compare(start, this.firstReservedBottomPlacedStart()) < 0) {
			this.bottomFloatOneDimensionalFallback = true;
			this.rebuildBottomPageFloatExclusions();
		}
	}

	/** Reselects this page's path from the current placed range and the first bottom float's placedStart. */
	private void updateBottomFloatFallbackForCurrentPosition() {
		final boolean fallback = this.currentPositionPastFirstReservedBottom();
		if (this.bottomFloatOneDimensionalFallback != fallback) {
			this.bottomFloatOneDimensionalFallback = fallback;
			this.rebuildBottomPageFloatExclusions();
		}
	}

	/**
	 * Updates actual placement rectangles when footnote reservations grow after bottom-float registration. Reverts to
	 * one-dimensional handling only if the current position reaches the new first rectangle after movement; otherwise,
	 * rebuilds two-dimensional exclusion with the new rectangles.
	 */
	private void footnoteReservationChangedAfterBottomRegistration() {
		if (this.bottomFloatReservedCount == 0) {
			return;
		}
		this.reserveBottomFloats();
		this.updateBottomFloatFallbackForCurrentPosition();
	}

	/**
	 * Reregisters the currently reserved bottom prefix at its actual placement {@code [placedStart, placedEnd]}, using
	 * the current footnote reservation.
	 *
	 * <p>
	 * Drawing uses logical {@code lineAxis=0}: bottom left in horizontal-tb, top left in vertical-rl (right of
	 * footnotes, if any), and top right in vertical-lr (left of footnotes, if any). Thus the logical exclusion is
	 * {@code [0, inlineExtent]} on {@link FloatSide#START}. Changing it to the draft design's END-side rectangle would
	 * intersect current drawing, so actual drawing coordinates are authoritative. However, physical {@code bottom} in
	 * vertical writing goes at line end (paper bottom), with exclusion on {@link FloatSide#END} as well ({@link
	 * #bottomAtLineEnd}, 2026-10-05).
	 * </p>
	 */
	private void rebuildBottomPageFloatExclusions() {
		if (this.bottomFloatOneDimensionalFallback || this.bottomFloatReservedCount == 0) {
			this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
			this.refreshPageFloatExclusionSnapshot();
			return;
		}
		final java.util.List<FloatExclusion> exclusions = new java.util.ArrayList<>(
				this.bottomFloatReservedCount);
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		final double fragmentLimit = Math.max(0, super.getPageLimit() - this.pageFootnoteHost.footnoteReservation);
		double pageAxis = this.firstReservedBottomPlacedStart();
		int i = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
			if (i++ >= this.bottomFloatReservedCount) {
				break;
			}
			final double placedStart = pageAxis;
			final double placedEnd = placedStart + this.footnoteExtent(floatBox);
			final double exclusionStart = Math.max(0, placedStart);
			final double exclusionEnd = Math.min(fragmentLimit, placedEnd);
			if (exclusionEnd > exclusionStart) {
				Long order = this.bottomFloatOrders.get(floatBox);
				if (order == null) {
					order = Long.valueOf(this.nextPageFloatOrder());
					this.bottomFloatOrders.put(floatBox, order);
				}
				final double lineExtent = floatBox.getLineExtent(flow);
				final boolean lineEnd = this.bottomAtLineEnd(floatBox);
				exclusions.add(new FloatExclusion(order.longValue(), lineEnd ? FloatSide.END : FloatSide.START,
						new AxisSpan(exclusionStart, exclusionEnd),
						lineEnd ? new AxisSpan(this.pageBox.getLineSize() - lineExtent, this.pageBox.getLineSize())
								: new AxisSpan(0, lineExtent)));
			}
			pageAxis = placedEnd;
		}
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.copyOfSorted(exclusions);
		this.refreshPageFloatExclusionSnapshot();
	}

	/**
	 * Whether bottom floats go at line end (2026-10-05). Only for physical {@code bottom} in vertical writing when
	 * lines run top to bottom, placing them at the paper bottom (css-page-floats bottom means block-end or inline-end
	 * depending on writing direction). {@code block-end} and horizontal writing use line start, as before.
	 */
	private boolean bottomAtLineEnd(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox) {
		return floatBox.getPos() instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos pos && pos.physical
				&& this.pageBox.getBlockParams()
						.getInlineProgression() == net.zamasoft.foliojet.layout.box.params.TypesettingMode.InlineProgression.TOP_TO_BOTTOM;
	}

	/**
	 * Settles bottom floats when finalizing the page (from finishLayout). Stacks them above footnotes if present,
	 * otherwise at the type area bottom.
	 *
	 * @param notesExtent actual extent occupied by footnotes (including separator spacing)
	 */
	private void attachBottomPageFloats(final double notesExtent) {
		if (this.pendingBottomFloats.isEmpty()) {
			this.bottomFloatReservedCount = 0;
			this.bottomFloatReservation = 0;
			this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
			this.refreshPageFloatExclusionSnapshot();
			return;
		}
		double attachedExtent = 0;
		{
			int i = 0;
			for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
				if (i >= this.bottomFloatReservedCount) {
					break;
				}
				attachedExtent += this.footnoteExtent(floatBox);
				++i;
			}
		}
		double pageAxis = super.getPageLimit() - notesExtent - attachedExtent;
		for (int i = 0; i < this.bottomFloatReservedCount; ++i) {
			final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox = this.pendingBottomFloats
					.removeFirst();
			this.pendingBottomFloatGenerations.remove(floatBox);
			final Long previous = this.placedBottomFloatGenerations.put(floatBox, this.pageGeneration);
			if (previous != null && previous.longValue() == this.pageGeneration) {
				throw new IllegalStateException(
						"bottom page float repeated on page generation " + this.pageGeneration);
			}
			final double lineAxis = this.bottomAtLineEnd(floatBox)
					? this.pageBox.getLineSize() - floatBox.getLineExtent(this.pageBox.getBlockParams().flow)
					: 0;
			this.pageBox.getContainer().addFloating(floatBox, lineAxis, pageAxis);
			pageAxis += this.footnoteExtent(floatBox);
			this.pageFloatProgressed = true;
		}
		this.bottomFloatReservedCount = 0;
		this.bottomFloatReservation = 0;
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.refreshPageFloatExclusionSnapshot();
	}

	/**
	 * Plans the continuous prefix placeable from the front without changing the top-float queue. Never skips entries;
	 * when {@code atPageStart == false}, returns an empty plan if the first entry does not fit. At page start, retains
	 * the existing rule: take the first entry with {@code stackEnd == 0} regardless of capacity, then take subsequent
	 * entries only while they fit.
	 *
	 * @param queue       FIFO queue of top floats (read-only)
	 * @param stackEnd    end of the placed top prefix
	 * @param maxArea     page-axis end up to which top floats can stack
	 * @param atPageStart true to apply the page-start progress guarantee
	 * @return accepted prefix and its total occupied extent
	 */
	final TopFloatPlan planTopFloats(
			final java.util.Deque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> queue,
			final double stackEnd, final double maxArea, final boolean atPageStart) {
		return planTopFloats(queue, this::footnoteExtent, stackEnd, maxArea, atPageStart);
	}

	/**
	 * Pure core of {@link #planTopFloats(java.util.Deque, double, double, boolean)}. Accepts an injected
	 * occupied-extent measure, allowing standalone checks without constructing a builder.
	 */
	static TopFloatPlan planTopFloats(
			final Iterable<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> queue,
			final java.util.function.ToDoubleFunction<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> extentOf,
			final double stackEnd, final double maxArea, final boolean atPageStart) {
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes = new java.util.ArrayList<>();
		double pageAxis = stackEnd;
		double dy = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : queue) {
			final double extent = extentOf.applyAsDouble(floatBox);
			if ((!atPageStart || pageAxis > 0) && pageAxis + extent > maxArea) {
				break;
			}
			boxes.add(floatBox);
			pageAxis += extent;
			dy += extent;
			if (atPageStart && pageAxis > maxArea) {
				break;
			}
		}
		return new TopFloatPlan(boxes, dy);
	}

	/**
	 * Places only planned top floats in FIFO order and registers their actual rectangles in this page's exclusion
	 * space for line scanning. Does not advance the body cursor.
	 *
	 * @param plan placement plan returned by {@link #planTopFloats}
	 */
	private void placeTopPageFloats(final TopFloatPlan plan) {
		if (plan.boxes.isEmpty() && this.pendingTopFloats.isEmpty()) {
			return;
		}
		// Keep MIN_PAGE_LIMIT for base page breaks and footnote reservations. Top placement does not
		// push body text down, so it can stack up to the actual free end of the page.
		final double maxArea = super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation;
		final double fragmentLimit = this.getPageOwnerLimit();
		double pageAxis = this.topPageFloatStackEnd;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : plan.boxes) {
			assert this.pendingTopFloats.peekFirst() == floatBox : "top float plan/queue order mismatch";
			final double extent = this.footnoteExtent(floatBox);
			this.pendingTopFloats.removeFirst();
			this.pendingTopFloatGenerations.remove(floatBox);
			final Long previous = this.placedTopFloatGenerations.put(floatBox, this.pageGeneration);
			// Always consume the queue during placement. Replacing the same float on the same page
			// breaks the progress guarantee for float-only pages, so never duplicate silently.
			if (previous != null && previous.longValue() == this.pageGeneration) {
				throw new IllegalStateException("top page float repeated on page generation " + this.pageGeneration);
			}
			final double placedStart = pageAxis;
			final double placedEnd = placedStart + extent;
			// Limit exclusions to this PageBox's fragmentainer. Using the full actual extent
			// of visually overflowing floats moves lines many pages ahead, then ordinary
			// page breaks bring them back gradually. Replacing the set on the next page
			// would still leave many blank pages.
			// **The end must not precede the start** (2026-09-17). fragmentLimit can shrink within
			// one page generation (footnote reservations arrive later). A zero-height float stacked
			// after shrinking had start=139.68 but min(end, limit)=139.42, creating a negative interval
			// and violating the ascending pageSpan.end contract (ExclusionSpace.copyOfSorted)
			// (AssertionError in the wild sweep; production disables asserts, so it used
			// the out-of-order exclusions unchanged).
			final double exclusionEnd = Math.max(placedStart, Math.min(placedEnd, fragmentLimit));
			if (DebugFlags.TOP_FLOAT) {
				System.err.println("[topFloat] gen=" + this.pageGeneration + " start=" + placedStart + " extent=" + extent
						+ " end=" + placedEnd + " fragmentLimit=" + fragmentLimit + " exclusionEnd=" + exclusionEnd
						+ " stackEnd=" + this.topPageFloatStackEnd + " existing=" + this.topPageFloatExclusions.size()
						+ " translate=" + this.placingTopByTranslate + " element="
						+ (floatBox.getParams() == null ? "-" : String.valueOf(floatBox.getParams().element)));
			}
			this.pageBox.getContainer().addFloating(floatBox, 0, pageAxis);
			final double lineExtent = floatBox.getLineExtent(this.pageBox.getBlockParams().flow);
			if (!this.placingTopByTranslate && net.zamasoft.foliojet.layout.util.LayoutUtils.compare(lineExtent,
					this.pageBox.getLineSize()) != 0) {
				this.narrowTopPlacedWithTextBeside = true;
			}
			this.topPageFloatExclusions.add(new FloatExclusion(this.nextPageFloatOrder(), FloatSide.START,
					new AxisSpan(placedStart, exclusionEnd), new AxisSpan(0, lineExtent)));
			pageAxis = placedEnd;
			this.topPageFloatStackEnd = pageAxis;
			this.pageFloatProgressed = true;
			if (pageAxis > maxArea) {
				// Place floats that cannot fit even alone on a page with overflow
				// (crash-elimination policy: warn and continue).
				LOG.warning("page float too large for the page: " + extent + "pt");
			}
		}
		this.topPageFloatExclusionSnapshot = ExclusionSpace.copyOfSorted(this.topPageFloatExclusions);
		this.refreshPageFloatExclusionSnapshot();
	}

	/** Whether float placement advanced on the most recent page (finish() progress guard). */
	private boolean pageFloatProgressed = false;

	/** Most recent (page generation, occupied size) per element for floats split at page start. */
	private final java.util.Map<Object, double[]> fragmentStartFloatSplits = new java.util.IdentityHashMap<>();

	/**
	 * Returns whether a float placed at page start <b>may be split again</b> (2026-09-17).
	 *
	 * <p>
	 * Block floats on the same axis are always treated as splittable, but if their contents cannot split along the
	 * page axis (explicit sizes such as {@code width:58pt} in vertical writing are reapplied in full to every
	 * fragment; orthogonal-flow cells cannot split), the remainder is rebuilt at <b>the same size</b> on the next
	 * page. Splitting at page start → same-sized remainder → splitting at page start repeated indefinitely, producing
	 * blank pages until OutOfMemoryError ("excessive page count" in the wild sweep: a 63.25 pt remainder continued for
	 * 27,820 pages on 60 pt paper).
	 * </p>
	 *
	 * <p>
	 * If occupied size has not shrunk since the previous page-start split, no progress has occurred. Stop splitting
	 * and place with overflow (same treatment as an unsplittable float at page start). Do not compare reclassification
	 * within the same page generation (column balancing/restyle).
	 * </p>
	 *
	 * @return true if splitting is allowed; false if no progress occurred
	 */
	boolean fragmentStartFloatSplitProgresses(final Object element, final double occupiedExtent) {
		if (element == null) {
			return true;
		}
		final double[] previous = this.fragmentStartFloatSplits.get(element);
		if (DebugFlags.FLOAT_TRACE) {
			System.err.println("[float-progress] gen=" + this.pageGeneration + " extent=" + occupiedExtent + " previous="
					+ (previous == null ? "null" : previous[0] + "/" + previous[1]) + " element=" + System.identityHashCode(element));
		}
		if (previous != null && previous[0] < this.pageGeneration
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(occupiedExtent, previous[1]) >= 0) {
			this.fragmentStartFloatSplits.remove(element);
			return false;
		}
		if (previous == null || previous[0] < this.pageGeneration) {
			this.fragmentStartFloatSplits.put(element, new double[] { this.pageGeneration, occupiedExtent });
		}
		return true;
	}

	/**
	 * Whether this page has a narrow top float placed at page start with two-dimensional exclusion. Body text may run
	 * beside it, so prohibit later translation (shifted lines would overlap the new band). Exclude narrow top floats
	 * placed as bands by translation, since no body text runs beside them.
	 */
	private boolean narrowTopPlacedWithTextBeside = false;

	/** True while {@link #placeTopPageFloats} is called from translation (band placement). */
	private boolean placingTopByTranslate = false;

	/**
	 * Settles footnotes at page finalization (from finishLayout, after splitting and before drawing). Collects
	 * ::footnote-call IDs remaining in the finalized box tree and places only the continuous leading pending prefix
	 * whose entries are carried in (committed) or have a call remaining on this page at the type area bottom. Marks
	 * unplaced notes whose calls remain here as committed (capacity deferral/order preservation) for first-priority
	 * placement on the next page. Bottom-aligns using the total height actually placed, not reserved height (if call
	 * movement defers some notes, using reserved height leaves space below them). In bands with explicit
	 * height/min-height, arranges notes from the body side of the reserved area. Always settles ledger state, even
	 * with zero placements, so it does not leak into the next page.
	 */
	private double attachFootnotes() {
		final boolean probe = this.isFootnoteProbe();
		final boolean planned = this.hasFootnotePlan() || this.footnoteArea().isHeightFixed();
		final FootnoteCallScan probeScan = probe ? scanFootnoteCalls(this.pageBox, false)
				: planned ? this.scanFootnoteCalls(this.pageBox) : null;
		if (planned) {
			// Do not lose IDs awaiting their bodies, ownership, or numbering even if a call closes the page first.
			for (final long id : new java.util.TreeSet<>(probeScan.ids())) {
				if (!this.registeredFootnotes.contains(id)) this.bottomFootnote(id);
			}
			this.pageHadContent = probeScan.contentful();
			this.updateFootnotePrefix();
		}
		if (probe) {
			if (this.probeCallNumbers == null) this.probeCallNumbers = new java.util.HashMap<>();
			int number = 1;
			for (final long id : new java.util.TreeSet<>(probeScan.ids())) {
				boolean committed = false;
				boolean pending = false;
				for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
					if (entry.id == id) {
						pending = true;
						committed = entry.committed;
					}
				}
				if (!pending && this.registeredFootnotes.contains(id)) continue;
				if (!committed && !this.probeCallNumbers.containsKey(id)) this.probeCallNumbers.put(id, number++);
			}
		}
		final FootnoteCallScan columnPageScan = this.columnPageEntries.isEmpty() ? null : this.scanFootnoteCalls(this.pageBox);
		if (columnPageScan != null) this.numberColumnPageFootnotes(columnPageScan);
		if (this.pageFootnoteHost.pendingFootnotes.isEmpty()) {
			final double emptyArea = !this.isPageBandFootnoteArea()
					&& (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0)
					? this.pageFootnoteHost.footnoteReservation : 0;
			this.pageFootnoteHost.footnoteReservedCount = 0;
			this.pageFootnoteHost.footnoteReservation = 0;
			return emptyArea;
		}
		final FootnoteCallScan scan = columnPageScan != null ? columnPageScan
				: probe || planned ? probeScan : this.scanFootnoteCalls(this.pageBox);
		this.pageHadContent = scan.contentful();
		final java.util.Set<Long> retained = scan.ids();
		this.traceFootnote("page-plan", null, 0, retained);

		// F5 numbering: assign numbers starting at 1 in FIFO (document) order to unnumbered
		// entries whose calls remain on this page. Do not renumber committed entries
		// (carry-in already numbered on earlier pages).
		if (columnPageScan == null) {
			int nextNumber = 1;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				if (!entry.committed && retained.contains(entry.id)) {
					entry.assignedNumber = probe ? this.probeCallNumbers.remove(entry.id) : nextNumber++;
				}
			}
		}
		// Placement plan (determine every destination without mutation, then commit once).
		int attachCount = 0;
		double attachedExtent = 0;
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				final boolean forced = this.forceFootnoteAttach && i == 0;
				if (i >= this.pageFootnoteHost.footnoteReservedCount
						|| (!entry.committed && !retained.contains(entry.id) && !forced)) {
					break;
				}
				if (planned) {
					final FootnoteReservation reservation = this.footnotePlan.get(entry.id);
					if (entry.noteBox == null || reservation == null) break;
					final double height = this.isPageBandFootnoteArea() ? this.footnoteBandCost(entry.noteBox) : this.footnoteExtent(entry.noteBox);
					final double capacity = this.isPageBandFootnoteArea() ? this.footnoteBandCapacity() : this.pageFootnoteHost.footnoteReservation;
					// A vertical band's gap lies along paper height, outside the horizontal note-stacking calculation.
					final double gap = this.isVerticalFootnoteBand() ? 0 : FOOTNOTE_GAP;
					if (!(i == 0 && reservation.oversized())
							&& (net.zamasoft.foliojet.layout.util.LayoutUtils.compare(height, reservation.height()) > 0
									|| net.zamasoft.foliojet.layout.util.LayoutUtils.compare(gap + attachedExtent + height, capacity) > 0)) break;
				}
				++attachCount;
				if (this.isPageBandFootnoteArea()) {
					attachedExtent += this.footnoteBandCost(entry.noteBox);
				} else {
					attachedExtent += this.footnoteExtent(entry.noteBox);
				}
				++i;
			}
		}
		// Unplaced remaining notes whose calls stay on this page become carry-in.
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				this.traceFootnote(i < attachCount ? "attach" : "defer", entry, 0, retained);
				if (i >= attachCount && retained.contains(entry.id)) {
					entry.committed = true;
				}
				++i;
			}
		}
		// F5: resolve call labels remaining in this page's finalized tree (markers belong
		// to notes, so resolve them at attachment). Skip labels for IDs absent from pending
		// (such as markers in previously placed notes).
		if (!probe) {
			final java.util.Map<Long, Integer> numbers = new java.util.HashMap<>();
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				if (entry.assignedNumber > 0) {
					numbers.put(entry.id, entry.assignedNumber);
				}
			}
			for (final net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label : scan.labels()) {
				if (!label.isMarker()) {
					final Integer number = numbers.get(label.getFootnoteId());
					if (number != null) {
						label.resolve(number);
					}
				}
			}
		}
		final double base = this.isPageBandFootnoteArea() ? super.getPageLimit() : this.pageFootnoteHost.capacityBase.getAsDouble();
		final boolean sizedBlockArea = !this.isPageBandFootnoteArea()
				&& (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0);
		final double blockArea = sizedBlockArea ? this.pageFootnoteHost.footnoteReservation : 0;
		double pageAxis = sizedBlockArea ? base - blockArea + FOOTNOTE_GAP : base - attachedExtent;
		// Arrange bands from the reserved area's line start. Even oversized notes must not overflow toward body text.
		// Bottom lies outside the type area bottom (positive); top lies above the content origin (negative).
		// For top bands, PageBox.reserveHeadArea has lowered the content origin by the band extent,
		// so the band starts at minus the reserved extent.
		double lineAxis = 0;
		if (this.isPageBandFootnoteArea()) {
			lineAxis = this.footnoteArea().isHeadBand() ? -this.pageBox.getHeadInset()
					: this.pageBox.getInnerHeight() + FOOTNOTE_GAP;
		}
		// Horizontal paper position for notes stacked in a vertical band (advance from block-start).
		double bandPageAxis = 0;
		for (int i = 0; i < attachCount; ++i) {
			final FootnoteEntry entry = this.pageFootnoteHost.pendingFootnotes.removeFirst();
			if (planned) this.bottomFootnotes.remove(entry.id);
			if (entry.assignedNumber < 0) {
				// Footnotes whose calls the scan did not find (e.g. in table cells).
				// Use document-order sequential numbering without failing conversion (§5.13).
				entry.assignedNumber = (int) (entry.id + 1);
			}
			// Resolve the ::footnote-marker label at the note body start with the call-page number.
			if (!probe) {
				for (final net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label : this
						.scanFootnoteCalls(entry.noteBox).labels()) {
					if (label.isMarker()) {
						label.resolve(entry.assignedNumber);
					}
				}
			}
			if (this.isPageBandFootnoteArea()) {
				// addFloating takes (lineAxis, pageAxis), not physical x/y. In vertical writing, the line axis is y.
				final WritingMode pageFlow = this.pageBox.getBlockParams().flow;
				if (this.isVerticalFootnoteBand()) {
					// Vertical band (2026-09-11): notes **stack horizontally**, with line starts aligned at the band top.
					// The pageAxis origin is block-start (right edge for RL), so advance left from there
					// by each note's width. lineAxis stays at the band top for all notes.
					this.pageBox.getContainer().addFloating(entry.noteBox, lineAxis, bandPageAxis);
					bandPageAxis += this.footnoteBandCost(entry.noteBox);
				} else {
					// Horizontal band: align notes to the paper's left edge. In RL, the pageAxis origin
					// is the right edge, so subtract the note's width (page-direction extent), preventing
					// left-edge overflow even when the carry-over page is narrower than the call page.
					final double notePageAxis = pageFlow == WritingMode.RL
							? this.pageBox.getInnerPageExtent(pageFlow) - entry.noteBox.getPageExtent(pageFlow)
							: 0;
					this.pageBox.getContainer().addFloating(entry.noteBox, lineAxis, notePageAxis);
					lineAxis += this.footnoteBandExtent(entry.noteBox);
				}
			} else {
				this.pageFootnoteHost.addFloating(entry.noteBox, pageAxis);
				pageAxis += this.footnoteExtent(entry.noteBox);
			}
			this.pageFootnoteHost.footnoteProgressed = true;
		}
		if (this.isPageBandFootnoteArea()) {
			if (attachCount > 0) {
				final net.zamasoft.foliojet.ua.FootnoteArea area = this.pageBox.getUserAgent()
						.getUAContext().getFootnoteArea();
				// Center the rule in the body-to-band gap. A top band's rule is above the content origin, hence negative.
				this.pageBox.setFootnoteSeparatorLineAxis(
						area.isHeadBand() ? -FOOTNOTE_GAP / 2 : this.pageBox.getInnerHeight() + FOOTNOTE_GAP / 2,
						area.flow == null ? this.pageBox.getBlockParams().flow : area.flow);
			}
			this.pageFootnoteHost.footnoteReservedCount = 0;
			this.pageFootnoteHost.footnoteReservation = 0;
			// The block-direction footnote extent passed to bottom page floats is 0.
			return 0;
		}
		if (attachCount > 0) {
			// Separator rule (F6/F7 recommendation ①): centered in the existing gap, so no extra
			// reservation. Draw after flows in PageSequence.drawPage (artifact).
			this.pageBox.setFootnoteSeparatorAxis(sizedBlockArea ? base - blockArea + FOOTNOTE_GAP / 2
					: base - attachedExtent - FOOTNOTE_GAP / 2);
		}
		this.pageFootnoteHost.footnoteReservedCount = 0;
		this.pageFootnoteHost.footnoteReservation = 0;
		return sizedBlockArea ? blockArea : attachedExtent == 0 ? 0 : attachedExtent + FOOTNOTE_GAP;
	}

	/** Numbers all hosts once by logical ID (document order). Carry-in does not consume a new number. */
	private void numberColumnPageFootnotes(final FootnoteCallScan scan) {
		final java.util.SortedMap<Long, FootnoteEntry> entries = new java.util.TreeMap<>(this.columnPageEntries);
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) entries.put(entry.id, entry);
		int nextNumber = 1;
		for (final FootnoteEntry entry : entries.values()) {
			if (!entry.committed && scan.ids().contains(entry.id)) entry.assignedNumber = nextNumber++;
		}
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels = new java.util.ArrayList<>(scan.labels());
		labels.addAll(this.columnPageLabels);
		for (final var label : labels) {
			final FootnoteEntry entry = entries.get(label.getFootnoteId());
			if (entry != null && entry.assignedNumber > 0) label.resolve(entry.assignedNumber);
		}
		for (final FootnoteEntry entry : entries.values()) {
			// Notes whose calls remain in the column but cannot be placed (carry-over to the next column/page)
			// become committed and receive top-priority carry-in placement, as with page attachment
			// (using this page's number; grok review requirement 2).
			if (entry.columnCallRetained && entry.attachedColumnHost == null && scan.ids().contains(entry.id)) {
				entry.committed = true;
			}
			entry.columnCallRetained = false;
		}
	}

	/** Warns about footnotes larger than the type area once per document. */
	private boolean warnedOversizedFootnote = false;

	/**
	 * Whether to force placement from the front even for notes with no call found (finish() progress guarantee,
	 * 2026-08-02).
	 */
	private boolean forceFootnoteAttach = false;

	/**
	 * Scan result: call ID set, found footnote labels (calls and markers), and presence of content (lines/replaced
	 * elements).
	 */
	private record FootnoteCallScan(java.util.Set<Long> ids,
			java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels, boolean contentful) {
	}

	/**
	 * Collects footnote call IDs and label atoms from the box tree (revised candidate A of the F4 recommendation + F5
	 * label resolution). A set collapses duplicate inline fragments of the same call across lines into one entry. Uses
	 * iterative DFS with an explicit worklist. Besides flows, floats, lines, and inlines, descends into <b>tables (row
	 * groups → rows → cells) and absolutely positioned boxes</b> (2026-09-02). Previously those two were outside the
	 * scan; notes in table cells stayed pending until the end without their calls being found, then their bodies
	 * disappeared when EOF "gave up", while call numbers stayed document-wide (cti.li report, 2026-09-01: the note in
	 * a page-2 cell had no body and number 6).
	 */
	private FootnoteCallScan scanFootnoteCalls(final net.zamasoft.foliojet.layout.box.AbstractContainerBox root) {
		return scanFootnoteCalls(root, true);
	}

	/** Read-only scan for measurement. Does not perform deferred absolute binding or resolve footnote numbers. */
	public static java.util.Set<Long> collectFootnoteCalls(
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox root) {
		return java.util.Set.copyOf(scanFootnoteCalls(root, false).ids());
	}

	private static FootnoteCallScan scanFootnoteCalls(
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox root, final boolean bindAbsolute) {
		final java.util.ArrayDeque<Object> work = new java.util.ArrayDeque<>();
		work.push(root);
		return scanFootnoteCalls(work, bindAbsolute);
	}

	private static FootnoteCallScan scanFootnoteCalls(final net.zamasoft.foliojet.layout.box.content.Container source,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, final boolean bindAbsolute) {
		final java.util.ArrayDeque<Object> work = new java.util.ArrayDeque<>();
		pushFootnoteChildren(source, owner, bindAbsolute, work);
		return scanFootnoteCalls(work, bindAbsolute);
	}

	private static FootnoteCallScan scanFootnoteCalls(final java.util.ArrayDeque<Object> work, final boolean bindAbsolute) {
		final java.util.Set<Long> ids = new java.util.HashSet<>();
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels = new java.util.ArrayList<>();
		boolean contentful = false;
		while (!work.isEmpty()) {
			final Object node = work.pop();
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractReplacedBox
					|| node instanceof net.zamasoft.foliojet.layout.box.AbstractLineBox) {
				contentful = true;
			}
			if (node instanceof net.zamasoft.foliojet.layout.box.IBox box) {
				final net.zamasoft.foliojet.layout.box.params.Params params = box.getParams();
				if (params != null && params.element == net.zamasoft.foliojet.css.CSSElement.FOOTNOTE_CALL
						&& params.footnoteId >= 0) {
					ids.add(params.footnoteId);
				}
			}
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractReplacedBox replaced
					&& replaced.getReplacedParams().image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label) {
				labels.add(label);
			}
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox container) {
				pushFootnoteChildren(container.getContainer(), container, bindAbsolute, work);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.impl.TableBox table) {
				// Tables: row groups in header → body → footer order, rows, and original cells
				// (skip extended cells because they refer to the same box).
				final java.util.List<net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox> groups = new java.util.ArrayList<>();
				if (table.getTableHeader() != null) {
					groups.add(table.getTableHeader());
				}
				for (int i = 0; i < table.getTableBodyCount(); ++i) {
					groups.add(table.getTableBody(i));
				}
				if (table.getTableFooter() != null) {
					groups.add(table.getTableFooter());
				}
				for (final net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox group : groups) {
					for (int r = 0; r < group.getTableRowCount(); ++r) {
						final net.zamasoft.foliojet.layout.box.impl.TableRowBox row = group.getTableRow(r);
						for (int c = 0; c < row.getCellCount(); ++c) {
							final net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell cell = row.getCell(c);
							if (cell.isSource() && cell.getCellBox() != null) {
								work.push(cell.getCellBox());
							}
						}
					}
				}
			} else if (node instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
				textBlock.forEachLine(work::push);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.AbstractTextBox textBox) {
				textBox.forEachInlineBox(work::push);
			}
		}
		return new FootnoteCallScan(ids, labels, contentful);
	}

	/** Shares the container entry point. Binds deferred absolute boxes through that container's owner. */
	private static void pushFootnoteChildren(final net.zamasoft.foliojet.layout.box.content.Container source,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, final boolean bindAbsolute,
			final java.util.ArrayDeque<Object> work) {
		source.eachFlowBox(work::push);
		source.eachFloatingBox(work::push);
		source.eachAbsoluteBox(box -> {
			if (!bindAbsolute) return;
			if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute) {
				absolute.bindDeferredContent(owner);
			}
			work.push(box);
		});
	}

}
