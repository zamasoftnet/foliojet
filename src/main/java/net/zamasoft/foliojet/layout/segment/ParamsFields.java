package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Params;

/**
 * {@code Params}が持つ基底フィールド(element/zIndexValue/zIndexType/
 * opacity/transform/transformOrigin)のfreeze/materialize処理です
 * (2026-07-22新設、M6d-A3b、package-private——{@link TextParamsFields}
 * (`AbstractTextParams`用)と{@link InnerTableParamsTemplate}
 * (`InnerTableParams`は`Params`を直接継承)が共有する)。
 *
 * <p>
 * {@code transform}({@code AffineTransform}、mutableなJDKクラス)は
 * freeze時・materialize時それぞれで防御的コピーが必要
 * ({@link TextParamsFields}と同じ理由)。
 * </p>
 */
final class ParamsFields {
	final CSSElement element;
	final int zIndexValue;
	final byte zIndexType;
	final float opacity;
	private final AffineTransform transform;
	final Offset transformOrigin;

	private ParamsFields(final CSSElement element, final int zIndexValue, final byte zIndexType, final float opacity,
			final AffineTransform transform, final Offset transformOrigin) {
		this.element = element;
		this.zIndexValue = zIndexValue;
		this.zIndexType = zIndexType;
		this.opacity = opacity;
		this.transform = new AffineTransform(transform);
		this.transformOrigin = transformOrigin;
	}

	static ParamsFields freeze(final Params source) {
		return new ParamsFields(source.element, source.zIndexValue, source.zIndexType, source.opacity,
				source.transform, source.transformOrigin);
	}

	void materializeInto(final Params target) {
		target.element = this.element;
		target.zIndexValue = this.zIndexValue;
		target.zIndexType = this.zIndexType;
		target.opacity = this.opacity;
		target.transform = new AffineTransform(this.transform);
		target.transformOrigin = this.transformOrigin;
	}
}
