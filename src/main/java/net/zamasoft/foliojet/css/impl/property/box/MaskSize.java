package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundSize;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.layout.box.params.BackgroundFit;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@code mask-size}(css-masking-1 §7.9、2026-08-29)。文法と解決は
 * {@link BackgroundSize}を共有する。
 */
public class MaskSize extends BackgroundSize {
	public static final PrimitivePropertyInfo INFO_WIDTH = new MaskSize();
	public static final PrimitivePropertyInfo INFO_HEIGHT = new MaskSize();
	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_WIDTH, INFO_HEIGHT };

	public static BackgroundFit getFit(final CSSStyle style, final Image image) {
		return getFit(style, image, INFO_WIDTH, INFO_HEIGHT);
	}

	public static Dimension get(final CSSStyle style, final Image image) {
		return get(style, image, INFO_WIDTH, INFO_HEIGHT);
	}

	/** 既定(auto auto)のままか。 */
	public static boolean isDefault(final CSSStyle style) {
		return style.get(INFO_WIDTH) == KeywordValue.AUTO && style.get(INFO_HEIGHT) == KeywordValue.AUTO;
	}

	protected MaskSize() {
		super("mask-size");
	}

	@Override
	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}
}
