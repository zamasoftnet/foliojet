package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.FootnotePageProbe;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * M6b v3 layout source protocol tee: records the doc input protocol
 * (StartBlock/Chars/EndBlock) in {@link LayoutSource} and forwards it to
 * {@link DocumentBuilder}. Default documents pass through immediately after recording;
 * only bottom + vertical writing delivers immediately to B and delays C with a queue.
 *
 * <p>
 * <b>Order and freeze timing are the contract</b> for recording and delivery:
 * freeze input with {@code LayoutSource.append/freeze} before delivery.
 * A dedicated driver ({@code SourceReplayer}) replays page-break remainders from this
 * log without interfering with live state.
 * </p>
 *
 * <p>
 * {@link LayoutSource} lives for one conversion. Both {@code StyleBuilder.finish()}
 * (early release on success) and {@code CSSProcessor.dispose()} (formatter finally:
 * cleanup on exceptions) guarantee close (idempotent).
 * </p>
 */
final class RecordingLayoutSink {
	private final DocumentBuilder doc;

	boolean isEligibleFootnoteColumnOwner() {
		return this.doc.isEligibleFootnoteColumnOwner();
	}
	private final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events;
	private net.zamasoft.foliojet.layout.box.IBox sourceBox;
	private net.zamasoft.foliojet.css.CSSElement sourceElement;
	private final java.util.Map<net.zamasoft.foliojet.css.CSSStyle, StringBuilder> contentsSources =
			new java.util.IdentityHashMap<net.zamasoft.foliojet.css.CSSStyle, StringBuilder>();

	void beginSource(final net.zamasoft.foliojet.css.CSSElement element) {
		this.sourceBox = null;
		this.sourceElement = element;
	}

	net.zamasoft.foliojet.layout.box.IBox sourceBox() {
		return this.sourceBox;
	}

	void endContentsSource(final net.zamasoft.foliojet.css.CSSStyle style) {
		this.contentsSources.remove(style);
	}
	private net.zamasoft.foliojet.css.style.running.RunningRegistry assignments;
	private static final class AnchorFrame {
		final long source;
		byte whiteSpace = AbstractTextParams.WHITE_SPACE_NORMAL;
		int lastChar = -1;
		int tailChar = -1;
		long lastBox = -1;
		final java.util.List<Long> waiting = new java.util.ArrayList<Long>();

		AnchorFrame(final long source) {
			this.source = source;
		}
	}
	private final java.util.Deque<AnchorFrame> anchors = new java.util.ArrayDeque<AnchorFrame>();

	void setAssignments(final net.zamasoft.foliojet.css.style.running.RunningRegistry assignments) {
		this.assignments = assignments;
	}

	/** Attaches a token to the preceding character or next placed content without producing layout input. */
	void assignment(final long order) {
		this.layoutSource.append(new LayoutSource.Assignment(order));
		if (this.anchors.isEmpty()) {
			this.anchors.push(new AnchorFrame(-1));
		}
		final AnchorFrame frame = this.anchors.peek();
		if (frame.lastChar >= 0) {
			this.assignments.bindCharacters(order, frame.lastChar, false);
		} else {
			frame.waiting.add(order);
		}
	}

	/** Attaches string-set to its source start anchor and uses the same commit path as running. */
	void stringAssignments(final java.util.List<net.zamasoft.foliojet.ua.PendingStringSet> strings,
			final net.zamasoft.foliojet.css.CSSStyle style, final net.zamasoft.foliojet.layout.box.IBox source) {
		final long order = strings.get(0).order;
		this.layoutSource.append(new LayoutSource.Assignment(order));
		if (source != null) {
			// Read completed text from this anchor's placed fragment (the original InlineBox may be laid out again).
			this.assignments.strings(order, strings);
			this.assignments.bindBox(order, source.getAssignmentAnchor());
		} else {
			// display:contents has no box of its own. Collect only its own input and attach it to the next placement.
			if (strings.stream().anyMatch(value -> value.parts.contains(net.zamasoft.foliojet.ua.PendingStringSet.CONTENT))) {
				final StringBuilder text = new StringBuilder();
				this.contentsSources.put(style, text);
				this.assignments.strings(order, strings, buffer -> {
					buffer.append(text);
					// Pending placement needs only the completed text. Do not retain closed styles or
					// parent computed-value arrays through the lambda (StringBuilder uses identity comparison).
					this.contentsSources.values().remove(text);
				});
			} else {
				this.assignments.strings(order, strings);
			}
			if (!this.anchors.isEmpty()) {
				this.anchors.peek().waiting.add(order);
			}
		}
	}

