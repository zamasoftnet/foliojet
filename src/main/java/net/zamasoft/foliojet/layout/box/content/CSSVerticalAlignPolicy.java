package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * Calculates {@code vertical-align}. CSS 2.1 §10.8.1 provides the basic keyword meanings,
 * but this does not limit FolioJet's overall CSS implementation scope to CSS 2.1.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSVerticalAlignPolicy.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class CSSVerticalAlignPolicy implements VerticalAlignPolicy {
	public static final short BASELINE = 0;

	public static final short MIDDLE = BASELINE + 1;

	public static final short SUB = MIDDLE + 1;

	public static final short SUPER = SUB + 1;

	public static final short TEXT_TOP = SUPER + 1;

	public static final short TEXT_BOTTOM = TEXT_TOP + 1;

	public static final short TOP = TEXT_BOTTOM + 1;

	public static final short BOTTOM = TOP + 1;

	public static final VerticalAlignPolicy BASELINE_POLICY = new CSSVerticalAlignPolicy(BASELINE);

	private final short verticalAlignType;

	protected CSSVerticalAlignPolicy(short verticalAlign) {
		this.verticalAlignType = verticalAlign;
	}

	public double getVerticalAlign(AbstractTextBox parentBox, AbstractLineBox lineBox, double ascent, double descent,
			double lineHeight, double baseline) {
		final double v;
		switch (this.verticalAlignType) {
		case CSSVerticalAlignPolicy.BASELINE:
			// Baseline
			v = 0;
			break;

		case CSSVerticalAlignPolicy.MIDDLE: {
			if (parentBox.getTextParams().isVerticalTypesetting()) {
				// **Vertical writing** (2026-09-02): Lines align to the centerline, so place the box center
				// on the parent centerline (there is no horizontal-writing x-height term; using it shifted
				// the box right by half the x-height). Symmetric text boxes have offset 0; asymmetric boxes
				// such as inline-table shift by the amount needed to center them.
				v = -((ascent + descent) / 2.0 - descent);
				break;
			}
			// Align the box centerline half the parent x-height above the parent box baseline.
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxXHeight() / 2.0 - ((ascent + descent) / 2.0 - descent);
			break;
		}

		case CSSVerticalAlignPolicy.SUPER: {
			// Superscript. **Raise the baseline by 1/3 of the parent font size**,
			// matching Chrome (Blink) superscripts (vertical writing: 2026-09-02; horizontal writing:
			// 2026-10-04).
			//
			// Vertical writing: Characters sit on the centerline, with glyph bounds extending only half their size
			// to either side. Reusing the old horizontal formula (box center at the parent font top = right edge)
			// placed the superscript box center at the glyph bounds' right edge, making it **protrude entirely
			// outside the column** (user report: footnote numbers shifted right in vertical writing).
			//
			// The old horizontal formula (box center at the parent font top, no SPEC) was also removed.
			// Japanese fonts with larger parent ascent raised it farther: in 12 pt body text,
			// it was about 2.5 pt above Chrome. The default ::footnote-call floated too high
			// and widened the line spacing (TECH-20261003-004, item ⑥, the Jigen Ango book).
			v = parentBox.getTextParams().fontStyle.getSize() / 3.0;
			break;
		}

		case CSSVerticalAlignPolicy.SUB: {
			// Subscript
			if (parentBox.getTextParams().isVerticalTypesetting()) {
				// Vertical writing: For the same reason as superscripts, match Chrome's subscript offset
				// (1/5 of the parent font size; 2026-09-02).
				v = -parentBox.getTextParams().fontStyle.getSize() / 5.0;
				break;
			}
			// Align the baseline with the bottom of the parent box font (no SPEC).
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = -flm.getMaxDescent();
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_TOP: {
			if (parentBox.getTextParams().isVerticalTypesetting()) {
				// Vertical writing (2026-09-02): The parent font "top" is the glyph bounds' right edge = half the size.
				// Using horizontal ascent (about 0.88 times the size) made it protrude to the right.
				v = parentBox.getTextParams().fontStyle.getSize() / 2.0 - ascent;
				break;
			}
			// Align the top of the box font with the top of the parent box font.
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxAscent() - ascent;
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_BOTTOM: {
			if (parentBox.getTextParams().isVerticalTypesetting()) {
				// Vertical writing: The parent font "bottom" is the glyph bounds' left edge = half the size.
				v = -(parentBox.getTextParams().fontStyle.getSize() / 2.0 - descent);
				break;
			}
			// Align the bottom of the box font with the bottom of the parent element font.
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = -flm.getMaxDescent() + descent;
			break;
		}

		case CSSVerticalAlignPolicy.TOP: {
			// Align the top of the box font with the top of the line.
			// v = (lineBox.getAscent() - ascent) - baseline - (lineBox.getPageSize() -
			// (ascent + descent)) / 2;
			v = lineBox.getAscent() - ascent;
			break;
		}

		case CSSVerticalAlignPolicy.BOTTOM: {
			// Align the bottom of the box font with the bottom of the line.
			// v = -(lineBox.getDescent() - descent) - baseline + (lineBox.getPageSize() -
			// (ascent + descent)) / 2;
			v = -lineBox.getDescent() + descent;
			break;
		}
		default:
			throw new IllegalStateException();
		}
		return v;
	}

	public short getVerticalAlignType() {
		return this.verticalAlignType;
	}

	public String toString() {
		switch (this.verticalAlignType) {
		case BASELINE:
			return "baseline";

		case MIDDLE:
			return "middle";

		case SUB:
			return "sub";

		case SUPER:
			return "super";

		case TEXT_TOP:
			return "text-top";

		case TEXT_BOTTOM:
			return "text-bottom";

		case TOP:
			return "top";

		case BOTTOM:
			return "bottom";

		default:
			throw new IllegalStateException();
		}
	}
}
