package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * Manages out-of-flow boxes together.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class Floatings {
	/**
	 * A placed float box.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
	 */
	public static class Floating extends BoxHolder {
		public final IFloatBox box;
		public final double lineAxis, pageAxis;
		/** One-time transfer to the next fragment, determined by intersection with the 2-D bottom band. */
		boolean moveToNext;

		public Floating(int serial, IFloatBox box, double lineAxis, double pageAxis) {
			this(serial, box, lineAxis, pageAxis, false);
		}

		Floating(int serial, IFloatBox box, double lineAxis, double pageAxis, boolean moveToNext) {
			super(serial);
			this.box = box;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
			this.moveToNext = moveToNext;
		}

		public IBox getBox() {
			return this.box;
		}

		/**
		 * Returns an immutable value with only the page-axis position translated,
		 * preserving the serial, box, line-axis position, and one-time transfer state.
		 */
		Floating shiftedPageAxis(final double dy) {
			return new Floating(this.serial, this.box, this.lineAxis, this.pageAxis + dy, this.moveToNext);
		}

		public void restyle(BlockBuilder builder) {
			switch (this.box.getType()) {
			case BLOCK: {
				// Block box
				// Anonymous box
				AbstractContainerBox floatBox = (AbstractContainerBox) this.box;
				if (DebugFlags.FLOAT_TRACE) {
					final net.zamasoft.foliojet.layout.box.content.Container c = floatBox.getContainer();
					System.err.println("[float] 再生 box=" + System.identityHashCode(floatBox) + " container="
							+ (c == null ? "null" : c.getClass().getSimpleName() + " flows="
									+ (c instanceof FlowContainer fc ? String.valueOf(fc.flowCountForDebug()) : "?")));
				}
				BlockBuilder floatBindBuilder = new BlockBuilder(builder, floatBox);
				floatBox.restyle(floatBindBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
				floatBindBuilder.close();
				builder.addBound(floatBox);
			}
				break;
			case REPLACED: {
				// Replaced box
				AbstractReplacedBox floatBox = (AbstractReplacedBox) this.box;
				builder.addBound(floatBox);
			}
				break;
			case RESCUE: {
				// 2026-07-25 (rescue splitting, increment 7): Remainder of a rescue fragment. The original box
				// is already laid out, so do not restyle its content; only redo placement
				// (recommendation §5: rerun normal float placement for the tail in the next fragment).
				// This uses the normal addBound path, so the order of side effects in
				// commitFloatPlacement remains completely unchanged.
				builder.addBound(this.box);
			}
				break;
			default:
				throw new IllegalStateException(this.box.toString());
			}
		}
	}

	/**
	 * Float boxes.
	 */
	private final List<Floating> floatings = new ArrayList<Floating>();

	/**
	 * Adds a float box.
	 *
	 * @param floating
	 */
	public void addFloating(Floating floating) {
		assert !LayoutUtils.isNone(floating.pageAxis) : "Undefined pageAxis";
		assert !LayoutUtils.isNone(floating.lineAxis) : "Undefined lineAxis";
		if (DebugFlags.FLOAT_TRACE) {
			final StringBuilder where = new StringBuilder();
			final StackTraceElement[] st = new Throwable().getStackTrace();
			for (int k = 1; k < Math.min(st.length, 8); ++k) {
				where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
			}
			System.err.println("[float] 台帳へ box=" + System.identityHashCode(floating.getBox()) + " 台帳="
					+ System.identityHashCode(this) + where);
		}
		this.floatings.add(floating);
	}

	/**
	 * Iterative draw (2026-07-20, for the same reason as IBox.draw). Pushes each float box's
	 * drawing steps onto {@code worklist} in **reverse order** to preserve traversal order.
	 */
	public void pushDraw(AbstractContainerBox box, PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		// Floats. Centralize logical-to-physical coordinate conversion in LayoutUtils.drawX/drawY
		// (2026-07-25, vertical-lr support; previously used handwritten RL-only formulas).
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = box.getBlockParams().flow;
		final double parentPageExtent = box.getInnerWidth();
		final double parentLineExtent = box.getInnerHeight();
		for (int i = this.floatings.size() - 1; i >= 0; --i) {
			Floating floating = (Floating) this.floatings.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(box.getBlockParams(), parentLineExtent,
					floating.lineAxis, floating.lineAxis + floating.box.getHeight());
			worklist.push(IBox.drawStep(floating.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(flow, x, parentPageExtent, floating.pageAxis,
							floating.pageAxis + floating.box.getWidth(), floating.lineAxis),
					LayoutUtils.drawY(flow, y, floating.pageAxis, lineStart)));
		}
	}

	/** Detaches column footnotes for collection before balancing (increment 6). Returns false if not found. */
	public boolean removeFloating(final IFloatBox box) {
		for (int i = 0; i < this.floatings.size(); ++i) {
			if (this.floatings.get(i).box == box) {
				this.floatings.remove(i);
				return true;
			}
		}
		return false;
	}

	public int getCount() {
		return this.floatings.size();
	}

	public Floating getFloating(int i) {
		return (Floating) this.floatings.get(i);
	}

	/**
	 * Translates the page-axis positions of all floats. Preserves list order, serials, boxes,
	 * line-axis positions, and {@link Floating#moveToNext}. Entries for boxes in {@code keep}
	 * remain the same instances.
	 *
	 * @param dy   the translation along the page axis
	 * @param keep boxes to leave at their current positions (an identity-based set)
	 */
	public void shiftPageAxis(final double dy, final java.util.Set<IBox> keep) {
		for (int i = 0; i < this.floatings.size(); ++i) {
			final Floating floating = this.floatings.get(i);
			if (!keep.contains(floating.box)) {
				this.floatings.set(i, floating.shiftedPageAxis(dy));
			}
		}
	}

	/**
	 * Collects measurements of all floats in their original order (2026-07-24, P2-1; read-only,
	 * affecting neither this list nor the boxes). ordinal is the stable ordinal at collection
	 * time (= the index in this list).
	 *
	 * @param ownerFlow the owner's writing direction
	 * @return the immutable list of measurements
	 */
	public List<FloatMeasurement> measure(final net.zamasoft.foliojet.layout.box.params.WritingMode ownerFlow) {
		final List<FloatMeasurement> measurements = new ArrayList<>(this.floatings.size());
		for (int i = 0; i < this.floatings.size(); ++i) {
			measurements.add(FloatMeasurement.of(i, (Floating) this.floatings.get(i), ownerFlow));
		}
		return java.util.Collections.unmodifiableList(measurements);
	}

	/**
	 * Paginates float boxes (2026-07-24). P2-3 switched to plan-driven commit; P2-5 removed
	 * the adapter for the old sentinel contract (null=KeepAll / this=MoveAll / new=Partition),
	 * unifying on typed results. The authoritative branch table is in the development log.
	 *
	 * <p>
	 * Two phases: classify via {@link FloatSplitPlan#planDirect} (pure decisions, no side effects),
	 * then commit according to the plan (codex design §2.3). Commit runs once in ordinal order.
	 * {@code SplitOnCommit} executes {@code containerBox.splitFloatFragment} exactly once here,
	 * resolving to Keep/Move/Prepared (A-3a-2: do not build the remainder box immediately;
	 * materialize it once when attached to the receiving Floating). Equivalence with the old
	 * implementation is locked down by the branch-table tests in {@code FloatingsSplitPageAxisTest},
	 * the P2-2 shadow comparison (0 mismatches in the SMOKE corpus), and the twin equivalence
	 * tests in {@code PreparedFloatFragmentTest}.
	 * </p>
	 *
	 * <p>
	 * Result meanings (see {@link FloatSplitResult}):
	 * KeepAll/MoveAll leave the original list untouched (MoveAll is deferred; the owner reassigns
	 * the ledger). Only Partition rebuilds the original list as "KEEP + SPLIT sources" and returns
	 * a remainder ledger (original MOVE Floatings + SPLIT remainders, in original order).
	 * </p>
	 */
	public FloatSplitResult splitPageAxis(final AbstractContainerBox box, final double pageLimit,
			final byte flags) {
		assert !this.floatings.isEmpty();
		if (DebugFlags.FLOAT_TRACE) {
			final StringBuilder where = new StringBuilder();
			final StackTraceElement[] st = new Throwable().getStackTrace();
			for (int k = 1; k < Math.min(st.length, 9); ++k) {
				where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
			}
			System.err.println("[float] 分割呼出 台帳=" + System.identityHashCode(this) + " 数="
					+ this.floatings.size() + where);
		}
		// Final snapshot at entry (lesson from the addBound incident; codex design §2.5).
		// Finalize classification for every float here. The old implementation classified float i+1
		// after splitting float i, but each float box is independent and a split does not affect other
		// floats' measurements, so this is equivalent (confirmed by the P2-2 shadow comparison).
		final int originalFloatCount = this.floatings.size();
		final FloatSplitPlan plan = FloatSplitPlan.planDirect(this, box.getBlockParams().flow, pageLimit, flags);
		assert plan.direct().size() == originalFloatCount;
		// Commit (codex design §2.3): Once in ordinal order. Build the source list (KEEP +
		// SPLIT sources) and the remainder list (MOVE + SPLIT remainders).
		// ordinal is stable; no index mutation via remove/--i as in the old implementation.
		final List<Floating> sourceSide = new ArrayList<Floating>(originalFloatCount);
		final List<Floating> remainderSide = new ArrayList<Floating>();
		boolean allWholeMoves = true;
		for (int ordinal = 0; ordinal < originalFloatCount; ++ordinal) {
			final Floating floating = (Floating) this.floatings.get(ordinal);
			final FloatSplitPlan.FloatItemPlan item = plan.direct().get(ordinal);
			assert item.expected().box() == floating.box : "plan/commitのidentity不一致 ordinal=" + ordinal;
			switch (item) {
			case FloatSplitPlan.FloatItemPlan.Keep keep -> {
				// Branch table 1 and first in the 4→5 fall-through: Keep in the source.
				sourceSide.add(floating);
				allWholeMoves = false;
			}
			case FloatSplitPlan.FloatItemPlan.Move move -> {
				// Branch table 2 and non-first in the 4→5 fall-through: Move the whole float.
				// Consume a forced transfer passed from placement exactly once here.
				floating.moveToNext = false;
				remainderSide.add(floating);
			}
			case FloatSplitPlan.FloatItemPlan.RescueOnCommit(final FloatMeasurement rescued,
					final net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice) -> {
				// Branch table 5-R (2026-07-25, rescue splitting, increment 7): Put head in the source ledger
				// and tail in the remainder ledger (recommendation §5). Leave the original box untouched.
				// Fragments are short-lived decorators that only clip and translate at draw time;
				// layout dimensions remain unchanged.
				final net.zamasoft.foliojet.layout.box.IFloatBox source;
				final double sourcePageExtent;
				if (floating.box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox fragment) {
					// Continuation of an already rescued fragment (do not create fragments of fragments)
					source = (net.zamasoft.foliojet.layout.box.IFloatBox) fragment.getSource();
					sourcePageExtent = fragment.getSourcePageExtent();
				} else {
					source = floating.box;
					sourcePageExtent = rescued.pageExtent();
				}
				final net.zamasoft.foliojet.layout.box.params.WritingMode progression = plan.ownerFlow();
				final double tailOffset = slice.nextOffset();
				final double tailExtent = sourcePageExtent - tailOffset;
				// Progress is guaranteed (established by the plan: RescueOnCommit rejects lastFragment,
				// and FloatSplitPlan.rescue also checks at runtime that the remainder is >0).
				// If violated, the VisualRescueFloatBox constructor fails immediately,
				// so no infinite loop is possible.
				assert tailOffset > slice.offset() && tailExtent > 0 : slice;
				// head stays at the original position (the exclusion area's page-axis height becomes sliceExtent).
				// tail enters the remainder ledger at (0,0), the next fragment start, with its serial inherited,
				// and undergoes normal float placement again in the next fragment.
				sourceSide.add(new Floating(floating.serial,
						new net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox(source, progression,
								sourcePageExtent, slice.offset(), slice.sliceExtent()),
						floating.lineAxis, floating.pageAxis));
				remainderSide.add(new Floating(floating.serial,
						new net.zamasoft.foliojet.layout.rescue.VisualRescueFloatBox(source, progression,
								sourcePageExtent, tailOffset, tailExtent),
						0, 0));
				allWholeMoves = false;
			}
			case FloatSplitPlan.FloatItemPlan.SplitOnCommit(final FloatMeasurement expected, final double innerLimit,
					final byte splitFlags) -> {
				// Branch table 3: Split exactly once here and resolve to Keep/Move/Prepared.
				// A-3a-2: Do not build the remainder box immediately inside the split. Receive its material
				// (PreparedFloatFragment) and materialize it exactly once here, when attaching it
				// to the receiving Floating (construction uses the same continueFragment
				// as the old immediate path).
				final net.zamasoft.foliojet.layout.box.AbstractBlockBox containerBox = (net.zamasoft.foliojet.layout.box.AbstractBlockBox) floating.box;
				switch (containerBox.splitFloatFragment(floating.serial, innerLimit, BreakMode.DEFAULT_BREAK_MODE,
						splitFlags)) {
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Keep keep -> {
					sourceSide.add(floating);
					allWholeMoves = false;
				}
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Move move ->
					remainderSide.add(floating);
				case net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
						final net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment fragment) -> {
					// The original Floating stays on this side; the remainder moves to next at (0,0),
					// the next fragment start, with its serial inherited.
					sourceSide.add(floating);
					final net.zamasoft.foliojet.layout.box.IFloatBox tailBox = fragment.materialize();
					if (DebugFlags.FLOAT_TRACE) {
						System.err.println("[float] 残余断片 元=" + System.identityHashCode(floating.getBox()) + " 断片="
								+ System.identityHashCode(tailBox));
					}
					remainderSide.add(new Floating(fragment.serial(), tailBox, 0, 0));
					allWholeMoves = false;
				}
				}
			}
			}
		}
		final FloatSplitResult result;
		if (remainderSide.isEmpty()) {
			// All KEEP: Leave the original list untouched.
			result = FloatSplitResult.KEEP_ALL;
		} else if (allWholeMoves) {
			// All floats MOVE in their entirety: Deferred representation (leave them in the original list;
			// the owner reassigns the entire ledger).
			result = FloatSplitResult.MOVE_ALL;
		} else {
			this.floatings.clear();
			this.floatings.addAll(sourceSide);
			final Floatings remainder = new Floatings();
			remainder.floatings.addAll(remainderSide);
			result = new FloatSplitResult.Partition(remainder);
		}
		// Check that the committed classification agrees with the plan (replaced the P2-2 shadow comparison
		// in P2-3; only FINE diagnostics in production with assertions disabled).
		final boolean consistent = commitConsistentWithPlan(plan, result);
		assert consistent : "commit結果がplanと不整合: pageLimit=" + pageLimit + " flags=" + flags;
		assert !(result instanceof FloatSplitResult.Partition(final Floatings r) && r.floatings.isEmpty());
		return result;
	}

	/**
	 * Checks whether the committed classification agrees with the plan's classification (P2-3).
	 * Relax the constraints for {@code SplitOnCommit}, which does not predict its result:
	 * a plan containing Move cannot yield KeepAll; one containing Keep cannot yield MoveAll;
	 * Partition cannot occur without Move or SplitOnCommit. Also log inconsistencies at FINE
	 * so they remain observable in production with assertions disabled.
	 */
	private static boolean commitConsistentWithPlan(final FloatSplitPlan plan, final FloatSplitResult result) {
		boolean anyKeepPlan = false;
		boolean anyMovePlan = false;
		boolean anySplitPlan = false;
		for (final FloatSplitPlan.FloatItemPlan item : plan.direct()) {
			switch (item) {
			case FloatSplitPlan.FloatItemPlan.Keep keep -> anyKeepPlan = true;
			case FloatSplitPlan.FloatItemPlan.Move move -> anyMovePlan = true;
			case FloatSplitPlan.FloatItemPlan.SplitOnCommit splitOnCommit -> anySplitPlan = true;
			// Treat rescue like splitting, since it also places entries on both the source and remainder sides.
			case FloatSplitPlan.FloatItemPlan.RescueOnCommit rescueOnCommit -> anySplitPlan = true;
			}
		}
		final boolean consistent = switch (result) {
		case FloatSplitResult.KeepAll keepAll -> !anyMovePlan;
		case FloatSplitResult.MoveAll moveAll -> !anyKeepPlan;
		case FloatSplitResult.Partition partition -> anyMovePlan || anySplitPlan;
		};
		if (!consistent) {
			java.util.logging.Logger.getLogger(Floatings.class.getName())
					.fine(() -> "FloatSplit commit/plan不整合: plan=" + plan + " result=" + result);
		}
		return consistent;
	}

	public String toString() {
		return super.toString() + ": floatings.size=" + this.floatings.size();
	}
}
