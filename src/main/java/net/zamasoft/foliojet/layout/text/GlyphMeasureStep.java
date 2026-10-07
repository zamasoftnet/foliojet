package net.zamasoft.foliojet.layout.text;

/**
 * The CSS layout width formula for one glyph (2026-08-01, 85-point plan increment 3:
 * the first step toward a single measurement core).
 *
 * <p>
 * The CSS-layer width accounting added to the font's base advance (including kerning, owned solely
 * by pdfg2d's TextImpl)—letter-spacing, autospace gaps, and punctuation trimming—was duplicated
 * in TextBuilder.glyph()/TotalFitSession.recordGlyph()/intrinsic measurement ("three width-accounting paths").
 * This record is the sole definition of that formula, and <b>all three paths</b> calculate identical
 * values through it (normal layout and K-P mirror: 2026-08-01 increment 3;
 * intrinsic measurement: connected in 2026-08-06 increment 5).
 * </p>
 *
 * <p>
 * Usage: line accounting may add {@link #baseAndSpacing()} and {@link #adjustment()} separately
 * in the existing order to preserve floating-point addition order.
 * Combined estimates (wrap prediction and K-P candidate widths) use {@link #totalAdvance()}.
 * Intrinsic measurement adds components separately because its policy differs in two ways:
 * gaps apply only to max-content, and the addition order is (base−trim)+spacing.
 * See the Javadoc of IntrinsicMeasurer.glyph() for the reasoning.
 * </p>
 *
 * @param baseAdvance     Font base advance (including kerning;
 *                        return value of TextImpl.appendGlyph or glyphAdvance)
 * @param letterSpacing   letter-spacing
 * @param autospaceGap    text-autospace gap (positive)
 * @param punctuationTrim Punctuation trim (positive values reduce spacing)
 * @author MIYABE Tatsuhiko
 */
public record GlyphMeasureStep(double baseAdvance, double letterSpacing, double autospaceGap,
		double punctuationTrim) {

	/** Cluster-boundary adjustment (gap−trim). The value baked into xadvance. */
	public double adjustment() {
		return this.autospaceGap - this.punctuationTrim;
	}

	/** Base advance+letter-spacing (advance before adjustment). */
	public double baseAndSpacing() {
		return this.baseAdvance + this.letterSpacing;
	}

	/** Total width of this glyph (for estimates and candidate widths). */
	public double totalAdvance() {
		return this.baseAndSpacing() + this.adjustment();
	}
}
