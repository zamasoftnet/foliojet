package net.zamasoft.foliojet.css.value;

/**
 * {@code flex-basis}の1値です(Flex F1a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2)。{@code auto | content |
 * <length-percentage>}。寸法はcomputed時に絶対化された
 * {@link QuantityValue}(長さまたはパーセント)を保持し、doubleへ早期に
 * 潰さない(パーセントは使用時にコンテナ主軸で解決)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexBasisValue implements Value {
	/** {@code auto}(width/heightプロパティへ委譲——§7.2.3)。 */
	public static final FlexBasisValue AUTO_VALUE = new FlexBasisValue(null);

	/** {@code content}(内容の最大内在サイズ——§7.2.3)。 */
	public static final FlexBasisValue CONTENT_VALUE = new FlexBasisValue(null);

	/** 寸法(auto/content時はnull)。 */
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
