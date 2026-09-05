package net.zamasoft.foliojet.layout.box.params;

import java.io.Serializable;
import java.util.Locale;

import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * 描画側にも必要なテキスト特性を保持するフォントスタイルです。
 * フォント選択に使う値は元の{@link FontStyle}からそのまま写します。
 */
public record LayoutFontStyle(FontFamilyList family, double size, FontStyle.Style style, FontStyle.Weight weight,
		FontStyle.Direction direction, FontPolicyList policy, FontFeatureSet features, boolean synthesisWeight,
		boolean synthesisStyle,
		FontStyle.TextOrientation textOrientation, int widthClass, Locale lang, String paintOrder)
		implements FontStyle, Serializable {

	public static FontStyle withPaintOrder(final FontStyle base, final String paintOrder) {
		if (paintOrder == null) {
			return base;
		}
		return copy(base, base.getFeatures(), paintOrder);
	}

	public static FontStyle withFeatures(final FontStyle base, final FontFeatureSet features) {
		if (base instanceof final LayoutFontStyle layout) {
			return copy(base, features, layout.paintOrder);
		}
		return new FontStyleImpl(base.getFamily(), base.getSize(), base.getStyle(), base.getWeight(),
				base.getDirection(), base.getPolicy(), features, base.getSynthesisWeight(), base.getSynthesisStyle(),
				base.getTextOrientation(), base.getWidthClass(), base.getLang());
	}

	private static LayoutFontStyle copy(final FontStyle base, final FontFeatureSet features, final String paintOrder) {
		return new LayoutFontStyle(base.getFamily(), base.getSize(), base.getStyle(), base.getWeight(),
				base.getDirection(), base.getPolicy(), features, base.getSynthesisWeight(), base.getSynthesisStyle(),
				base.getTextOrientation(), base.getWidthClass(), base.getLang(), paintOrder);
	}

	@Override
	public FontFamilyList getFamily() {
		return this.family;
	}

	@Override
	public double getSize() {
		return this.size;
	}

	@Override
	public FontStyle.Style getStyle() {
		return this.style;
	}

	@Override
	public FontStyle.Weight getWeight() {
		return this.weight;
	}

	@Override
	public FontStyle.Direction getDirection() {
		return this.direction;
	}

	@Override
	public FontPolicyList getPolicy() {
		return this.policy;
	}

	@Override
	public FontFeatureSet getFeatures() {
		return this.features;
	}

	@Override
	public boolean getSynthesisWeight() {
		return this.synthesisWeight;
	}

	@Override
	public boolean getSynthesisStyle() {
		return this.synthesisStyle;
	}

	@Override
	public FontStyle.TextOrientation getTextOrientation() {
		return this.textOrientation;
	}

	@Override
	public int getWidthClass() {
		return this.widthClass;
	}

	@Override
	public Locale getLang() {
		return this.lang;
	}
}
