package net.zamasoft.foliojet.layout.fragment;

/**
 * The open shape at the end of a continuation (M3b Phase 3c: types the former int depth convention).
 *
 * <p>
 * Represents a moved-open box chain (carrying write-once fragments, approved in ARCHITECTURE §5.5)
 * as nested OpenChains, and open text (slice handoff) as OpenText.
 * The former "depth n" corresponds to Chain^(n-1)(Text), making invalid states such as negative depth
 * unrepresentable. depth() derives the old value for compatibility with tracing and watermark traversal.
 * </p>
 */
public sealed interface OpenShape {
	/** Everything is closed (former depth=0). */
	record Closed() implements OpenShape {
	}

	/** The tail is open text (former depth=1). */
	record OpenText() implements OpenShape {
	}

	/** The tail is a moved-open box. inner is its inner shape (former depth-1). */
	record OpenChain(OpenShape inner) implements OpenShape {
	}

	OpenShape CLOSED = new Closed();
	OpenShape TEXT = new OpenText();

	/** Converts from the former depth convention (0=Closed, 1=Text, n=Chain^(n-1)(Text)). */
	static OpenShape of(final int depth) {
		OpenShape shape = depth <= 0 ? CLOSED : TEXT;
		for (int i = 1; i < depth; ++i) {
			shape = new OpenChain(shape);
		}
		return shape;
	}

	/**
	 * The value under the former depth convention (for trace display and watermark traversal compatibility).
	 *
	 * <p>
	 * 2026-07-21: The recursive implementation (1 + inner.depth()) overlooked that the depth guard itself
	 * could reach StackOverflowError on a deep OpenChain
	 * (noted in the ChatGPT Pro consultation; design consultation).
	 * A safety net is useless if calculating its guard value fails first,
	 * so this was changed to iteration with an explicit cursor.
	 * </p>
	 */
	default int depth() {
		int depth = 0;
		OpenShape cursor = this;
		while (cursor instanceof OpenChain(final OpenShape inner)) {
			++depth;
			cursor = inner;
		}
		return depth + (cursor instanceof OpenText ? 1 : 0);
	}
}
