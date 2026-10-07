package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

/**
 * Converts a {@link Shape} to an SVG {@code d} attribute.
 *
 * <p>
 * SVG path syntax <b>allows command letters to be omitted for repeated commands</b>,
 * so omit the letter when the type matches the previous command.
 * Also omit separator commas and spaces before negative numbers, whose sign serves as a separator.
 * This difference matters for long documents.
 * </p>
 *
 * <p>
 * <b>Decide whether a separator is needed solely from the last character actually written.</b>
 * Assuming a command letter was written loses the separator when the command is omitted,
 * joining numbers as in {@code L300 0300.4} and <b>silently producing a different shape</b>.
 * The XML remains valid, so well-formedness checks cannot catch this.
 * </p>
 *
 * <p>
 * Do not use exponential notation for numbers. SVG syntax allows it, but some implementations
 * cannot read it (the same policy as {@link SVGWriter#number}).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPathWriter {
	private SVGPathWriter() {
		// Utility
	}

	/** Fill rule. Specified in a {@code fill-rule} attribute separately from {@code d}. */
	static String fillRule(final Shape shape) {
		return shape.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD ? "evenodd" : null;
	}

	static String toPathData(final Shape shape, final AffineTransform at) {
		final StringBuilder d = new StringBuilder(128);
		final double[] c = new double[6];
		char last = 0;
		for (final PathIterator i = shape.getPathIterator(at); !i.isDone(); i.next()) {
			switch (i.currentSegment(c)) {
			case PathIterator.SEG_MOVETO -> {
				d.append('M');
				// Coordinate pairs following moveto imply lineto. Subsequent L letters can be omitted.
				last = 'L';
				number(d, c[0]);
				number(d, c[1]);
			}
			case PathIterator.SEG_LINETO -> {
				last = command(d, last, 'L');
				number(d, c[0]);
				number(d, c[1]);
			}
			case PathIterator.SEG_QUADTO -> {
				last = command(d, last, 'Q');
				number(d, c[0]);
				number(d, c[1]);
				number(d, c[2]);
				number(d, c[3]);
			}
			case PathIterator.SEG_CUBICTO -> {
				last = command(d, last, 'C');
				number(d, c[0]);
				number(d, c[1]);
				number(d, c[2]);
				number(d, c[3]);
				number(d, c[4]);
				number(d, c[5]);
			}
			case PathIterator.SEG_CLOSE -> {
				d.append('Z');
				// No implicit continuation after closepath. Write a command next.
				last = 0;
			}
			default -> throw new IllegalStateException("unknown path segment");
			}
		}
		return d.toString();
	}

	/** Writes a command letter if it differs from the previous one; otherwise omits it. */
	private static char command(final StringBuilder d, final char last, final char wanted) {
		if (last != wanted) {
			d.append(wanted);
		}
		return wanted;
	}

	/**
	 * Writes one number. A separator is needed only when the last character written is a digit
	 * or decimal point and this number is not negative.
	 */
	private static void number(final StringBuilder d, final double value) {
		final String text = SVGWriter.number(value);
		if (d.length() > 0 && text.charAt(0) != '-') {
			final char prev = d.charAt(d.length() - 1);
			if ((prev >= '0' && prev <= '9') || prev == '.') {
				d.append(' ');
			}
		}
		d.append(text);
	}
}
