package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;

/**
 * An intra-line break opportunity (M3a). Makes the last fitting break point an explicit type, replacing implicit
 * tracking by TextBuilder's two cursors (textUnitElementCount/textUnitGlyphCount) and repeated instanceof checks
 * during drawing.
 *
 * <ul>
 * <li>elementCount — number of buffered elements up to this opportunity</li>
 * <li>glyphCount — glyph position when the opportunity lies inside text under construction
 * (0 or the full glyph count at a text boundary)</li>
 * <li>hyphen — target to materialize at the cut if this opportunity comes from a soft hyphen</li>
 * </ul>
 *
 * <p>
 * Note (design memo for M3b/M3c): the current greedy algorithm retains only opportunities that fit when recorded.
 * Available line width varies by line due to floats (locateLine), so feasibility may differ between recording and
 * selection. Design the transition to listing all opportunities and evaluating them at selection together with M6
 * BreakToken resumption semantics.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record BreakOpportunity(int elementCount, int glyphCount, SoftHyphen hyphen) {
	/** No break opportunity (buffer start). */
	public static final BreakOpportunity NONE = new BreakOpportunity(0, 0, null);
}
