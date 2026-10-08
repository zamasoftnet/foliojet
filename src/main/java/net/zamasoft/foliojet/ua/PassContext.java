package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * Holds objects related to the current pass.
 */
public class PassContext {
	private final SectionState sectionState = new SectionState();
	private final PageAssignmentState<String> stringState = new PageAssignmentState<String>();
	private final BuildStringState buildStringState = new BuildStringState();
	private final net.zamasoft.foliojet.css.style.running.RunningRegistry runningRegistry =
			new net.zamasoft.foliojet.css.style.running.RunningRegistry();

	/** Owner of placement anchors shared by running and string-set. */
	public net.zamasoft.foliojet.css.style.running.RunningRegistry getRunningRegistry() {
		return this.runningRegistry;
	}

	public PageAssignmentState<net.zamasoft.foliojet.css.style.running.RunningTemplate> getRunningState() {
		return this.runningRegistry.state();
	}

	/** For forward references from generated content in body text. Separate from already placed page state. */
	public BuildStringState getBuildStringState() {
		return this.buildStringState;
	}

	/** Body-reference state retaining read-ahead assignments when an already placed page ends. */
	public static final class BuildStringState {
		private static final class Candidates {
			final java.util.TreeMap<Long, String> pending = new java.util.TreeMap<Long, String>();
			final java.util.Set<Long> committed = new java.util.HashSet<Long>();
			PageAssignmentState.Assignment<String> latest, entry, pageLast;
		}

		private final java.util.Map<String, Candidates> names = new java.util.HashMap<String, Candidates>();

		public void begin(final String name, final long order) {
			this.names.computeIfAbsent(name, key -> new Candidates()).pending.putIfAbsent(order, null);
		}

		public void assign(final String name, final String value, final long order, final boolean beginsPage) {
			final Candidates c = this.names.computeIfAbsent(name, key -> new Candidates());
			c.pending.put(order, value);
			if (c.latest == null || order >= c.latest.order()) {
				c.latest = new PageAssignmentState.Assignment<String>(order, value, false);
			}
		}

		/** Completed content() values update the same order; an old page does not roll back the latest value. */
		public void complete(final String name, final String value, final long order) {
			this.assign(name, value, order, false);
			final Candidates c = this.names.get(name);
			c.committed.add(order);
			if (c.pageLast == null || order >= c.pageLast.order()) {
				c.pageLast = new PageAssignmentState.Assignment<String>(order, value, false);
			}
		}

		public PageAssignmentState.Resolution<String> resolve(final String name, final PageAssignmentState.Mode mode) {
			final Candidates c = this.names.get(name);
			if (c == null) {
				return new PageAssignmentState.Resolution<String>(PageAssignmentState.Presence.ABSENT, null);
			}
			if (mode == PageAssignmentState.Mode.FIRST_EXCEPT && !c.pending.isEmpty()) {
				return new PageAssignmentState.Resolution<String>(PageAssignmentState.Presence.SUPPRESSED, null);
			}
			final String value = switch (mode) {
			case LAST -> c.latest == null ? null : c.latest.value();
			case FIRST -> !c.pending.isEmpty() ? c.pending.firstEntry().getValue()
					: c.entry == null ? null : c.entry.value();
			case START, FIRST_EXCEPT -> c.entry == null ? null : c.entry.value();
			};
			return new PageAssignmentState.Resolution<String>(value == null
					? PageAssignmentState.Presence.ABSENT : PageAssignmentState.Presence.VALUE, value);
		}

		public void endPage() {
			for (final Candidates c : this.names.values()) {
				if (c.pageLast != null) {
					c.entry = c.pageLast;
					c.pageLast = null;
				}
				c.committed.forEach(c.pending::remove);
				c.committed.clear();
			}
		}
	}
	private CSSElement pageSide;
	private CounterContext counterContext = null;
	private int pageNumber = 0;

	public SectionState getSectionState() {
		return this.sectionState;
	}

	public PageAssignmentState<String> getStringState() {
		return this.stringState;
	}

	public CSSElement getPageSide() {
		return this.pageSide;
	}

	public void setPageSide(CSSElement pageSide) {
		this.pageSide = pageSide;
	}
	
	public void resetNonPageCounters() {
		if (this.counterContext == null) {
			return;
		}
		this.counterContext.resetNonPageCounters();
	}

	public CounterScope getCounterScope(int level, boolean create) {
		if (this.counterContext == null) {
			if (!create) {
				return null;
			}
			this.counterContext = new CounterContext();
		}
		return this.counterContext.getCounterScope(level, create);
	}

	public int getPageNumber() {
		return this.pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	/**
	 * The first ElementKey of the next document in this pass (2026-10-08). Keys count elements in document order and
	 * key the two-pass facts ({@code SelectorFacts}, {@code ContainerFacts}); when the spine items of an EPUB are laid
	 * out into one output, the keys of each item continue after the previous item's, so the facts of different items
	 * do not overwrite each other. Every pass reads the items in the same order, so the keys match between passes.
	 */
	private long elementKeyBase = 0;

	public long getElementKeyBase() {
		return this.elementKeyBase;
	}

	public void setElementKeyBase(final long elementKeyBase) {
		this.elementKeyBase = elementKeyBase;
	}

	/**
	 * The imposition shared by every document of this pass, or {@code null} (2026-10-08). The EPUB formatter sets it
	 * so the slug page number and n-up sheets continue across the spine items; see {@code Impositions}.
	 */
	private net.zamasoft.foliojet.layout.imposition.Imposition sharedImposition = null;

	public net.zamasoft.foliojet.layout.imposition.Imposition getSharedImposition() {
		return this.sharedImposition;
	}

	public void setSharedImposition(final net.zamasoft.foliojet.layout.imposition.Imposition imposition) {
		this.sharedImposition = imposition;
	}

	/** Documents whose start has been registered as a link target in this pass (see {@code AbstractVisitor}). */
	private final java.util.Set<java.net.URI> startedDocuments = new java.util.HashSet<>();

	/** Records the start of a document's output; {@code false} if it was already recorded in this pass. */
	public boolean startDocumentOutput(final java.net.URI document) {
		return this.startedDocuments.add(document);
	}
}
