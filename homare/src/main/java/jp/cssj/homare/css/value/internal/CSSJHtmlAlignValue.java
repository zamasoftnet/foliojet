package jp.cssj.homare.css.value.internal;

import jp.cssj.homare.style.box.params.Types;

public class CSSJHtmlAlignValue implements InternalValue {
	public static final CSSJHtmlAlignValue START_VALUE = new CSSJHtmlAlignValue(Types.ALIGN_START);
	public static final CSSJHtmlAlignValue END_VALUE = new CSSJHtmlAlignValue(Types.ALIGN_END);
	public static final CSSJHtmlAlignValue CENTER_VALUE = new CSSJHtmlAlignValue(Types.ALIGN_CENTER);

	private final byte align;

	private CSSJHtmlAlignValue(byte align) {
		this.align = align;
	}

	public short getValueType() {
		return TYPE_CSSJ_HTML_ALIGN;
	}

	public byte getHtmlAlign() {
		return this.align;
	}
}
