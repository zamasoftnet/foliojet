package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.css.value.Value;

public enum CSSJHtmlAlignValue implements Value {
	START_VALUE(Types.ALIGN_START),

	END_VALUE(Types.ALIGN_END),

	CENTER_VALUE(Types.ALIGN_CENTER);

	private final byte align;

	private CSSJHtmlAlignValue(byte align) {
		this.align = align;
	}

	public byte getHtmlAlign() {
		return this.align;
	}
}
