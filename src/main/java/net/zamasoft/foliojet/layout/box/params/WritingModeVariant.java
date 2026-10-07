package net.zamasoft.foliojet.layout.box.params;

/**
 * Glyph rotation variant for the writing mode.
 *
 * <p>
 * Stored independently of block progression ({@link WritingMode}) and text direction
 * ({@link AbstractTextParams#direction}). sideways is applied to drawing at the visualization stage.
 * </p>
 */
public enum WritingModeVariant {
	NORMAL,
	/** Rotates the glyph run clockwise. */
	SIDEWAYS_CW,
	/** Rotates the glyph run counterclockwise. */
	SIDEWAYS_CCW
}
