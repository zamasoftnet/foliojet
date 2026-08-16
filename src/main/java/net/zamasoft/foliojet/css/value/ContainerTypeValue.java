package net.zamasoft.foliojet.css.value;

/**
 * {@code container-type}の値です(css-contain-3、2026-08-15段2——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 *
 * <p>
 * {@code size}は構文としては受理するが、寸法をブロック軸まで含めて
 * containしてしまい改ページと直交するため、段1〜3の実装では
 * クエリコンテナとして扱わない(設計§4「`container-type: size`は
 * 初回に入れない」)。値そのものは保持し、警告は実際に
 * {@code ContainerFacts}を参照する段で出す。
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
