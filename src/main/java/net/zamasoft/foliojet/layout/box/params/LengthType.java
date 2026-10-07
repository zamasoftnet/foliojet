package net.zamasoft.foliojet.layout.box.params;

/**
 * How a length value is specified. Shared by Length/Dimension/Insets/Offset.
 * <p>
 * MIXED is the result of calc() combining an absolute length and a percentage (e.g., calc(50% + 10px)).
 * Actual length = absolute + ratio * ref. Exactly four values fit in two-bit packing
 * (the flags in Dimension/Insets/Offset).
 * </p>
 */
public enum LengthType {
	ABSOLUTE, RELATIVE, AUTO, MIXED;

	static final LengthType[] VALUES = values();

	/**
	 * Whether obtaining the actual length requires a reference value (container size, etc.).
	 * RELATIVE (pure percentage) and MIXED (absolute length plus percentage via calc()) both depend
	 * on a reference value. ABSOLUTE/AUTO do not.
	 */
	public boolean needsReference() {
		return this == RELATIVE || this == MIXED;
	}
}
