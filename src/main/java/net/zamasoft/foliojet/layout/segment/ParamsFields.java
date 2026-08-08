package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.StructureElement;
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
 *
 * <p>
 * {@code element}はE-6増分3b-4(2026-07-24)で{@code CSSElement}の
 * 直接保持から{@link StructureToken#freeze}の結果へ切り替えた——
 * {@code CSSElement.precedingElement}チェーン(過去の要素列)をrecipeが
 * 引き留めないため。identity契約(同じ論理要素=同じインスタンス)は
 * 再生セッション内のintern({@code SegmentExecutor})が保つ。
 * </p>
 */
record ParamsFields(StructureElement element, long footnoteId, int zIndexValue, byte zIndexType, float opacity,
		AffineTransform transform, double transformTxRatio, double transformTyRatio, Offset transformOrigin) {
	ParamsFields {
		transform = new AffineTransform(transform);
	}

	static ParamsFields freeze(final Params source) {
		return new ParamsFields(StructureToken.freeze(source.element), source.footnoteId, source.zIndexValue,
				source.zIndexType, source.opacity, source.transform, source.transformTxRatio, source.transformTyRatio,
				source.transformOrigin);
	}

	void materializeInto(final Params target) {
		target.element = this.element;
		target.footnoteId = this.footnoteId;
		target.zIndexValue = this.zIndexValue;
		target.zIndexType = this.zIndexType;
		target.opacity = this.opacity;
		target.transform = new AffineTransform(this.transform);
		// %のtranslate成分(要素寸法が要るため行列へ畳めず別持ち——
		// Params.transformTxRatio/TyRatio)。2026-08-08まで凍結対象から
		// 漏れており、純粋な translate(-50%) 等(行列は恒等)が
		// 再具現化で丸ごと消えていた——yahoo.co.jpの検索ボタンの虫眼鏡
		// (::beforeのtranslateY(-50%))が半個ぶん下にずれた実バグ
		target.transformTxRatio = this.transformTxRatio;
		target.transformTyRatio = this.transformTyRatio;
		target.transformOrigin = this.transformOrigin;
	}
}
