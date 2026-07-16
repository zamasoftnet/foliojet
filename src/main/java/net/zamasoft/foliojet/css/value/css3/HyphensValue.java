package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.css.value.Value;

/**
 * hyphensプロパティの値です。
 *
 * @author MIYABE Tatsuhiko
 */
public enum HyphensValue implements Value {
	NONE_VALUE(AbstractTextParams.HYPHENS_NONE),

	MANUAL_VALUE(AbstractTextParams.HYPHENS_MANUAL),

	AUTO_VALUE(AbstractTextParams.HYPHENS_AUTO);

	private final byte hyphens;

	private HyphensValue(byte hyphens) {
		this.hyphens = hyphens;
	}

	public byte getHyphens() {
		return this.hyphens;
	}

	public String toString() {
		switch (this) {
		case NONE_VALUE:
			return "none";

		case MANUAL_VALUE:
			return "manual";

		case AUTO_VALUE:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}
