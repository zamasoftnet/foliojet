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

public class SVGInlineObject extends SAXSVGDocumentFactory implements InlineObject {
	protected SVGImageLoader loader = null;

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
	 * HTMLパーサーが小文字化したSVG要素名を正しいcamelCaseへ戻す表です
	 * (HTML Standard §13.2.6.5 "adjust SVG tag names"と同内容)。HTML内の
	 * インラインSVGはHTML構文で字句解析されるため要素名・属性名が小文字化
	 * されるが、SVG DOM(Batik)はcamelCaseでしか認識しない——たとえば
	 * {@code lineargradient}のままではグラデーションが無効になり、
	 * fill="url(#...)"のパスが描画されず絵ごと消える(2026-08-07、
	 * yahoo.co.jpのAIアシスタントアイコンで発覚)。
	 */
	private static final java.util.Map<String, String> SVG_TAG_ADJUST = buildAdjustMap(new String[] { "altGlyph",
			"altGlyphDef", "altGlyphItem", "animateColor", "animateMotion", "animateTransform", "clipPath", "feBlend",
			"feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting",
			"feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG",
			"feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset",
			"fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "foreignObject", "glyphRef",
			"linearGradient", "radialGradient", "textPath" });

	/**
	 * HTMLパーサーが小文字化したSVG属性名を戻す表です(HTML Standard
	 * §13.2.6.5 "adjust SVG attributes"と同内容)。
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
		String path = uri.getPath();
		if (path != null) {
			int slash = path.lastIndexOf('/');
			if (slash != -1) {
				path = path.substring(slash + 1);
			}
		}
		doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", path);
		doc.setParsedURL(new ParsedURL(uri.toString()));
		Image image = this.loader.getImage(uri.toString(), doc, ua);
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
