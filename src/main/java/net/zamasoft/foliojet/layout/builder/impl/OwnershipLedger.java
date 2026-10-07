package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.builder.RetainedFlex;
import net.zamasoft.foliojet.layout.builder.RetainedGrid;
import net.zamasoft.foliojet.layout.builder.RetainedTable;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;

/** Tracks ownership of a TwoPass host's children and execution plans, and checks whether a range can absorb them. */
final class OwnershipLedger {
	enum Kind { STF, INLINE_BLOCK, INLINE_TABLE, TABLE, GRID, FLEX, PAGE_FLOAT, MARGIN_NOTE, FOOTNOTE, ABSOLUTE }

	enum State { RECORDING, REPLAY_ONLY, SEALED, EMPTY, DETACHED, CONSUMED, SUBSUMED, ABANDONED }

	/** Does not acquire a lease. Plan ranges have no handle. */
	record SourceRange(LayoutSource source, long fromId, long toId, RangeHandle handle) {
		SourceRange(final RangeHandle handle) {
			this(handle.source(), handle.fromId(), handle.toId(), handle);
		}
	}

	sealed interface OwnerNode {
		TwoPass identity();
		TwoPassBlockBuilder parent();
		Kind kind();
		SourceRange sourceRange();
		State state();
		TwoPass retainedPlan();
		long anchor();

		default OwnerNode terminal(final State state) {
			return new TerminalNode(this.anchor(), state, this.kind());
		}
	}

	/** Does not fix the anchor at registration. Also reflects anchors assigned to root boxes later. */
	record LiveNode(TwoPass identity, TwoPassBlockBuilder parent, Kind kind, SourceRange sourceRange,
			State state, TwoPass retainedPlan) implements OwnerNode {
		public long anchor() {
			return anchorOf(this.identity);
		}
	}

	/** At termination, keeps only the anchor, classification, and state; retains no builder/box/plan/source. */
	record TerminalNode(long anchor, State state, Kind kind) implements OwnerNode {
		public TwoPass identity() { return null; }
		public TwoPassBlockBuilder parent() { return null; }
		public SourceRange sourceRange() { return null; }
		public TwoPass retainedPlan() { return null; }
	}

	private static long anchorOf(final TwoPass identity) {
		return switch (identity) {
		case TwoPassBlockBuilder child -> child.getRootBox().getSourceAnchor();
		case RetainedTable table -> table.getSourceAnchor();
		case RetainedGrid grid -> grid.getGridBox().getSourceAnchor();
		case RetainedFlex flex -> flex.getFlexBox().getSourceAnchor();
		default -> throw new IllegalArgumentException("Unknown ownership identity: " + identity);
		};
	}

	/** For tests only. Observes ownership at entry to validation; normally does not retain the whole range. */
	private static volatile java.util.function.Consumer<TwoPassBlockBuilder> collectionObserver;

	private final TwoPassBlockBuilder owner;
	private List<OwnerNode> nodes = List.of();
	private OwnershipLedger parentLedger;
	private State state = State.RECORDING;
	private SourceRange sourceRange;

	OwnershipLedger(final TwoPassBlockBuilder owner) {
		this.owner = owner;
	}

	List<OwnerNode> nodes() {
		return List.copyOf(this.nodes);
	}

	void addChild(final TwoPassBlockBuilder child, final Kind kind) {
		if (this.nodes.isEmpty()) this.nodes = new ArrayList<>();
		final OwnershipLedger ledger = child.ownershipLedger();
		ledger.parentLedger = this;
		final OwnerNode node = new LiveNode(child, this.owner, kind, ledger.sourceRange, ledger.state, null);
		this.nodes.add(switch (ledger.state) {
		case DETACHED, CONSUMED, SUBSUMED, ABANDONED -> node.terminal(ledger.state);
		default -> node;
		});
	}

	void addPlan(final TwoPass plan, final Kind kind) {
		if (this.nodes.isEmpty()) this.nodes = new ArrayList<>();
		// The synthetic InlineBlockBox for an inline-table has no anchor, so use the plan's root box.
		final long anchor = anchorOf(plan);
		SourceRange range = null;
		final RootBuilder root = this.owner.layoutStack == null ? null : this.owner.getPageContext();
		if (root != null) {
			final LayoutSource log = root.getPageGenerator().getLayoutSource();
			final long end = log == null || anchor < 0 ? -1 : log.endOf(anchor);
			if (end >= anchor && anchor >= 0) {
				range = new SourceRange(log, anchor, end, null);
			}
		}
		this.nodes.add(new LiveNode(plan, this.owner, kind, range, State.RECORDING, plan));
	}

	void plansSubsumed() {
		// After acquiring the parent lease and absorbing all children, the ownership proof has moved
		// to absoluteAnchors. Do not retain child builders/measurers from terminal nodes.
		this.nodes = List.of();
	}

