package net.zamasoft.foliojet.css.value.internal;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.LengthValue;

public class CSSJHtmlTableBorderValue implements InternalValue {
	public static final CSSJHtmlTableBorderValue NULL_BORDER = new CSSJHtmlTableBorderValue(AbsoluteLengthValue.ZERO,
			null);

	private final LengthValue width;
	private final ColorValue color;

	public CSSJHtmlTableBorderValue(LengthValue width, ColorValue color) {
		assert width != null;
		this.width = width;
		this.color = color;
	}

	public short getValueType() {
		return TYPE_CSSJ_HTML_TABLE_BORDER;
	}

	public LengthValue getWidth() {
		return this.width;
	}

	public ColorValue getColor() {
		return this.color;
	}
}
