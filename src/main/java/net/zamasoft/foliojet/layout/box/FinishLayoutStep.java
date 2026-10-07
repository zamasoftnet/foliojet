package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * One worklist step in the iterative implementation of {@link IBox#finishLayout}
 * (2026-07-20, to follow the no-recursion policy: ARCHITECTURE.md invariant 6).
 *
 * <p>
 * The old implementation used polymorphic direct recursion from
 * {@code IBox.finishLayout(IFramedBox)} to children, causing StackOverflowError in deeply nested
 * documents (over 1000 levels; confirmed on an actual legislation page). This interface supports
 * replacing that recursion with iterative DFS using an explicit {@link Deque} worklist
 * instead of the JVM call stack. Its contract is "execute this step and push subsequent
 * steps onto {@code worklist} if needed."
 * </p>
 *
 * <p>
 * To preserve the original recursive traversal order (depth-first, siblings from the start),
 * implementations that enqueue multiple children must {@code push} in **reverse order**
 * (the worklist is a stack, so the last pushed item is popped first).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
@FunctionalInterface
public interface FinishLayoutStep {
	/**
	 * Executes this step. Pushes subsequent steps (child processing) onto
	 * {@code worklist}, if any.
	 */
	void run(Deque<FinishLayoutStep> worklist);
}
