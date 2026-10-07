package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * A worklist unit for the iterative implementation of {@link IBox#getText}
 * (2026-07-20, for the same reason as draw). Text extraction must preserve document order.
 * Where local text appends alternate with delegation to child boxes in the same loop
 * ({@link AbstractTextBox#pushGetTextSteps}), follow the same convention as draw:
 * assemble steps in their original execution order, then push in **reverse order**.
 */
@FunctionalInterface
public interface GetTextStep {
	void run(Deque<GetTextStep> worklist);
}
