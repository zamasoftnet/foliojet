package net.zamasoft.foliojet.css.value;

/**
 * {@code target-text(target, target-property?)}. In v1,
 * only {@code content} (the default) is supported for {@code target-property}
 * (see CSS-SUPPORT.md).
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

	public String toString() {
		return "target-text(" + this.ref + ")";
	}
}
