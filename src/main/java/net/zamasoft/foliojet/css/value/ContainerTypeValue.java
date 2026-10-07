package net.zamasoft.foliojet.css.value;

/**
 * A {@code container-type} value (css-contain-3, 2026-08-15 stage 2;
 * development record §5).
 *
 * <p>
 * Accepts {@code size} as syntax, but containing dimensions through the block axis
 * conflicts with page breaking, so stages 1–3 do not treat it as a query container
 * (design §4: do not include `container-type: size` initially).
 * Retains the value itself and warns at the stage that actually consults
 * {@code ContainerFacts}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum ContainerTypeValue implements Value {
	NORMAL_VALUE(ContainerTypeValue.NORMAL),

	INLINE_SIZE_VALUE(ContainerTypeValue.INLINE_SIZE),

	SIZE_VALUE(ContainerTypeValue.SIZE);

	public static final byte NORMAL = 0;

	public static final byte INLINE_SIZE = 1;

	public static final byte SIZE = 2;

	private final byte containerType;

	private ContainerTypeValue(byte containerType) {
		this.containerType = containerType;
	}

	public byte getContainerType() {
		return this.containerType;
	}

	public String toString() {
		switch (this.containerType) {
		case NORMAL:
			return "normal";

		case INLINE_SIZE:
			return "inline-size";

		case SIZE:
			return "size";

		default:
			throw new IllegalStateException();
		}
	}
}
