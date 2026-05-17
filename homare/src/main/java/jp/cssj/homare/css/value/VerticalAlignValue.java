package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.content.CSSVerticalAlignPolicy;

public class VerticalAlignValue extends CSSVerticalAlignPolicy implements Value {
	public static final VerticalAlignValue BASELINE_VALUE = new VerticalAlignValue(BASELINE);

	public static final VerticalAlignValue MIDDLE_VALUE = new VerticalAlignValue(MIDDLE);

	public static final VerticalAlignValue SUB_VALUE = new VerticalAlignValue(SUB);

	public static final VerticalAlignValue SUPER_VALUE = new VerticalAlignValue(SUPER);

	public static final VerticalAlignValue TEXT_TOP_VALUE = new VerticalAlignValue(TEXT_TOP);

	public static final VerticalAlignValue TEXT_BOTTOM_VALUE = new VerticalAlignValue(TEXT_BOTTOM);

	public static final VerticalAlignValue TOP_VALUE = new VerticalAlignValue(TOP);

	public static final VerticalAlignValue BOTTOM_VALUE = new VerticalAlignValue(BOTTOM);

	protected VerticalAlignValue(short verticalAlign) {
		super(verticalAlign);
	}

	public short getValueType() {
		return TYPE_VERTICAL_ALIGN;
	}
}
