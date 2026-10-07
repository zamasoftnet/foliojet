package net.zamasoft.foliojet.objects.svg;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URI;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.XMLResourceDescriptor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.foliojet.css.token.Unit;

public class SVGInlineObject extends SAXSVGDocumentFactory
		implements net.zamasoft.foliojet.css.StyleAwareInlineObject {
	protected SVGImageLoader loader = null;

	/** Style of the svg element in the host document (context for author CSS var() resolution). */
	private net.zamasoft.foliojet.css.CSSStyle hostStyle;
	private net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource runningSource;

	public net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource getRunningSource() {
		return this.runningSource;
	}

	private boolean isRunning() {
		for (var style = this.hostStyle; style != null; style = style.getParentStyle()) {
			if (style.get(net.zamasoft.foliojet.css.impl.property.box.CSSPosition.INFO)
					instanceof net.zamasoft.foliojet.css.value.RunningPositionValue) {
				return true;
			}
		}
		return false;
	}

	/** Enforces the limit during serialization, avoiding a full temporary copy of a huge SVG. */
	private void snapshotRunningSource(final org.w3c.dom.Document document, final String baseURI) {
		final StringBuilder xml = new StringBuilder();
		final int limit = net.zamasoft.foliojet.css.style.running.RunningCapture.MAX_TEXT_BYTES / 2 - baseURI.length();
		try {
			final java.io.Writer writer = new java.io.Writer() {
				@Override
				public void write(final char[] chars, final int offset, final int length) throws IOException {
					if (length > limit - xml.length()) {
						throw new IOException("running SVG text bytes");
					}
					xml.append(chars, offset, length);
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() {
				}
			};
			javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
					new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
			this.runningSource = new net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource(xml.toString(), baseURI);
		} catch (final javax.xml.transform.TransformerException e) {
			// Continue normal image creation. The running capture side warns and refuses registration.
			this.runningSource = new net.zamasoft.foliojet.css.value.internal.CSSJImageValue.SvgSource(null, baseURI);
		}
	}

	public void setHostStyle(net.zamasoft.foliojet.css.CSSStyle style) {
		this.hostStyle = style;
	}

	public SVGInlineObject() {
		super(XMLResourceDescriptor.getXMLParserClassName());
		synchronized (this) {
			if (this.loader == null) {
				this.loader = new SVGImageLoader();
			}
		}
		try {
			this.parser = XMLParsers.createXMLReader();
		} catch (Exception e) {
			// ignore
		}
		this.setValidating(false);
	}
	/**
	 * Table restoring SVG element names lowercased by the HTML parser to correct camelCase
	 * (equivalent to HTML Standard §13.2.6.5 "adjust SVG tag names"). Inline SVG in HTML is tokenized
	 * as HTML, so element/attribute names become lowercase, but the SVG DOM (Batik) recognizes only
	 * camelCase. For example, leaving {@code lineargradient} lowercase disables the gradient,
	 * so paths with fill="url(#...)" are not drawn and the entire image disappears
	 * (found 2026-08-07 on yahoo.co.jp's AI assistant icon).
	 */
	private static final java.util.Map<String, String> SVG_TAG_ADJUST = buildAdjustMap(new String[] { "altGlyph",
			"altGlyphDef", "altGlyphItem", "animateColor", "animateMotion", "animateTransform", "clipPath", "feBlend",
			"feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting",
			"feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG",
			"feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset",
			"fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "foreignObject", "glyphRef",
			"linearGradient", "radialGradient", "textPath" });

	/**
	 * Table restoring SVG attribute names lowercased by the HTML parser
	 * (equivalent to HTML Standard §13.2.6.5 "adjust SVG attributes").
	 */
	private static final java.util.Map<String, String> SVG_ATTR_ADJUST = buildAdjustMap(new String[] {
			"attributeName", "attributeType", "baseFrequency", "baseProfile", "calcMode", "clipPathUnits",
			"diffuseConstant", "edgeMode", "filterUnits", "glyphRef", "gradientTransform", "gradientUnits",
			"kernelMatrix", "kernelUnitLength", "keyPoints", "keySplines", "keyTimes", "lengthAdjust",
			"limitingConeAngle", "markerHeight", "markerUnits", "markerWidth", "maskContentUnits", "maskUnits",
			"numOctaves", "pathLength", "patternContentUnits", "patternTransform", "patternUnits", "pointsAtX",
			"pointsAtY", "pointsAtZ", "preserveAlpha", "preserveAspectRatio", "primitiveUnits", "refX", "refY",
			"repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "specularConstant",
			"specularExponent", "spreadMethod", "startOffset", "stdDeviation", "stitchTiles", "surfaceScale",
			"systemLanguage", "tableValues", "targetX", "targetY", "textLength", "viewBox", "viewTarget",
			"xChannelSelector", "yChannelSelector", "zoomAndPan" });

	private static java.util.Map<String, String> buildAdjustMap(String[] names) {
		final java.util.Map<String, String> map = new java.util.HashMap<>();
		for (final String name : names) {
			map.put(name.toLowerCase(java.util.Locale.ROOT), name);
		}
		return map;
	}

	private static String adjustTag(String name) {
		final String adjusted = SVG_TAG_ADJUST.get(name);
		return adjusted != null ? adjusted : name;
	}

	@Override
	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		AttributesImpl attsi = null;
		for (int i = 0; i < atts.getLength(); ++i) {
			final String adjusted = SVG_ATTR_ADJUST.get(atts.getLocalName(i));
			if (adjusted != null) {
				if (attsi == null) {
					attsi = new AttributesImpl(atts);
					atts = attsi;
				}
				attsi.setLocalName(i, adjusted);
				attsi.setQName(i, adjusted);
			}
		}
		super.startElement(uri, adjustTag(lName), adjustTag(qName), atts);
	}

	@Override
	public void endElement(String uri, String lName, String qName) throws SAXException {
		super.endElement(uri, adjustTag(lName), adjustTag(qName));
	}


	public Image getImage(UserAgent ua) throws IOException {
		SVGOMDocument doc = (SVGOMDocument) this.document;
		this.document = null;
		this.currentNode = null;
		this.locator = null;

		URI uri = ua.getDocumentContext().getBaseURI();
		// Pass relative/opaque bases to Batik as synthesized URIs (SVGImageLoader.toBatikInlineURI)
		final String batikURI = net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader.toBatikInlineURI(uri);
		if (batikURI.equals(uri.toString())) {
			String path = uri.getPath();
			if (path != null) {
				int slash = path.lastIndexOf('/');
				if (slash != -1) {
					path = path.substring(slash + 1);
				}
			}
			doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", path);
		}
		doc.setParsedURL(new ParsedURL(batikURI));

		// Inject the SVG subset of the HTML document's author CSS as <style>
		// (2026-08-07). Inline SVG is passed to Batik as an independent document,
		// so without this, icons using CSS classes for fill/stroke become solid black
		// with SVG's default fill=black (found on qiita's like button).
		// See CSSStyleSheetBuilder.collectSVGStyleRule for collection/filtering and
		// SVGAuthorCss.toCssText for var() resolution (in the hostStyle context).
		// Insert at the start of the root so existing <style> elements and style attributes
		// inside the SVG can override it later (cascade source order)
		final String svgAuthorCss = ua.getDocumentContext().getSVGAuthorCss().toCssText(this.hostStyle);
		final boolean running = this.isRunning();
		this.hostStyle = null;
		if (!svgAuthorCss.isEmpty()) {
			// **Use a separate <style> for each rule** (2026-08-07). Batik
			// invalidates an entire stylesheet if even one value cannot be parsed
			// (qiita's display:flex turned all icons back to plain black).
			// The allowlist in SVGAuthorCss prevents most cases, but limit the failure
			// unit to one rule so an unexpected value affects
			// only that rule
			final org.w3c.dom.Element root = doc.getDocumentElement();
			org.w3c.dom.Node anchor = root.getFirstChild();
			for (final String rule : svgAuthorCss.split("\n")) {
				if (rule.isEmpty()) {
					continue;
				}
				final org.w3c.dom.Element styleElement = doc.createElementNS("http://www.w3.org/2000/svg", "style");
				styleElement.setAttributeNS(null, "type", "text/css");
				styleElement.appendChild(doc.createCDATASection(rule));
				root.insertBefore(styleElement, anchor);
			}
		}

		if (running) {
			this.snapshotRunningSource(doc, uri.toString());
		}
		Image image = this.loader.getImage(batikURI, doc, ua);
		double scale = LengthUtils.convert(ua, 1.0, Unit.PX, Unit.PT);
		if (scale != 1) {
			ImageMap map = ua.getUAContext().getImageMaps().remove(image);
			AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
			image = new TransformedImage(image, at);
			map = map.getTransformedImageMap(at);
			ua.getUAContext().getImageMaps().put(image, map);
		}
		return image;
	}

}
