package net.zamasoft.foliojet.layout.box.content;

/**
 * A continuation token (BreakToken) for a text block.
 * Represents resume information for page/column splits and subsequent text blocks in the same flow.
 *
 * <p>
 * M6b: Extended from an enum to a sealed type that carries positions. The total order is
 * None &lt; MidFlow &lt; MidLine; combination takes the stronger one.
 * MidFlow/MidLine charOffset is the source character offset at the resume position
 * (from pdfg2d Text.getCharOffset), used to map resume positions for segment replay
 * (segment-restyle). A combined token with an unknown position carries -1.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface BreakToken {
	/** The start of a flow (text-indent and :first-line apply). */
	public static final BreakToken NONE = new None();

	/** A mid-flow continuation with an unknown position (for combination). */
	public static final BreakToken MID_FLOW = new MidFlow(-1);

	record None() implements BreakToken {
	}

	/**
	 * A continuation from within a flow (suppresses text-indent and :first-line).
	 *
	 * @param charOffset the source character offset at the resume position (-1 if unknown)
	 */
	record MidFlow(int charOffset) implements BreakToken {
	}

	/**
	 * A continuation from within a line (within a word; connects to the preceding line for wrapping).
	 *
	 * @param charOffset the source character offset at the resume position (-1 if unknown)
	 */
	record MidLine(int charOffset) implements BreakToken {
	}

	/**
	 * Returns true for a mid-flow continuation.
	 *
	 * @return true if within a flow
	 */
	public default boolean midFlow() {
		return !(this instanceof None);
	}

	/**
	 * Returns true for a mid-line continuation.
	 *
	 * @return true if within a line
	 */
	public default boolean midLine() {
		return this instanceof MidLine;
	}

	private int rank() {
		return switch (this) {
		case None none -> 0;
		case MidFlow midFlow -> 1;
		case MidLine midLine -> 2;
		};
	}

	/**
	 * Combines two continuation states (takes the stronger one).
	 *
	 * @param other the continuation state to combine
	 * @return the combined result
	 */
	public default BreakToken combine(BreakToken other) {
		return this.rank() >= other.rank() ? this : other;
	}
}