	private void bindWaitingBox(final long source) {
		if (!this.anchors.isEmpty()) {
			final AnchorFrame frame = this.anchors.peek();
			for (final long order : frame.waiting) {
				this.assignments.bindBox(order, source);
			}
			frame.waiting.clear();
		}
	}

	/**
	 * Layout source protocol log (M6b v3). E-6 increment 3b-2:
	 * inject the text payload spill budget (processing.text-spill-budget).
	 */
	private final LayoutSource layoutSource;
	private FootnotePageProbe probe;
	private boolean inputDelivered;
	private boolean closed;
	private boolean probeFinished;
	private long reportEnd, deliveredEventEnd, consumedWatermark;
	private long resolvedReportGeneration;
	private long reportEventId = -1, windowEventId = -1;
	private long windowDeliveries;
	/** Value-only hook to observe retained input window/main log data even when B/C generations differ. */
	static volatile java.util.function.Consumer<FootnotePageProbe.WindowRetention> windowObserver;
	private final java.util.NavigableMap<Long, net.zamasoft.foliojet.layout.FootnotePageProbeReport> reports = new java.util.TreeMap<>();
	private record Delivery(DocumentBuilder.DispatchEvent type, long id, LayoutSource.Event boundary,
			net.zamasoft.foliojet.layout.box.IBox box, Runnable dispatch, long textBytes) {
		long fromId() { return this.boundary == null ? this.id : this.id - 1; }
	}
	private final java.util.ArrayDeque<Delivery> deliveries = new java.util.ArrayDeque<>();
	private LayoutSource.RetentionLease deliveryLease;
	private net.zamasoft.foliojet.layout.RetainedTextLimit.PageWindow pageWindow;
	private boolean splitCharacters;

	/** Before opening children on C's first page, secure geometry and pins, then drive it from B's input. */
	void pageStarted(final net.zamasoft.foliojet.layout.box.impl.PageBox page,
			final double width, final double height, final String pageName,
			final java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> geometry) {
		if (this.closed || this.inputDelivered || this.probe != null
				|| page.getUserAgent().getUAContext().getFootnoteArea().isHeightFixed()
				|| !page.getBlockParams().flow.isVertical()
				|| !page.getUserAgent().getUAContext().getFootnoteArea().isPageBand()) return;
		this.pageWindow = page.getUserAgent().getRetainedTextLimit().pageWindow();
		this.deliveryLease = this.layoutSource.retainFrom(0);
		// NFC applies per input call, so preserve original character boundaries only in that case.
		this.splitCharacters = !net.zamasoft.foliojet.ua.props.UAProps.INPUT_NORMALIZE_TEXT.getBoolean(page.getUserAgent());
		final net.zamasoft.foliojet.ua.UserAgent ua = page.getUserAgent();
		this.probe = new FootnotePageProbe(FootnotePageProbe.PageStart.capture(page, width, height, pageName), this.layoutSource, report -> {
			this.windowEventId = this.reportEventId;
			this.reportEventId = report.eventId();
			this.reportEnd = report.generation();
			if (report.generation() > this.resolvedReportGeneration
					&& report.generation() >= this.doc.getPageGeneration()) this.reports.put(report.generation(), report);
			final var listener = ua.getUAContext().getFootnotePageProbeListener();
			if (listener != null) listener.accept(report);
		});
		this.probe.setPageGeometry(geometry);
		this.probe.observeReports(this.doc::getPageGeneration, this.reports::size);
	}

	private void deliver(final DocumentBuilder.DispatchEvent type, final long id,
			final LayoutSource.Event boundary, final net.zamasoft.foliojet.layout.box.IBox box,
			final Runnable dispatch, final long textBytes) {
		this.inputDelivered = true;
		if (this.probe == null) {
			dispatch.run();
			return;
		}
		this.deliveries.addLast(new Delivery(type, id, boundary, box, dispatch, textBytes));
		this.probe.deliver(type, this.layoutSource.get(id), id, boundary);
		this.observeWindow();
		this.drain(false);
	}

