package net.zamasoft.foliojet.impl.objects.svg;

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
import net.zamasoft.foliojet.impl.ua.svg.SVGImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.xerces.Xerces2Parser;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;

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
			this.parser = Xerces2Parser.createXMLReader();
		} catch (Exception e) {
			// ignore
		}
		this.setValidating(false);
	}
	@Override
	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		// HACK
		int viewbox = atts.getIndex("viewbox");
		if (viewbox != -1) {
			AttributesImpl attsi = new AttributesImpl(atts);
			attsi.setLocalName(viewbox, "viewBox");
			attsi.setQName(viewbox, attsi.getQName(viewbox).replace("viewbox", "viewBox"));
			atts = attsi;
		}
		super.startElement(uri, lName, qName, atts);
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
		double scale = LengthUtils.convert(ua, 1.0, LengthValue.UNIT_PX, LengthValue.UNIT_PT);
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
