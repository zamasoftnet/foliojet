package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * A worklist unit for the iterative implementation of {@link IBox#textShape}
 * (2026-07-20, for the same reason as draw). textShape does not need push-order control
 * as strict as draw, since it only accumulates geometry in a clipping {@code GeneralPath}
 * and drawing order does not matter. It follows the same convention (push children
 * in **reverse order**) to keep the implementation consistent with the other iterative methods.
 */
@FunctionalInterface
public interface TextShapeStep {
	void run(Deque<TextShapeStep> worklist);
}
