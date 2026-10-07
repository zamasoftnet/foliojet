package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Params;

/**
 * Freeze/materialize processing for {@code Params} ' base fields
 * (element/zIndexValue/zIndexType/opacity/transform/transformOrigin).
 * Introduced 2026-07-22, M6d-A3b; package-private and shared by
 * {@link TextParamsFields} (for `AbstractTextParams`) and {@link InnerTableParamsTemplate}
 * (`InnerTableParams` directly extends `Params`).
 *
 * <p>
 * {@code transform} ({@code AffineTransform}, a mutable JDK class) needs defensive copies on both freeze
 * and materialize (the same reason as {@link TextParamsFields} ).
 * The compact constructor copies at freeze time, and each {@link #materializeInto} call creates a fresh copy
 * (Stage2, 2026-07-22; replaced with an immutable record).
 * </p>
 *
 * <p>
 * E-6 increment 3b-4 (2026-07-24) changed {@code element} from direct {@code CSSElement} retention
 * to the result of {@link StructureToken#freeze} , so recipes do not retain the
 * {@code CSSElement.precedingElement} chain (past elements).
 * Interning within the replay session ({@code SegmentExecutor}) preserves the identity contract:
 * same logical element = same instance.
 * </p>
 */
record ParamsFields(StructureElement element, long footnoteId, int zIndexValue, byte zIndexType, float opacity,
		AffineTransform transform, double transformTxRatio, double transformTyRatio, double transformTxRatioH,
		double transformTyRatioW, Offset transformOrigin, net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode,
		double zoom, net.zamasoft.foliojet.css.value.css3.FilterValue filter,
		net.zamasoft.foliojet.layout.box.params.BookmarkSpec bookmark) {
	ParamsFields {
		transform = new AffineTransform(transform);
	}

	static ParamsFields freeze(final Params source) {
		return new ParamsFields(StructureToken.freeze(source.element), source.footnoteId, source.zIndexValue,
				source.zIndexType, source.opacity, source.transform, source.transformTxRatio, source.transformTyRatio,
				source.transformTxRatioH, source.transformTyRatioW, source.transformOrigin, source.blendMode,
				source.zoom, source.filter, source.bookmark);
	}

	void materializeInto(final Params target) {
		target.element = this.element;
		target.footnoteId = this.footnoteId;
		target.zIndexValue = this.zIndexValue;
		target.zIndexType = this.zIndexType;
		target.opacity = this.opacity;
		target.transform = new AffineTransform(this.transform);
		// Percentage translate components (stored separately because element dimensions are needed
		// and they cannot be folded into the matrix; Params.transformTxRatio/TyRatio). Until 2026-08-08,
		// they were omitted from freezing, so pure translate(-50%), etc. (identity matrix)
		// disappeared entirely on rematerialization. This caused the actual yahoo.co.jp search-button bug:
		// the magnifying glass (::before with translateY(-50%)) shifted down by half its own height.
		target.transformTxRatio = this.transformTxRatio;
		target.transformTyRatio = this.transformTyRatio;
		target.transformTxRatioH = this.transformTxRatioH;
		target.transformTyRatioW = this.transformTyRatioW;
		target.transformOrigin = this.transformOrigin;
		target.blendMode = this.blendMode;
		target.zoom = this.zoom;
		target.filter = this.filter;
		target.bookmark = this.bookmark;
	}
}
