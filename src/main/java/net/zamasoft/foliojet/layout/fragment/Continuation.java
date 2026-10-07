package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * Page-break continuation description (ARCHITECTURE §5.7).
 *
 * <p>
 * Represents what to send to the next fragmentainer as a type. Its contents are nested root frames ({@link
 * ContinuationFrame}): each frame has a recipe, fragment state, remainder container, and absorbed replay ranges
 * (prefixItems), with open descendants represented by tail. The C0' compatibility wrapper (LegacyCarry, which
 * carried an entire remainder box tree) was removed on C1 completion. Carrying a remainder box tree no longer
 * exists as a concept for plain block chains.
 * </p>
 *
 * @param depth  ancestor-chain depth to validate after resumption (flowStack element count;
 *               traversal itself is driven by the tail type; scheduled for removal after C3 completion)
 * @param root   root frame
 * @param ranges replay ranges for closed subtrees (C2: decisions recorded together at the break.
 *               Resume traversal only consumes them, without recomputing gates.
 *               For nested subtrees with box fallback; reduced in C4)
 * @author MIYABE Tatsuhiko
 */
public record Continuation(int depth, ContinuationFrame root,
		java.util.Map<net.zamasoft.foliojet.layout.box.IBox, SourceRange> ranges) {
	/**
	 * Frame of a continuation fragment (C1d-A: unified form for root and chain fragments). split does not construct
	 * fragment boxes; resume reconstructs them from recipes and fragment state (C1d-B: does not retain the old box as
	 * a factory). Nested tails represent open descendants.
	 *
	 * @param recipe      recipe for reconstructing the fragment box (values captured at the cut)
	 * @param state       fragment state
	 * @param container   remainder container at this level (excludes the chain child)
	 * @param crossExtent cross-axis size at the cut
	 * @param prefixItems replay ranges for closed subtrees absorbed from the container (C1c)
	 * @param tail        representation of the open continuation
	 */
	public record ContinuationFrame(FragmentRecipe recipe, FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container, double crossExtent,
			List<SourceRange> prefixItems, OpenTail tail) {
	}

	/**
	 * Representation of a frame's open continuation (C1d-A).
	 */
	public sealed interface OpenTail {
		/**
		 * Next inner frame traversed by the cut.
		 */
		record Child(ContinuationFrame frame) implements OpenTail {
		}

		/**
		 * Open shape at the continuation tail (M3b Phase 3c made the old int depth convention explicit as a type). Held
		 * by the innermost collected frame (continuation of a moved-open box or open text remaining in the tree) and by
		 * the root frame of an uncollectable break.
		 *
		 * @param shape tail open shape passed to this frame's container traversal
		 */
		record OpenTailShape(OpenShape shape) implements OpenTail {
			public OpenTailShape {
				// Root = full flowStack depth; innermost = remaining depth (D - chain count).
				// Both are open (the chain is a subsequence of flowStack[1..]).
				// Shape became an explicit type in M3b Phase 3c (formerly the int depth convention).
				assert !(shape instanceof OpenShape.Closed) : shape;
			}
		}
	}

	/**
	 * Source range for a closed subtree moved as a whole (recorded in C2, carried as prefixItems in C1c).
	 *
	 * @param serial merge order in the original container (BoxHolder serial;
	 *               preserves relative order with floats; -1 for ranges-map use)
	 * @param fromId EventId of the subtree's StartBlock
	 * @param toId   EventId of the corresponding EndBlock
	 */
	public record SourceRange(int serial, long fromId, long toId) {
	}
}
