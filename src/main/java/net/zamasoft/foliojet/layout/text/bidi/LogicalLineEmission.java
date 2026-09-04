package net.zamasoft.foliojet.layout.text.bidi;

/**
 * A reordered line's semantic text, kept separately from its visual leaves.
 *
 * @param lineId unique identity of the laid-out line
 * @param logicalText text and controls in logical source order; atomic inlines
 *                    are represented by U+FFFC
 */
public record LogicalLineEmission(long lineId, String logicalText) {
}
