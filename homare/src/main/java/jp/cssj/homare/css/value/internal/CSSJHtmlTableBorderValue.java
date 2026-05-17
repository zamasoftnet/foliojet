package jp.cssj.homare.css.value.internal;

import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.LengthValue;

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
