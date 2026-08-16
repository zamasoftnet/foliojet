package net.zamasoft.foliojet.ua.impl.pagedsvg;

import org.apache.batik.svggen.DOMGroupManager;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.Element;

/** SVGGraphics2D bridge that lets the text GC preserve Batik's draw order. */
final class PagedSVGGraphics2D extends SVGGraphics2D {
	PagedSVGGraphics2D(final SVGGeneratorContext context) {
		super(context, false);
	}

	void addTextElement(final Element element) {
		this.getDOMGroupManager().addElement(element, DOMGroupManager.FILL);
	}
}
