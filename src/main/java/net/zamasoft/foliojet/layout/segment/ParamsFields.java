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
 * ({@link TextParamsFields}と同じ理由)——コンパクトコンストラクタで
 * freeze時のコピーを、{@link #materializeInto}呼び出しごとに新品の
 * コピーをそれぞれ行う(2026-07-22 Stage2、不変recordへ置換)。
 * </p>
 */
record ParamsFields(CSSElement element, int zIndexValue, byte zIndexType, float opacity, AffineTransform transform,
		Offset transformOrigin) {
	ParamsFields {
		transform = new AffineTransform(transform);
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