	/**
	 * Even when waiting by matching generations is impossible, deliver input through B's
	 * previous finalized page. Do not expand the window to the whole document when named-page
	 * geometry differences cause more page breaks in C. Do not stop multiple page breaks in
	 * one delivery; beginPage without a report advances using carryover only.
	 */
	private void drain(final boolean endOfInput) {
		while (!this.deliveries.isEmpty()
				&& (endOfInput || this.reportEnd >= this.doc.getPageGeneration() + 1
						|| this.deliveries.peekFirst().id() <= this.windowEventId)) {
			if (!endOfInput && this.reportEnd < this.doc.getPageGeneration() + 1) ++this.windowDeliveries;
			this.doc.startFootnoteInput();
			final Delivery delivery = this.deliveries.peekFirst();
			this.deliveredEventEnd = delivery.id() + 1;
			// Determine C's own live boundary using the original ID of the recorded boundary. Do not append again.
			final LayoutSource.Event boundary = this.doc.preDispatch(delivery.type(), delivery.box(), delivery.fromId());
			if (!java.util.Objects.equals(delivery.boundary(), boundary)) {
				throw new IllegalStateException("B/Cの匿名境界が一致しません: " + delivery.fromId());
			}
			delivery.dispatch().run();
			this.pageWindow.remove(delivery.textBytes());
			this.deliveries.removeFirst();
			final long from = Math.min(this.deliveries.isEmpty() ? this.deliveredEventEnd : this.deliveries.peekFirst().fromId(),
					this.doc.oldestUnfinishedSourceId());
			final LayoutSource.RetentionLease lease = this.layoutSource.retainFrom(from);
			final boolean retryCompaction = this.deliveryLease.fromId() < this.consumedWatermark
					&& from > this.deliveryLease.fromId();
			this.deliveryLease.close();
			this.deliveryLease = lease;
			if (retryCompaction) this.layoutSource.compact(this.consumedWatermark);
		}
		this.observeWindow();
	}

	private void observeWindow() {
		final var observer = windowObserver;
		if (observer != null && this.pageWindow != null) observer.accept(new FootnotePageProbe.WindowRetention(
				this.pageWindow.currentBytes(), this.deliveries.size(), this.reportEnd, this.doc.getPageGeneration(),
				this.windowDeliveries, this.layoutSource.retentionSnapshot(), this.layoutSource.retainedInlineTextBytes(), this.reports.size()));
	}

	boolean isFootnoteInputDelayed() {
		return this.pageWindow != null;
	}

	double footnoteLineWidth(final net.zamasoft.foliojet.layout.box.impl.PageBox page) {
		final double width = this.probe == null ? Double.NaN : this.probe.namedPageWidth();
		return Double.isNaN(width) ? page.getInnerWidth() : width;
	}

	long deliveredEventEnd() {
		return this.pageWindow == null ? Long.MAX_VALUE : this.deliveredEventEnd;
	}

	/**
	 * Consumed only once when C starts. No longer needed whether adopted or rejected due to
	 * geometry mismatch, etc. Do not retain late reports for generations whose plan was fixed
	 * without a report.
	 */
	net.zamasoft.foliojet.layout.FootnotePageProbeReport report(final long generation) {
		this.resolvedReportGeneration = Math.max(this.resolvedReportGeneration, generation);
		this.reports.headMap(generation, false).clear();
		return this.reports.remove(generation);
	}

	boolean probeFinished() {
		return this.probeFinished;
	}

	private void discardProbe() {
		final FootnotePageProbe probe = this.probe;
		this.probe = null;
		if (probe != null) probe.discard();
	}

	void finishProbes() {
		try {
			final boolean complete = this.anchors.stream().noneMatch(frame -> frame.source >= 0);
			if (this.probe != null) this.probe.finishInput(complete);
			this.probeFinished = complete;
			this.drain(true);
		} finally {
			this.discardProbe();
		}
	}

