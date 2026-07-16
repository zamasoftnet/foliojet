package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.css.value.Value;

public enum CSSJHtmlAlignValue implements Value {
	START_VALUE(Align.START),

	END_VALUE(Align.END),

	CENTER_VALUE(Align.CENTER);

	private final Align align;

	private CSSJHtmlAlignValue(Align align) {
		this.align = align;
	}

	public Align getHtmlAlign() {
		return this.align;
	}
}
