package net.zamasoft.foliojet.ua.impl.svg;

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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGRect;

import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.ua.impl.svg.Dimension2DImpl;
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

	/** Sequence number ensuring uniqueness of synthetic URIs ({@link #toBatikDocURI}). */
	private static final java.util.concurrent.atomic.AtomicLong INLINE_SEQ = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * Returns the document URI to pass to Batik.
	 * <p>
	 * Using opaque URIs such as data: as document URIs breaks various Batik internals:
	 * relative resolution (java.net.URI#resolve does not support opaque bases),
	 * same-document detection (url(#id) clip and gradient references are mistaken for other
	 * documents, causing reparsing of the reference string itself or empty clips), and more.
	 * Repeated workarounds (rebuilding fragments in the ParsedURL handler, retrying without
	 * clip-path, etc.) still left gaps (discovered on 2026-08-06–07 in yahoo.co.jp icons).
	 * Give documents with opaque URIs unique synthetic hierarchical URIs to remove the cause entirely.
	 * Relative references within opaque URIs cannot resolve in the first place,
	 * so this replacement loses no information.
	 * </p>
	 */
	private static String toBatikDocURI(URI uri) {
		if (!uri.isOpaque()) {
			return uri.toString();
		}
		return syntheticDocURI();
	}

	/**
	 * Converts an inline SVG document URI (= the HTML document's base URI) for Batik
	 * (2026-10-04, item ④ of TECH-20261003-004).
	 * <p>
	 * The base may be <b>relative</b>: when REST multipart sends the body as a file,
	 * its file name (main.xhtml) becomes the document URI unchanged. Batik created
	 * {@code file:.} from relative document URIs, causing URISyntaxException for every
	 * same-document reference such as {@code url(#arrow)}. Supply a synthetic URI as for opaque URIs
	 * (relative references from relative bases could not resolve in the first place).
	 * </p>
	 */
	public static String toBatikInlineURI(URI uri) {
		if (uri.isAbsolute() && !uri.isOpaque()) {
			return uri.toString();
		}
		final String path = uri.isOpaque() ? null : uri.getRawPath();
		if (path != null && !path.isEmpty() && !path.startsWith("/")) {
			// **Retain the relative base path under the synthetic URI** (2026-10-04, item ⑲ of TECH-20261003-004).
			// EPUB item document URIs are relative paths within the archive (EPUB/text/book.xhtml).
			// An inner <image xlink:href="../images/x.jpg"> resolves under the synthetic URI and
			// converts back to relative on fetching (toSourceURI), matching the relative URI used by HTML <img>.
			return "http://" + INLINE_HOST + "/" + INLINE_SEQ.incrementAndGet() + "/" + path;
		}
		return syntheticDocURI();
	}

	/**
	 * Converts a URI resolved by Batik under a synthetic URI back to its fetching URI (2026-10-04).
	 * If it is under a synthetic URI that retains a relative base ({@link #toBatikInlineURI}),
	 * return that relative URI. Otherwise leave it unchanged (references to the synthetic URI
	 * alone remain unfetchable, as before).
	 */
	public static URI toSourceURI(final URI uri) {
		if (!"http".equals(uri.getScheme()) || !INLINE_HOST.equals(uri.getHost())) {
			return uri;
		}
		final java.util.regex.Matcher m = INLINE_RELATIVE.matcher(uri.getRawPath());
		if (!m.matches()) {
			return uri;
		}
		final StringBuilder relative = new StringBuilder(m.group(1));
		if (uri.getRawQuery() != null) {
			relative.append('?').append(uri.getRawQuery());
		}
		if (uri.getRawFragment() != null) {
			relative.append('#').append(uri.getRawFragment());
		}
		return URI.create(relative.toString());
	}

	private static final String INLINE_HOST = "svg-inline.invalid";

	private static final java.util.regex.Pattern INLINE_RELATIVE = java.util.regex.Pattern.compile("/[0-9]+/(.+)");

	private static String syntheticDocURI() {
		// Use http form so Batik's standard http protocol handler processes it
		// (custom schemes use MyParsedURLDefaultProtocolHandler's incomplete
		// ParsedURLData, breaking reference resolution through CSS). The host uses
		// RFC 2606's reserved .invalid; it does not exist, collide, or allow successful fetching.
		return "http://" + INLINE_HOST + "/" + INLINE_SEQ.incrementAndGet() + ".svg";
	}

	public Image loadImage(final UserAgent ua, Source source) throws IOException {
		SVGOMDocument doc = (SVGOMDocument) this.loadDocument(source);
		// loadDocument replaces opaque URIs with synthetic ones, so use the document's
		// own URL (= the value passed to createDocument). source.getURI() would
		// differ from the document URL and break same-document detection again.
		return getImage(doc.getURL(), doc, ua);
	}

	/**
	 * Loads an external SVG with CSS {@code currentColor} baked into the root.
	 * Used for the approximation that renders {@code mask-image:url(...)} as a monochrome SVG.
	 */
	public Image loadImage(final UserAgent ua, final Source source, final Color color) throws IOException {
		final SVGOMDocument doc = (SVGOMDocument) this.loadDocument(source);
		final Element root = doc.getDocumentElement();
		root.setAttribute("color", "rgb(" + Math.round(color.getRed() * 255f) + ","
				+ Math.round(color.getGreen() * 255f) + "," + Math.round(color.getBlue() * 255f) + ")");
		if (color.getAlpha() < 1f) {
			root.setAttribute("opacity", Float.toString(color.getAlpha()));
		}
		return getImage(doc.getURL(), doc, ua);
	}

	public Document loadDocument(Source source) throws IOException {
		final URI uri = source.getURI();
		// **Use a lowercase copy only to check the extension** (2026-08-28).
		// Previously, path itself was lowercased and reused,
		// including in xml:base below. For SVG file names containing uppercase letters
		// (actual example: asahi.com's logo_globePlus.svg), relative resolution via xml:base
		// produced lowercase URLs, requesting nonexistent URLs from a case-sensitive server
		// (observed response: 403).
		final String path = uri.getPath();
		boolean gzip = path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".svgz");

		// SAXSVGDocumentFactory is not thread-safe.
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
		final String uriStr = toBatikDocURI(uri);

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
			final int slash = path.lastIndexOf('/');
			final String fileName = slash == -1 ? path : path.substring(slash + 1);
			doc.getDocumentElement().setAttributeNS("http://www.w3.org/XML/1998/namespace", "base", fileName);
		}
		// Do not set xml:base when path is null (opaque URIs such as data:).
		// An empty xml:base triggers java.net.URI#resolve("")'s non-RFC behavior during base resolution,
		// dropping the last segment of the document URI (inline-svg:/1.svg → inline-svg:/).
		// Absolutizing url(#id) through CSS then differs from the document URL, failing same-document detection
		// and silently emptying clip and gradient references (identified on 2026-08-07).
		return doc;
	}

	private static final Dimension2D VIEWPORT = new Dimension2DImpl(400, 400);

	public Image getImage(String docURI, final Document doc, final UserAgent ua) throws IOException {
		// **Route resources fetched by Batik itself through FolioJet's resolver.**
		// CSS within SVG (@import, <?xml-stylesheet?>), color profiles, etc.
		// call ParsedURL.openStream() directly; without binding here,
		// they bypass input.include/input.exclude.
		// See the explanation in MyParsedURLDefaultProtocolHandler.
		final UserAgent previousUA = MyParsedURLDefaultProtocolHandler.enter(ua);
		try {
			return this.buildImage(docURI, doc, ua);
		} finally {
			MyParsedURLDefaultProtocolHandler.leave(previousUA);
		}
	}

	private Image buildImage(String docURI, final Document doc, final UserAgent ua) throws IOException {
		try {
			SVGOMSVGElement root = (SVGOMSVGElement) doc.getDocumentElement();
			Dimension2D viewport = VIEWPORT;
			double vbWidth = 0;
			double vbHeight = 0;
			try {
				SVGRect r = root.getViewBox().getBaseVal();
				vbWidth = r.getWidth();
				vbHeight = r.getHeight();
				if (vbWidth > 0 && vbHeight > 0) {
					viewport = new Dimension2DImpl(vbWidth, vbHeight);
				}
			} catch (Exception e) {
				// For absent or invalid viewBox, defer to intrinsic size resolution below.
			}
			MyBridgeContext ctx = new MyBridgeContext(docURI, ua, viewport, this);
			// A workaround here once stripped clip-path attributes and retried on BridgeException
			// (2026-08-06). The cause, failed same-document fragment reference resolution
			// (url(#id)) against data: bases, was fixed at its source by MyURIResolver.getNode()
			// always resolving fragments within the same document, so the workaround was removed (2026-08-07).
			GVTBuilder gvt = new PDFGVTBuilder();
			GraphicsNode gvtRoot = gvt.build(ctx, doc);

			String width = root.getAttribute("width");
			String height = root.getAttribute("height");
			// Determine intrinsic dimensions from **raw attributes** (2026-08-27). width defaults to
			// 100%, and with viewBox present, getCheckedValue() below resolves it to viewBox dimensions.
			// Resolved values therefore cannot distinguish this from SVGs with dimensions set by attributes.
			// Percentages do not define intrinsic dimensions (SVG2/CSS).
			final boolean attrWidthFixed = width != null && width.length() > 0 && !width.trim().endsWith("%");
			final boolean attrHeightFixed = height != null && height.length() > 0 && !height.trim().endsWith("%");
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

			// Finalize GVT geometry (bounds, clip shapes, etc.) here.
			// Batik lazily evaluates clips (clip-path) and similar geometry; evaluation depends on
			// the DOM and CSS engine held by BridgeContext through weak references.
			// FolioJet draws the constructed GraphicsNode later through the display list,
			// so garbage collection in between silently broke lazy evaluation,
			// making only clipped SVG drawings empty (dependent on GC timing;
			// discovered as disappearing yahoo.co.jp icons on 2026-08-07). Small standalone
			// documents did not reproduce it; larger documents did so more often. getBounds()
			// recursively finalizes and caches all geometry.
			gvtRoot.getBounds();

			// Supply intrinsic sizes for SVGs without width/height attributes
			// (or whose percentages resolve to 0). Use viewBox dimensions and aspect ratio if present;
			// otherwise use CSS's default replaced-element size of 300x150. Returning 0
			// makes Pattern creation (BufferedImage) for background drawing
			// abort the entire conversion with "Width (0) and height (0) cannot be <= 0"
			// (discovered on 2026-08-07 in yahoo.co.jp icons with only viewBox).
			// Intrinsic dimension kind (see Image.Intrinsic, 2026-08-27). SIZE if attributes determine
			// dimensions (including one dimension plus the viewBox ratio).
			// RATIO for only a viewBox ratio; NONE for neither. Background drawing's
			// background-size:auto uses this to choose default size rules. Previously,
			// the fallback values below were always treated as intrinsic size, so logo SVGs
			// with only viewBox were drawn at that size (hundreds of px) and overflowed the box
			// (discovered in asahi.com's footer Re:Ron logo).
			net.zamasoft.pdfg2d.gc.image.Image.Intrinsic intrinsic;
			if (attrWidthFixed && attrHeightFixed) {
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE;
			} else if (vbWidth > 0 && vbHeight > 0) {
				// SIZE also applies when one fixed attribute plus the viewBox ratio determines dimensions.
				intrinsic = (attrWidthFixed || attrHeightFixed)
						? net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE
						: net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.RATIO;
			} else if (attrWidthFixed || attrHeightFixed) {
				// Only one dimension (no viewBox): attribute completion above treats it as a square.
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.SIZE;
			} else {
				intrinsic = net.zamasoft.pdfg2d.gc.image.Image.Intrinsic.NONE;
			}
			if (w <= 0 || h <= 0) {
				if (vbWidth > 0 && vbHeight > 0) {
					if (w > 0) {
						h = w * vbHeight / vbWidth;
					} else if (h > 0) {
						w = h * vbWidth / vbHeight;
					} else {
						w = vbWidth;
						h = vbHeight;
					}
				} else {
					if (w <= 0) {
						w = 300;
					}
					if (h <= 0) {
						h = 150;
					}
				}
			}

			ImageMap imageMap = ctx.imageMap;
			Image image = new SVGImage(gvtRoot, w, h, intrinsic);
			ua.getUAContext().getImageMaps().put(image, imageMap);
			return image;
		} catch (BridgeException e) {
			// Include the cause message ("could not load" alone gives no indication
			// of which reference or attribute failed, requiring stack-trace instrumentation
			// for every diagnosis).
			IOException ioe = new IOException("SVGを読み込めませんでした: " + e.getMessage());
			ioe.initCause(e);
			throw ioe;
		}
	}

}
