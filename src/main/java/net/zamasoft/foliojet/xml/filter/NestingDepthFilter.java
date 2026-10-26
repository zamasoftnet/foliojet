package net.zamasoft.foliojet.xml.filter;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;

/**
 * Keeps the element tree at most {@value #MAX_DEPTH} deep, as Chrome's HTML parser does (2026-10-26, user decision):
 * an element that would be deeper is attached as the next sibling of the innermost element at the limit, which is
 * closed early, and its own end tag is then dropped. Every element and every character is kept; only the nesting
 * past the limit is flattened.
 *
 * <p>
 * Deeply nested inline elements made layout grow much faster than the input (5,000 nested {@code <em>} took 71
 * seconds, 20,000 did not finish in 120, and nested Markdown emphasis of 120 KB filled a 2 GB heap), in a JVM that
 * other conversions share. Unlike Chrome, which keeps the deeper elements open and attaches the content that follows
 * one of them to it, a stream cannot reopen an element without duplicating it, so such content goes to the parent at
 * the limit. Namespace declarations made on an element closed early do not follow the elements moved after it (a
 * prefixed SVG element deeper than 512 levels whose prefix was declared at the limit loses it); only input crafted to
 * be that deep reaches this.
 * </p>
 */
public class NestingDepthFilter extends DefaultXMLHandlerFilter {
	static final int MAX_DEPTH = 512;

	/** The names (uri, local, qualified) of the elements sent and not yet ended, outermost first. */
	private final List<String[]> sent = new ArrayList<>();

	/** For each element of the input not yet ended, whether its end tag is still to be sent (bit stack). */
	private long[] forward = new long[16];

	private int depth = 0;

	@Override
	public void startElement(final String uri, final String lName, final String qName, final Attributes atts)
			throws SAXException {
		if (this.sent.size() >= MAX_DEPTH) {
			// The innermost sent element is the innermost input element, since deeper ones have ended.
			final String[] innermost = this.sent.remove(this.sent.size() - 1);
			this.outHandler.endElement(innermost[0], innermost[1], innermost[2]);
			this.setForward(this.depth - 1, false);
		}
		this.outHandler.startElement(uri, lName, qName, atts);
		this.sent.add(new String[] { uri, lName, qName });
		if (this.depth / 64 >= this.forward.length) {
			this.forward = java.util.Arrays.copyOf(this.forward, this.forward.length * 2);
		}
		this.setForward(this.depth++, true);
	}

	@Override
	public void endElement(final String uri, final String lName, final String qName) throws SAXException {
		if (this.depth == 0) {
			this.outHandler.endElement(uri, lName, qName);
			return;
		}
		if ((this.forward[--this.depth / 64] & (1L << (this.depth % 64))) != 0) {
			this.sent.remove(this.sent.size() - 1);
			this.outHandler.endElement(uri, lName, qName);
		}
	}

	private void setForward(final int index, final boolean value) {
		if (value) {
			this.forward[index / 64] |= 1L << (index % 64);
		} else {
			this.forward[index / 64] &= ~(1L << (index % 64));
		}
	}
}
