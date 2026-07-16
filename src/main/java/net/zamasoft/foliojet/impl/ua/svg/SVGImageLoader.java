package net.zamasoft.foliojet.impl.ua.svg;

import java.awt.geom.Dimension2D;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.batik.anim.dom.AbstractSVGAnimatedLength;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.anim.dom.SVGOMSVGElement;
import org.apache.batik.bridge.BridgeException;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.svg.SVGRect;

import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.impl.ua.svg.Dimension2DImpl;
import net.zamasoft.pdfg2d.svg.PDFGVTBuilder;
import net.zamasoft.pdfg2d.svg.SVGImage;

public class SVGImageLoader implements ImageLoader {
	private static final Logger LOG = Logger.getLogger(SVGImageLoader.class.getName());
	protected static final String SVG_MIME_TYPE = "image/svg+xml";

	static {
		ParsedURL.registerHandler(MyParsedURLDefaultProtocolHandler.INSTANCE);
	}

	public boolean match(Source key) {
		Source source = (Source) key;
		String mimeType;
		try {
			mimeType = source.getMimeType();
		} catch (IOException e) {
			LOG.log(Level.WARNING, "MIME型を取得できませんでした。", e);
			return false;
		}
		URI uri = source.getURI();
		String path = uri.getPath();
		if (!SVG_MIME_TYPE.equalsIgnoreCase(mimeType)) {
			if (path == null || path.length() == 0) {
				path = uri.getSchemeSpecificPart();
			}
			if (path == null) {
				return false;
			}
			path = path.toLowerCase();
			if (!path.endsWith(".svgz") && !path.endsWith(".svg")) {
				return false;
			}
		}
		return true;
	}

	public Image loadImage(final UserAgent ua, Source source) throws IOException {
		Document doc = this.loadDocument(source);
		return getImage(source.getURI().toString(), doc, ua);
	}

	public Document loadDocument(Source source) throws IOException {
		final URI uri = source.getURI();
		String path = uri.getPath();
		boolean gzip;
		if (path != null) {
			path = path.toLowerCase();
			gzip = path.endsWith(".svgz");
		} else {
			gzip = false;
		}

		// SAXSVGDocumentFactoryはスレッドセーフではないことに注意
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
		final String uriStr = uri.toString();

		SVGOMDocument doc;
		if (!gzip && source.isReader()) {
			try (Reader in = new BufferedReader(source.getReader())) {
				doc = (SVGOMDocument) factory.createDocument(uriStr, in);
			}
		} else {
			InputStream in = new BufferedInputStream(source.getInputStream());
			try {
				if (!gzip) {
					in.mark(2);
					if (in.read() == 0x1f && in.read() == 0x8b) {
						gzip = true;
					}
					in.reset();
				}
				if (gzip) {
					in = new GZIPInputStream(in);
				}
				doc = (SVGOMDocument) factory.createDocument(uriStr, in);
			} finally {
				in.close();
			}

		}

		if (path != null) {
			int slash = path.lastIndexOf('/');
			if (slash != -1) {
				path = path.substring(slash + 1);
			}
		}
		doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", path);
		return doc;
	}

	private static final Dimension2D VIEWPORT = new Dimension2DImpl(400, 400);

	public Image getImage(String docURI, final Document doc, final UserAgent ua) throws IOException {
		try {
			SVGOMSVGElement root = (SVGOMSVGElement) doc.getDocumentElement();
			Dimension2D viewport = VIEWPORT;
			try {
				SVGRect r = root.getViewBox().getBaseVal();
				viewport = new Dimension2DImpl(r.getWidth(), r.getHeight());
			} catch (Exception e) {
				// ignore
			}
			MyBridgeContext ctx = new MyBridgeContext(docURI, ua, viewport, this);
			GVTBuilder gvt = new PDFGVTBuilder();
			GraphicsNode gvtRoot = gvt.build(ctx, doc);

			String width = root.getAttribute("width");
			String height = root.getAttribute("height");
			if ((width == null || width.length() == 0) && (height != null && height.length() > 0)) {
				root.setAttribute("width", height);
			} else if ((height == null || height.length() == 0) && (width != null && width.length() > 0)) {
				root.setAttribute("height", width);
			}
			// 'width' attribute - default is 100%
			AbstractSVGAnimatedLength _width = (AbstractSVGAnimatedLength) root.getWidth();
			double w = _width.getCheckedValue();

			// 'height' attribute - default is 100%
			AbstractSVGAnimatedLength _height = (AbstractSVGAnimatedLength) root.getHeight();
			double h = _height.getCheckedValue();

			ImageMap imageMap = ctx.imageMap;
			Image image = new SVGImage(gvtRoot, w, h);
			ua.getUAContext().getImageMaps().put(image, imageMap);
			return image;
		} catch (BridgeException e) {
			IOException ioe = new IOException("SVGを読み込めませんでした。");
			ioe.initCause(e);
			throw ioe;
		}
	}
}
