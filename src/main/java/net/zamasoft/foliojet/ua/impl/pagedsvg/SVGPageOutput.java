package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;

/**
 * Writes one page of SVG <b>without buffering</b>.
 *
 * <p>
 * The order is "root element → body → {@code defs} → close".
 * Clip paths, gradients, and {@code @font-face} become known during drawing,
 * but <b>SVG 1.1 allows {@code defs} anywhere in the document, including references
 * from elements that precede it</b>. It can therefore go at the end.
 * </p>
 *
 * <p>
 * <b>Using fragment output to move {@code defs} to the start was considered and rejected.</b>
 * PDF fills its cross-reference table later because it must record objects' <b>byte offsets</b>;
 * SVG has no such requirement. Since the specification allows any position, the end is fine.
 * <b>This alone is sufficient reason to reject it.</b>
 * </p>
 *
 * <p>
 * Moving it to the start makes the final bytes "a prefix finalized later + a body streamed earlier",
 * requiring the entire page to be held once and negating the benefit of unbuffered output.
 * It also prevents streaming calculation of the manifest's {@code svgSha256},
 * but <b>that is secondary</b>: page hashes are not used in URIs (which are sequential),
 * and no implementation currently reads them. They do not warrant treatment as a constraint.
 * </p>
 *
 * <p>
 * There is no performance justification, either. Measurements (build 19021, 314 pages, one pass,
 * median of 7 runs) showed layout taking about 1,250 ms of a 1,397 ms conversion;
 * the format-specific output difference from PDF was only 155 ms.
 * Fragments would not reduce those 155 ms; they would increase the amount retained.
 * </p>
 *
 * <p>
 * Ordering could be a meaningful use: reserving {@code manifest.json} at the start and filling
 * it at the end would let the consumer know the total page count, binding direction, and
 * table of contents before rendering from the first page. This concerns time until useful
 * information first arrives, not total time.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPageOutput implements AutoCloseable {
	private final Writer out;

	private final SVGWriter writer;

	SVGPageOutput(final Writer out, final double width, final double height) throws IOException {
		this.out = out;
		this.writer = new SVGWriter(out);
		out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.write("<svg xmlns=\"" + SVGWriter.SVG_NS + "\" xmlns:xlink=\"" + SVGWriter.XLINK_NS
				+ "\" version=\"1.1\"");
		out.write(" width=\"" + SVGWriter.number(width) + "\"");
		out.write(" height=\"" + SVGWriter.number(height) + "\"");
		out.write(" viewBox=\"0 0 " + SVGWriter.number(width) + ' ' + SVGWriter.number(height) + "\">");
	}

	SVGWriter writer() {
		return this.writer;
	}

	@Override
	public void close() throws IOException {
		this.writer.writeDefs(this.out);
		this.out.write("</svg>");
		this.out.flush();
	}
}
