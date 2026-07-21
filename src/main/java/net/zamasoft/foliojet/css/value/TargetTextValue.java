package net.zamasoft.foliojet.css.value;

/**
 * {@code target-text(target, target-property?)}。v1では
 * {@code target-property}は{@code content}(既定)のみ対応
 * (CSS-SUPPORT.md参照)。
 *
 * @author MIYABE Tatsuhiko
 */
public class TargetTextValue implements Value {
	public static final byte CONTENT = 1;

	private final byte type;

	private final String ref;

	private final byte targetProperty;

	public TargetTextValue(byte type, String ref, byte targetProperty) {
		this.type = type;
		this.ref = ref;
		this.targetProperty = targetProperty;
	}

	public byte getType() {
		return this.type;
	}

	public String getRef() {
		return this.ref;
	}

	public byte getTargetProperty() {
		return this.targetProperty;
	}

	public String toString() {
		return "target-text(" + this.ref + ")";
	}
}