	void bodyChanged(final State state, final RangeHandle handle) {
		if (this.sourceRange != null && this.sourceRange.handle() != null) {
			this.sourceRange.handle().observeOwnerState(null);
		}
		this.state = state;
		if (handle != null) {
			this.sourceRange = new SourceRange(handle);
		} else {
			this.sourceRange = null;
		}
		this.updateParent();
		if (handle != null) {
			handle.observeOwnerState(this::rangeTerminated);
		}
	}

	private void rangeTerminated(final RangeHandle.State terminal) {
		this.state = switch (terminal) {
		case OPEN -> State.SEALED;
		case CONSUMED -> State.CONSUMED;
		case SUBSUMED -> State.SUBSUMED;
		case ABANDONED -> State.ABANDONED;
		};
		this.sourceRange = null;
		this.updateParent();
	}

	void bound() {
		this.state = State.CONSUMED;
		this.sourceRange = null;
		this.updateParent();
	}

	private void updateParent() {
		if (this.parentLedger == null) {
			return;
		}
		for (int i = 0; i < this.parentLedger.nodes.size(); ++i) {
			final OwnerNode node = this.parentLedger.nodes.get(i);
			if (node.identity() == this.owner) {
				this.parentLedger.nodes.set(i, switch (this.state) {
				case DETACHED, CONSUMED, SUBSUMED, ABANDONED -> node.terminal(this.state);
				default -> new LiveNode(node.identity(), node.parent(), node.kind(),
						this.sourceRange, this.state, node.retainedPlan());
				});
			}
		}
	}

	static void observeCollection(final TwoPassBlockBuilder owner) {
		final var observer = collectionObserver;
		if (observer != null) observer.accept(owner);
	}

	boolean collectAbsorbable(final LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<RangeHandle> outRanges, final Set<Long> ownedAbsoluteAnchors,
			final Set<TwoPassBlockBuilder> seen) {
		observeCollection(this.owner);
		for (final OwnerNode node : this.nodes) {
			if (node.identity() == null) return false; // A terminated child cannot be absorbed again.
			switch (node.kind()) {
			case INLINE_TABLE, TABLE -> {
				// PlacedTable also owns the same plan as the table and inline measurement tokens.
				// Shared validation also collects anchors for absolute tables. Incremental plans occur only
				// in main FLOW, never enter recording hosts, and seal cell bodies individually.
				if (!(node.retainedPlan() instanceof RetainedTableBuilder table)
						|| !TwoPassBlockBuilder.collectAbsorbableTable(table, log, fromId, toId, out, outTables,
								outRanges, ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			}
			case GRID -> {
				if (!(node.retainedPlan() instanceof GridBuilder grid)
						|| !grid.collectAbsorbableItems(log, fromId, toId, out, outTables, outRanges,
								ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			}
			case FLEX -> {
				if (!(node.retainedPlan() instanceof FlexBuilder flex)
						|| !flex.collectAbsorbableItems(log, fromId, toId, out, outTables, outRanges,
								ownedAbsoluteAnchors, seen)) {
					return false;
				}
				continue;
			}
			case ABSOLUTE -> {
				if (!(node.identity() instanceof TwoPassBlockBuilder child)
						|| !(child.getRootBox() instanceof AbsoluteBlockBox box)) {
					return false;
				}
				final long anchor = box.getSourceAnchor();
				if (anchor < fromId || anchor > toId
						|| !(log.get(anchor) instanceof LayoutSource.Start start
								&& start.recipe() instanceof BoxRecipe.Absolute)
						|| !box.isUnattachedForParentRange() || !ownedAbsoluteAnchors.add(anchor)) {
					return false;
				}
			}
			case STF, INLINE_BLOCK, PAGE_FLOAT, MARGIN_NOTE, FOOTNOTE -> {
				// Validate the child's ownership state below.
			}
			}
			if (!(node.identity() instanceof TwoPassBlockBuilder child)
					|| !collectSelf(child, log, fromId, toId, out, outTables, outRanges,
							ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		return true;
	}

	static boolean collectSelf(final TwoPassBlockBuilder owner, final LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<RangeHandle> outRanges, final Set<Long> ownedAbsoluteAnchors,
			final Set<TwoPassBlockBuilder> seen) {
		if (!seen.add(owner)) {
			return true;
		}
		// Validate sealed, empty, and unfinalized children according to their respective ownership states.
		switch (owner.bodyState()) {
		case SEALED -> {
			final RangeHandle range = owner.rangeHandle();
			if (range.source() != log || range.fromId() < fromId || range.toId() > toId || range.hasTextSlice()) {
				return false;
			}
			for (final long anchor : owner.rangeOwnedAbsoluteAnchors()) {
				if (!ownedAbsoluteAnchors.add(anchor)) {
					return false;
				}
			}
		}
		case RECORDING -> {
			if (!owner.collectAbsorbableChildren(log, fromId, toId, out, outTables, outRanges, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		case EMPTY -> { }
		case REPLAY_ONLY, DETACHED, CONSUMED, SUBSUMED, ABANDONED -> { return false; }
		}
		out.add(owner);
		return true;
	}

	State state() {
		return this.state;
	}

}
