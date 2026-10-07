package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * A worklist unit for the iterative implementation of {@link IBox#draw}
 * (2026-07-20, ARCHITECTURE.md invariant 6; it caused StackOverflowError for the same
 * reason as finishLayout).
 *
 * <p>
 * Unlike finishLayout, drawing is not a simple split into "local processing, then delegate
 * to children": some loops alternate local drawing (such as text runs) with child drawing
 * (e.g., {@link AbstractTextBox#pushDrawSteps}). Each box type therefore assembles its
 * steps (local drawing closures and child box steps) in their original execution order,
 * then pushes them onto {@code worklist} in **reverse order**
 * (see {@link IBox#pushDrawSteps}).
 * </p>
 */
@FunctionalInterface
public interface DrawStep {
	void run(Deque<DrawStep> worklist);
}
