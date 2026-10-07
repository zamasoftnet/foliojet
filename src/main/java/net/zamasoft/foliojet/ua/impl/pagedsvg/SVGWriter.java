package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal writer for <b>streaming SVG without assembling it</b>.
 *
 * <p>
 * Batik's {@code SVGGraphics2D} builds a DOM during drawing and serializes it at the end.
 * Here, strings flow to the output in drawing order without building a DOM.
 * </p>
 *
 * <p>
 * <b>Handling defs is the use case for fragment output.</b> Clip paths, gradients,
 * and {@code @font-face} <b>become known during drawing</b>, but putting them first
 * is convenient for consumers (references can resolve before reading the body).
 * Reserve one fragment at the start, stream the body to the second fragment,
 * and fill the reserved fragment when closing the page.
 * This is analogous to filling PDF's cross-reference table later.
 * </p>
 *
 * <p>
 * SVG 1.1 allows {@code defs} anywhere in the document, including <b>references
 * from elements that precede it</b>. Placing it at the end is therefore valid;
 * fragments are a means of placing it at the start.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGWriter {
	static final String SVG_NS = "http://www.w3.org/2000/svg";

	static final String XLINK_NS = "http://www.w3.org/1999/xlink";

	/** Coordinate precision. Matches Batik's default (6 digits). */
	private static final int PRECISION = 6;

	private final Writer out;

	/**
	 * Definitions shared within a page (2026-08-29). A separate {@link SVGWriter} writes layer
	 * (group image) contents to another buffer, but {@code defs}, IDs, and {@code @font-face}
	 * are page-wide, so collect them here for sharing between parent and child.
	 */
	private static final class Shared {
		/** Definitions to place at the start. Written together when closing the page. */
		final List<String> defs = new ArrayList<>();
		/** IDs of definitions (filters) with identical contents. Avoid defining the same effect repeatedly. */
		final java.util.Map<String, String> defIds = new java.util.HashMap<>();
		/** The sequence of {@code @font-face} rules. References shared WOFF2. */
		final java.util.Map<String, String> fontFaces = new java.util.LinkedHashMap<>();
		/**
		 * How to determine the URI in {@code @font-face}'s {@code src}
		 * (B-1, 2026-08-29). Page-split SVG references shared WOFF2 by relative paths from
		 * {@code pages/}. Self-contained SVG inserts {@code data:}, so replaces this strategy.
		 */
		java.util.function.UnaryOperator<String> fontSrc = uri -> "../" + uri;
		int nextId = 0;
	}

	private final Shared shared;

	SVGWriter(final Writer out) {
		this.out = out;
		this.shared = new Shared();
	}

	/** Writer to a separate output, sharing definitions and IDs with {@code parent} (for layer contents). */
	SVGWriter(final Writer out, final SVGWriter parent) {
		this.out = out;
		this.shared = parent.shared;
	}

	/** Registers one definition and returns its reference ID. */
	String addDef(final String element) {
		this.shared.defs.add(element);
		return null;
	}

	/**
	 * Registers a definition, deduplicating by content, and returns its ID (2026-08-29).
	 * {@code content} contains the element's contents and attributes except id;
	 * returns an existing ID if the content matches.
	 *
	 * @param prefix  ID prefix
	 * @param name    element name
	 * @param content text following {@code <name id=".."} (attributes and contents, excluding the closing tag)
	 */
	String defId(final String prefix, final String name, final String content) {
		final String key = name + '\u0000' + content;
		String id = this.shared.defIds.get(key);
		if (id == null) {
			id = this.nextId(prefix);
			this.shared.defIds.put(key, id);
			this.addDef("<" + name + " id=\"" + id + "\"" + content + "</" + name + ">");
		}
		return id;
	}

	String nextId(final String prefix) {
		return prefix + (++this.shared.nextId);
	}

	void addFontFace(final String family, final String uri) {
		this.shared.fontFaces.put(family, uri);
	}

	/** @see Shared#fontSrc */
	void setFontSrc(final java.util.function.UnaryOperator<String> fontSrc) {
		this.shared.fontSrc = fontSrc;
	}

	/**
	 * Writes collected definitions. {@code defs} can appear anywhere in the document and
	 * be referenced by earlier elements, so placing it at the end is fine.
	 */
	void writeDefs(final Writer target) throws IOException {
		if (this.shared.defs.isEmpty() && this.shared.fontFaces.isEmpty()) {
			return;
		}
		target.write("<defs>");
		if (!this.shared.fontFaces.isEmpty()) {
			target.write("<style type=\"text/css\">");
			final StringBuilder css = new StringBuilder();
			for (final java.util.Map.Entry<String, String> face : this.shared.fontFaces.entrySet()) {
				css.append("@font-face{font-family:'").append(face.getKey()).append("';src:url('")
						.append(this.shared.fontSrc.apply(face.getValue()))
						.append("') format('woff2');font-display:block;}");
			}
			escapeText(target, css.toString());
			target.write("</style>");
		}
		for (final String def : this.shared.defs) {
			target.write(def);
		}
		target.write("</defs>");
	}

	void raw(final String text) throws IOException {
		this.out.write(text);
	}

	/** Opens an element. Write subsequent attributes with {@link #attr}. */
	void open(final String name) throws IOException {
		this.out.write('<');
		this.out.write(name);
	}

	void attr(final String name, final String value) throws IOException {
		this.out.write(' ');
		this.out.write(name);
		this.out.write("=\"");
		escapeAttribute(this.out, value);
		this.out.write('"');
	}

	void attr(final String name, final double value) throws IOException {
		this.attr(name, number(value));
	}

	/** Closes an element without children. */
	void closeEmpty() throws IOException {
		this.out.write("/>");
	}

	/** Closes a start tag (children follow). */
	void closeStart() throws IOException {
		this.out.write('>');
	}

	void end(final String name) throws IOException {
		this.out.write("</");
		this.out.write(name);
		this.out.write('>');
	}

	void text(final String value) throws IOException {
		escapeText(this.out, value);
	}

	void flush() throws IOException {
		this.out.flush();
	}

	/**
	 * Writes a number. Avoids exponential notation: SVG syntax permits it,
	 * but some implementations cannot read it. Writes integers directly
	 * and removes trailing zeros.
	 */
	static String number(final double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		java.math.BigDecimal d = new java.math.BigDecimal(value)
				.setScale(PRECISION, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
		return d.toPlainString();
	}

	static void escapeAttribute(final Writer out, final String value) throws IOException {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.write("&amp;");
			case '<' -> out.write("&lt;");
			case '>' -> out.write("&gt;");
			case '"' -> out.write("&quot;");
			case '\n' -> out.write("&#10;");
			case '\r' -> out.write("&#13;");
			case '\t' -> out.write("&#9;");
			default -> out.write(ch);
			}
		}
	}

	/** The same operation for appending directly to a string, as when assembling defs. */
	static void escapeAttribute(final StringBuilder out, final String value) {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.append("&amp;");
			case '<' -> out.append("&lt;");
			case '>' -> out.append("&gt;");
			case '"' -> out.append("&quot;");
			case '\n' -> out.append("&#10;");
			case '\r' -> out.append("&#13;");
			case '\t' -> out.append("&#9;");
			default -> out.append(ch);
			}
		}
	}

	static void escapeText(final Writer out, final String value) throws IOException {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.write("&amp;");
			case '<' -> out.write("&lt;");
			case '>' -> out.write("&gt;");
			default -> out.write(ch);
			}
		}
	}
}
