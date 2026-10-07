package net.zamasoft.foliojet.css.value;

/**
 * A {@code flex-basis} value (Flex F1a, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt Q2). {@code auto | content |
 * <length-percentage>}. The size retains a {@link QuantityValue} (length or percentage),
 * with lengths made absolute at the computed-value stage, rather than reducing it to a double early
 * (percentages resolve against the container's main axis at use time).
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexBasisValue implements Value {
	/** {@code auto} (delegates to the width/height property: §7.2.3). */
	public static final FlexBasisValue AUTO_VALUE = new FlexBasisValue(null);

	/** {@code content} (the content's maximum intrinsic size: §7.2.3). */
	public static final FlexBasisValue CONTENT_VALUE = new FlexBasisValue(null);

	/** The size (null for auto/content). */
	private final QuantityValue size;

	private FlexBasisValue(final QuantityValue size) {
		this.size = size;
	}

	public static FlexBasisValue size(final QuantityValue size) {
		return new FlexBasisValue(size);
	}

	public boolean isAuto() {
		return this == AUTO_VALUE;
	}

	public boolean isContent() {
		return this == CONTENT_VALUE;
	}

	public QuantityValue getSize() {
		return this.size;
	}

	@Override
	public String toString() {
		if (this == AUTO_VALUE) {
			return "auto";
		}
		if (this == CONTENT_VALUE) {
			return "content";
		}
		return String.valueOf(this.size);
	}
}