	/**
	 * @param doc a DocumentBuilder whose construction is <b>complete</b>: StyleBuilder constructs
	 *            doc before sink, so adding a future callback that calls {@code getLayoutSource()}
	 *            to the DocumentBuilder constructor would cause an NPE (2026-07-30: explicitly
	 *            documented the premise noted in the agy review)
	 */
	RecordingLayoutSink(final DocumentBuilder doc, final long textSpillBudget) {
		this.doc = doc;
		this.layoutSource = new LayoutSource(textSpillBudget);
		this.events = null;
	}

	/** For repeated-content expansion only. Owns neither the main log, DocumentBuilder, nor assignment state. */
	RecordingLayoutSink(final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events) {
		this.doc = null;
		this.layoutSource = null;
		this.events = events;
	}

	LayoutSource source() {
		return this.layoutSource;
	}

	/** Live-only protocol: boundary check → append synthetic boundary → append real event → dispatch. */
	private LayoutSource.Event preDispatch(final DocumentBuilder.DispatchEvent event,
			final net.zamasoft.foliojet.layout.box.IBox box) {
		if (!this.inputDelivered) this.doc.prepareFootnotePage();
		final LayoutSource.Event boundary = this.probe == null
				? this.doc.preDispatch(event, box, this.layoutSource.nextId())
				: this.probe.preDispatch(event, box, this.layoutSource.nextId());
		if (boundary != null) {
			this.layoutSource.append(boundary);
		}
		return boundary;
	}

	void compact(final long watermark) {
		if (this.pageWindow == null) {
			this.layoutSource.compact(watermark == Long.MAX_VALUE ? this.layoutSource.nextId() : watermark);
			return;
		}
		this.consumedWatermark = Math.max(this.consumedWatermark, Math.min(watermark, this.deliveredEventEnd));
		this.layoutSource.compact(this.consumedWatermark);
	}

	/**
	 * Closes the layout source spill store (temporary file)
	 * (E-6 increment 3b-2). Idempotent.
	 */
	void close() {
		// Separate stopping input (no further B construction) from cleanup. Do not skip cleanup
		// based on `closed`, so a subsequent dispose call can resume it if an exception interrupts
		// the first attempt (discardProbe nulls probe first, and layoutSource.close is idempotent:
		// required item 1 in the codex F-2a-2 review).
		this.closed = true;
		try {
			this.discardProbe();
		} finally {
			try {
				if (this.deliveryLease != null) this.deliveryLease.close();
				if (this.pageWindow != null) this.pageWindow.close();
				this.deliveries.clear();
				this.reports.clear();
				this.layoutSource.close();
			} finally {
				this.anchors.clear();
				this.contentsSources.clear();
				this.sourceBox = null;
				this.sourceElement = null;
				if (this.assignments != null) this.assignments.discardPending();
			}
		}
	}

