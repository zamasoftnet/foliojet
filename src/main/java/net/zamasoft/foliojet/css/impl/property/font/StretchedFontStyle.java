package net.zamasoft.foliojet.css.impl.property.font;

import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * {@code font-stretch}の幅級({@code usWidthClass})を添えた
 * {@link FontStyle}です(2026-08-29新設)。
 *
 * <p>
 * pdfg2dの{@link FontStyleImpl}はrecordで拡張できないため、これを内包
 * して委譲し、{@link #getWidthClass()}を加える。{@code normal}(5)の
 * ときは作らず素の{@code FontStyleImpl}を使う({@code CSSStyle.getFontStyle})。
 * フォント索引が幅級を持つようになれば、pdfg2dの照合はこの値を
 * 読めばよい。{@code FontUtils.equals/hashCode}は幅級を見ないので、
 * 現状の字形キャッシュでは幅級だけ違うスタイルは同一視される
 * (照合結果も同じなので矛盾はない)。
 * </p>
 */
public record StretchedFontStyle(FontStyleImpl base, int widthClass) implements FontStyle, Serializable {

	/** OpenType OS/2 {@code usWidthClass}(1..9、5=normal)。 */
	public int getWidthClass() {
		return this.widthClass;
	}

	@Override
	public Direction getDirection() {
		return this.base.getDirection();
	}

	@Override
	public TextOrientation getTextOrientation() {
		return this.base.getTextOrientation();
	}

	@Override
	public Weight getWeight() {
		return this.base.getWeight();
	}

	@Override
	public Style getStyle() {
		return this.base.getStyle();
	}

	@Override
	public FontFamilyList getFamily() {
		return this.base.getFamily();
	}

	@Override
	public double getSize() {
		return this.base.getSize();
	}

	@Override
	public FontPolicyList getPolicy() {
		return this.base.getPolicy();
	}

	@Override
	public FontFeatureSet getFeatures() {
		return this.base.getFeatures();
	}

	@Override
	public boolean getSynthesisWeight() {
		return this.base.getSynthesisWeight();
	}

	@Override
	public boolean getSynthesisStyle() {
		return this.base.getSynthesisStyle();
	}

	@Override
	public String toString() {
		return this.base.toString() + "[widthClass=" + this.widthClass + "]";
	}
}
