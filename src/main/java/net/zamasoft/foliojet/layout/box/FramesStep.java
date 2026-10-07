package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * A worklist unit for the iterative implementation of {@code frames}
 * (the background/border drawing pass; 2026-07-20, for the same reason as draw).
 * {@code frames} is not common to all {@link IBox} types: it exists independently in
 * the {@link AbstractContainerBox} hierarchy (blocks and table cells) and the internal table
 * hierarchy ({@link AbstractInnerTableBox}: rows, row groups, columns, and column groups).
 * Each hierarchy's entry method creates and consumes a worklist of this type.
 */
@FunctionalInterface
public interface FramesStep {
	void run(Deque<FramesStep> worklist);
}
