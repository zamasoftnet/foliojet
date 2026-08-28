package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ObjectFitValue implements Value {
	FILL_VALUE(ObjectFitMode.FILL),

	CONTAIN_VALUE(ObjectFitMode.CONTAIN),

	COVER_VALUE(ObjectFitMode.COVER),

	NONE_VALUE(ObjectFitMode.NONE),

	SCALE_DOWN_VALUE(ObjectFitMode.SCALE_DOWN);

	private final ObjectFitMode objectFit;

	private ObjectFitValue(ObjectFitMode objectFit) {
		this.objectFit = objectFit;
	}

	public ObjectFitMode getObjectFit() {
		return this.objectFit;
	}

	public String toString() {
		switch (this.objectFit) {
		case FILL:
			return "fill";

		case CONTAIN:
			return "contain";

		case COVER:
			return "cover";

		case NONE:
			return "none";

		case SCALE_DOWN:
			return "scale-down";

		default:
			throw new IllegalStateException();
		}
	}
}