	/**
	 * Records a box start in the log before passing it to doc (M6b v3).
	 *
	 * <p>
	 * E-6 increment 3b-4 (2026-07-24): freeze with {@code BoxRecipe.freeze} at recording
	 * time so the log retains no live params/pos references (including the {@code CSSElement}
	 * graph). All params/pos mutations are confined to StyleBuilder before recording
	 * (codex design §1.1; independently cross-checked), so freezing at recording time is
	 * equivalent to the previous sharing at replay time. Freeze covers all known kinds
	 * enumerated by {@link #boxKind} and throws for unknown boxes.
	 * Unlike {@code ReplacedRecipe.freeze}, no failure variant ({@code StartLive}) is needed.
	 * </p>
	 */
	void start(final INonReplacedBox box) {
		if (this.events != null) {
			final BoxRecipe recipe = this.recordRecipe(box);
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.BeginBox(recipe));
			return;
		}
		if (this.sourceBox == null && box.getParams().element == this.sourceElement) {
			this.sourceBox = box;
		}
		// The main log and running use the same freeze contract. Fail explicitly for unknown boxes/positions.
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.START_BOX, box);
		final BoxRecipe recipe = this.recordRecipe(box);
		box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Start(recipe)));
		this.bindWaitingBox(box.getSourceAnchor());
		this.anchors.push(new AnchorFrame(box.getSourceAnchor()));
		if (box.getParams() instanceof AbstractTextParams params) {
			this.anchors.peek().whiteSpace = params.whiteSpace;
		}
		this.deliver(DocumentBuilder.DispatchEvent.START_BOX, box.getSourceAnchor(), boundary, box,
				() -> this.doc.startBox(box), 0);
	}

	/**
	 * Records a replaced element in the log before passing it to doc (M6b v3).
	 * Without recording, a subtree containing a replaced element would appear replayable
	 * and lose content (prevents silent holes).
	 *
	 * <p>
	 * E-6 increment 3b-3 (2026-07-24): freeze with {@code ReplacedRecipe.freeze} at recording
	 * time so the log retains no live box reference. Params/pos mutations are confined to
	 * StyleBuilder before recording, so freezing at recording time is equivalent to the
	 * previous sharing at replay time (codex design §1.5; independently cross-checked).
	 * E-6 increment 3b-6: boxes referencing {@code ReplacedBoxImage} can now be frozen by
	 * duplication (the live type {@code ReplacedLive} was removed). Freeze returns empty
	 * only for unknown {@code AbstractReplacedBox} subclasses (structurally impossible for
	 * the four existing implementations). In that case, fail closed by occupying the
	 * position with an unreplayable marker ({@code Opaque} + matching {@code EndBlock};
	 * {@code Opaque} is a start event paired with {@code EndBlock} by contract and cannot
	 * be added alone). Sealing a TwoPass range containing it fails the conversion.
	 * </p>
	 */
	void replaced(final AbstractReplacedBox box) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Replaced(
					ReplacedRecipe.freeze(box).orElseThrow()));
			return;
		}
		if (this.sourceBox == null && box.getParams().element == this.sourceElement) {
			this.sourceBox = box;
		}
		for (final StringBuilder text : this.contentsSources.values()) {
			box.getText(text);
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.REPLACED, box);
		final java.util.Optional<ReplacedRecipe> recipe = ReplacedRecipe.freeze(box);
		if (recipe.isPresent()) {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Replaced(recipe.get())));
		} else {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Opaque()));
			this.layoutSource.append(new LayoutSource.EndBlock());
		}
		this.bindWaitingBox(box.getSourceAnchor());
		if (!this.anchors.isEmpty()) {
			this.anchors.peek().lastBox = box.getSourceAnchor();
			this.anchors.peek().lastChar = -1;
			this.anchors.peek().tailChar = -1;
		}
		this.deliver(DocumentBuilder.DispatchEvent.REPLACED, box.getSourceAnchor(), boundary, box,
				() -> this.doc.addReplacedBox(box), 0);
	}

	/**
	 * Records a box end in the log before passing it to doc (M6b v3).
	 */
	void end() {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.EndBox());
			return;
		}
		final AnchorFrame frame = this.anchors.peek();
		if (frame != null) {
			if (frame.tailChar >= 0) {
				for (final long order : frame.waiting) {
					this.assignments.bindCharacters(order, frame.tailChar, false);
				}
				frame.waiting.clear();
			} else {
				this.bindWaitingBox(frame.lastBox >= 0 ? frame.lastBox : frame.source);
			}
			this.anchors.pop();
			if (!this.anchors.isEmpty()) {
				this.anchors.peek().lastBox = frame.source;
				this.anchors.peek().lastChar = -1;
				this.anchors.peek().tailChar = frame.tailChar;
			}
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.END_BOX, null);
		final long id = this.layoutSource.append(new LayoutSource.EndBlock());
		this.deliver(DocumentBuilder.DispatchEvent.END_BOX, id, boundary, null, this.doc::endBox, 0);
	}

	/**
	 * Records {@code leader()} in the log before passing it to doc (leader() L1).
	 */
	void leader(final String pattern) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Leader(pattern));
			return;
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.LEADER, null);
		final long id = this.layoutSource.append(new LayoutSource.Leader(pattern));
		this.deliver(DocumentBuilder.DispatchEvent.LEADER, id, boundary, null, () -> this.doc.addLeader(pattern), 0);
	}

	/**
	 * Records text in the log before passing it to doc (M6b v3).
	 */
	void characters(final int charOffset, final char[] ch, final int off, final int len, final boolean fixed) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Text(
					charOffset, new String(ch, off, len), fixed));
			return;
		}
		if (!this.inputDelivered) this.doc.prepareFootnotePage();
		if (this.probe != null && this.splitCharacters && len > 256) {
			for (int offset = 0; offset < len;) {
				int size = Math.min(256, len - offset);
				if (offset + size < len && Character.isHighSurrogate(ch[off + offset + size - 1])
						&& Character.isLowSurrogate(ch[off + offset + size])) --size;
				this.characters(charOffset < 0 ? charOffset : charOffset + offset, ch, off + offset, size, fixed);
				offset += size;
			}
			return;
		}
		for (final StringBuilder text : this.contentsSources.values()) {
			text.append(ch, off, len);
		}
		if (!this.anchors.isEmpty() && charOffset < 0) {
			final AnchorFrame frame = this.anchors.peek();
			for (int i = 0; i < len; ++i) {
				if (ch[off + i] == '\n' && preserved('\n', frame.whiteSpace, fixed)) {
					// Generated line breaks such as br have no source character. Do not reuse the old line's anchor.
					frame.lastChar = frame.tailChar = -1;
				}
			}
		}
		if (!this.anchors.isEmpty() && charOffset >= 0 && len > 0) {
			final AnchorFrame frame = this.anchors.peek();
			// Whitespace/line breaks in pre modes are placed Controls. Exclude only collapsing line-end spaces.
			int first = 0;
			while (first < len && !preserved(ch[off + first], frame.whiteSpace, fixed)) {
				++first;
			}
			if (first < len) {
				int last = len - 1;
				while (last > first && !preserved(ch[off + last], frame.whiteSpace, fixed)) {
					--last;
				}
				for (final long order : frame.waiting) {
					this.assignments.bindCharacters(order, charOffset + first, true);
				}
				frame.waiting.clear();
				frame.lastChar = frame.tailChar = charOffset + last;
				if (ch[off + last] == '\n') {
					// The original position after a break is on the next line. Wait for placement even after break output.
					frame.lastChar = -1;
				}
			}
		}
		// E-6 increment 3b-2: LayoutSource handles defensive copying and budget-based spill decisions
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.TEXT, null);
		final long bytes = this.probe == null ? 0 : 2L * len;
		if (this.pageWindow != null) this.pageWindow.add(bytes);
		final long id = this.layoutSource.appendChars(charOffset, ch, off, len, fixed);
		if (this.probe == null) {
			this.deliver(DocumentBuilder.DispatchEvent.TEXT, id, boundary, null,
					() -> this.doc.characters(charOffset, ch, off, len, fixed), 0);
		} else {
			final char[] copy = java.util.Arrays.copyOfRange(ch, off, off + len);
			this.deliver(DocumentBuilder.DispatchEvent.TEXT, id, boundary, null,
					() -> this.doc.characters(charOffset, copy, 0, copy.length, fixed), bytes);
		}
	}

	private static boolean preserved(final char c, final byte whiteSpace, final boolean fixed) {
		return !TextUtils.isWhiteSpace(c) || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE
				|| whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_WRAP
				|| c == '\n' && (fixed || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_LINE);
	}


	/** Total function shared by the main log and independent replay. Unknown boxes/positions fail conversion. */
	private BoxRecipe recordRecipe(final INonReplacedBox box) {
		try {
			return boxRecipe(box);
		} catch (final net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException cause) {
			final long eventId = this.layoutSource == null ? -1 : this.layoutSource.nextId();
			final var failure = new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					cause.getMessage() + " " + (this.doc == null ? "uri=<unknown> owner state=RECORDING" : this.doc.sourceOwnerContext())
							+ " EventId=[" + eventId + "," + eventId + "]");
			failure.initCause(cause);
			throw failure;
		}
	}

	private static BoxRecipe boxRecipe(final INonReplacedBox box) {
		final LayoutSource.BoxKind kind = boxKind(box);
		return switch (kind) {
		case TABLE -> {
			final var table = (net.zamasoft.foliojet.layout.box.impl.TableBox) box;
			final var block = table.getBlockBox();
			if (block.getParams() != table.getTableParams()) throw unsupportedBox(box);
			final LayoutSource.BoxKind placement = boxKind(block);
			final boolean supported = switch (placement) {
			case FLOW -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FlowPos.class;
			case FLOAT_BLOCK -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FloatPos.class;
			case INLINE_BLOCK -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.InlinePos.class;
			case ABSOLUTE -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.AbsolutePos.class;
			case MULTICOL, INLINE, MARKER, INSIDE_MARKER, TABLE, TABLE_ROW_GROUP, TABLE_ROW,
					TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, GRID, CAPTION, FLEX -> false;
			};
			if (!supported) throw unsupportedBox(box);
			yield placement == LayoutSource.BoxKind.FLOW ? BoxRecipe.freeze(kind, box)
					: new BoxRecipe.PlacedTable(
							net.zamasoft.foliojet.layout.segment.TableParamsTemplate.freeze(table.getTableParams()),
							BoxRecipe.freeze(placement, block));
		}
		case FLOW, MULTICOL, INLINE, MARKER, FLOAT_BLOCK, INLINE_BLOCK, INSIDE_MARKER, TABLE_ROW_GROUP,
				TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, GRID, CAPTION, FLEX, ABSOLUTE ->
				BoxRecipe.freeze(kind, box);
		};
	}

	/** The hierarchy is not sealed, so enumerate known concrete classes and reject unknown subclasses too. */
	private static LayoutSource.BoxKind boxKind(final INonReplacedBox box) {
		return switch (box) {
		case net.zamasoft.foliojet.layout.box.impl.FlowBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class -> box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.TableCaptionPos
				? LayoutSource.BoxKind.CAPTION : LayoutSource.BoxKind.FLOW;
		case net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox.class -> LayoutSource.BoxKind.MULTICOL;
		case net.zamasoft.foliojet.layout.box.impl.GridBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.GridBox.class -> plainItemHost(box, LayoutSource.BoxKind.GRID);
		case net.zamasoft.foliojet.layout.box.impl.FlexBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FlexBox.class -> plainItemHost(box, LayoutSource.BoxKind.FLEX);
		case net.zamasoft.foliojet.layout.box.impl.InlineBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InlineBox.class -> LayoutSource.BoxKind.INLINE;
		case net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox.class -> LayoutSource.BoxKind.MARKER;
		case net.zamasoft.foliojet.layout.box.impl.FloatBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FloatBlockBox.class -> LayoutSource.BoxKind.FLOAT_BLOCK;
		case net.zamasoft.foliojet.layout.box.impl.InlineBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InlineBlockBox.class -> LayoutSource.BoxKind.INLINE_BLOCK;
		case net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox.class -> LayoutSource.BoxKind.INSIDE_MARKER;
		case net.zamasoft.foliojet.layout.box.impl.TableBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableBox.class -> LayoutSource.BoxKind.TABLE;
		case net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox.class -> LayoutSource.BoxKind.TABLE_ROW_GROUP;
		case net.zamasoft.foliojet.layout.box.impl.TableRowBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableRowBox.class -> LayoutSource.BoxKind.TABLE_ROW;
		case net.zamasoft.foliojet.layout.box.impl.TableCellBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableCellBox.class -> LayoutSource.BoxKind.TABLE_CELL;
		case net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox.class -> LayoutSource.BoxKind.TABLE_COLUMN_GROUP;
		case net.zamasoft.foliojet.layout.box.impl.TableColumnBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableColumnBox.class -> LayoutSource.BoxKind.TABLE_COLUMN;
		case net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox.class -> LayoutSource.BoxKind.ABSOLUTE;
		default -> throw unsupportedBox(box);
		};
	}

	private static LayoutSource.BoxKind plainItemHost(final INonReplacedBox box, final LayoutSource.BoxKind kind) {
		if (box.getPos().getClass() != net.zamasoft.foliojet.layout.box.params.FlowPos.class) throw unsupportedBox(box);
		return kind;
	}

	private static net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException unsupportedBox(
			final INonReplacedBox box) {
		return new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
				"Unsupported box recipe: box kind=" + box.getClass().getName() + " pos=" + box.getPos().getClass().getName());
	}
}
