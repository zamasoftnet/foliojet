package net.zamasoft.foliojet.layout.fragment;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Holds boxes open at the break (boxes on the builder's flowStack) only during a page/column cut (2026-10-07).
 *
 * <p>
 * Open boxes must not undergo rescue splitting (2026-09-16, {@code RelaxInside} in {@code FlowContainer} ).
 * Rescue visually cuts a box and replaces it with a closed remainder. Applied to an open box still receiving
 * content, it prevents flowStack from being rebuilt on resume, leaving it inconsistent with the continuation's
 * open depth. The previous check protected only chain boxes approved by the page-break plan ({@link
 * BreakPlan}).
 * It did not protect boxes that were open but absent from the plan: those beyond a barrier where the
 * capability scan stopped, paths where a column does not collect inner multi-column layout, or boundaries
 * such as {@code vertical-rl} versus {@code sideways-rl} (descent without a plan passes null).
 * Copy the open state here at the cut entry point, independently of the plan.
 * </p>
 *
 * <p>
 * Like the rescue-split decision, uses {@link ThreadLocal} to prevent concurrent conversions from interfering.
 * </p>
 */
public final class OpenBoxes {
	private static final ThreadLocal<Set<IBox>> OPEN = new ThreadLocal<>();

	/** Number of times rescue was blocked for an open box absent from the plan (sweep/test observation). */
	public static final AtomicLong UNSELECTED_RESCUES_PREVENTED = new AtomicLong();

	/** The cut scope. Closing restores the previous state. */
	public interface Scope extends AutoCloseable {
		@Override
		void close();
	}

	private OpenBoxes() {
		// unused
	}

	/**
	 * Treats {@code boxes} as open during the cut. Nested cuts (column cuts within a cut) also inherit outer
	 * boxes.
	 */
	public static Scope scope(final Collection<? extends IBox> boxes) {
		final Set<IBox> previous = OPEN.get();
		final Set<IBox> open = Collections.newSetFromMap(new IdentityHashMap<>());
		if (previous != null) {
			open.addAll(previous);
		}
		open.addAll(boxes);
		OPEN.set(open);
		return () -> {
			if (previous == null) {
				OPEN.remove();
			} else {
				OPEN.set(previous);
			}
		};
	}

	/** Whether the box is open at the break. Always false outside a cut. */
	public static boolean isOpen(final IBox box) {
		final Set<IBox> open = OPEN.get();
		return open != null && open.contains(box);
	}
}
