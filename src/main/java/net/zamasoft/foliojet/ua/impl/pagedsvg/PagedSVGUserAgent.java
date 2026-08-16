package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;

/** Produces stable URI-addressed pages, shared WOFF2 subsets and image assets. */
public class PagedSVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private static final String SVG_NS = "http://www.w3.org/2000/svg";
	private Results results, savedResults;
	private boolean middleStateSaved;
	private FontManagerImpl fontManager;
	private PagedSVGGraphics2D svg;
	private PagedSVGResources.PageData currentPage;
	private PagedSVGResources resources;
	private PagedSVGVisitor visitor;
	private int page;
	private final Map<String, String> metadata = new LinkedHashMap<>();

	public PagedSVGUserAgent() {
		this.resetOutput();
	}

	@Override
	public void setResults(final Results results) {
		this.results = results;
		this.resetOutput();
	}

	@Override
	public void prepare(final PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS -> {
			if (!this.middleStateSaved) {
				this.savedResults = this.results;
				this.middleStateSaved = true;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.resetOutput();
		}
		case LAST_PASS -> {
			if (this.middleStateSaved) {
				this.results = this.savedResults;
				this.savedResults = null;
				this.middleStateSaved = false;
			}
			this.resetOutput();
		}
		default -> {
			// keep the current document state
		}
		}
	}

	private void resetOutput() {
		this.svg = null;
		this.currentPage = null;
		this.page = 0;
		this.metadata.clear();
		this.resources = new PagedSVGResources(this::emit);
		this.visitor = null;
	}

	@Override
	public FontManager getFontManager() {
		if (this.fontManager == null) {
			this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
		}
		return this.fontManager;
	}

	@Override
	public void meta(final String name, final String content) {
		if (name != null && content != null) {
			this.metadata.put(name, content);
		}
	}

	@Override
	protected GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		final int number = ++this.page;
		this.currentPage = new PagedSVGResources.PageData(number, this.pageWidth, this.pageHeight);
		final DOMImplementation dom = GenericDOMImplementation.getDOMImplementation();
		final Document document = dom.createDocument(SVG_NS, "svg", null);
		final SVGGeneratorContext context = SVGGeneratorContext.createDefault(document);
		context.setImageHandler(new PagedSVGImageHandler(this.resources));
		context.setPrecision(6);
		this.svg = new PagedSVGGraphics2D(context);
		this.svg.setSVGCanvasSize(new Dimension((int) Math.ceil(this.pageWidth), (int) Math.ceil(this.pageHeight)));
		return new PagedSVGGC(this.svg, this.getFontManager(), this.resources, this.currentPage);
	}

	@Override
	public void closePage(final GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		try {
			final Element root = this.svg.getRoot();
			root.setAttribute("width", PagedSVGResources.number(this.currentPage.width));
			root.setAttribute("height", PagedSVGResources.number(this.currentPage.height));
			root.setAttribute("viewBox", "0 0 " + PagedSVGResources.number(this.currentPage.width) + " "
					+ PagedSVGResources.number(this.currentPage.height));
			this.addFontFaces(root);
			final ByteArrayOutputStream svgBytes = new ByteArrayOutputStream(1 << 15);
			try (var writer = new OutputStreamWriter(svgBytes, StandardCharsets.UTF_8)) {
				this.svg.stream(root, writer, true, true);
			}
			final String stem = String.format(Locale.ROOT, "pages/%04d", this.currentPage.number);
			final String svgUri = stem + ".svg";
			final byte[] svgData = svgBytes.toByteArray();
			this.emit(svgUri, "image/svg+xml", svgData);

			final String jsonUri = stem + ".json";
			final byte[] jsonData = this.currentPage.json();
			this.emit(jsonUri, "application/json", jsonData);
			this.resources.addPage(new PagedSVGResources.PageAsset(this.currentPage.number, this.currentPage.width,
					this.currentPage.height, svgUri, PagedSVGResources.sha256(svgData), jsonUri,
					PagedSVGResources.sha256(jsonData)));
		} catch (final IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.svg = null;
			this.currentPage = null;
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	private void addFontFaces(final Element root) {
		if (this.currentPage.fonts.isEmpty()) {
			return;
		}
		final Document document = root.getOwnerDocument();
		Element defs = null;
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && "defs".equals(element.getLocalName())) {
				defs = element;
				break;
			}
		}
		if (defs == null) {
			defs = document.createElementNS(SVG_NS, "defs");
			root.insertBefore(defs, root.getFirstChild());
		}
		final Element style = document.createElementNS(SVG_NS, "style");
		style.setAttribute("type", "text/css");
		final StringBuilder css = new StringBuilder();
		for (final var font : this.currentPage.fonts.entrySet()) {
			css.append("@font-face{font-family:'").append(font.getKey()).append("';src:url('../")
					.append(font.getValue()).append("') format('woff2');font-display:block;}");
		}
		style.setTextContent(css.toString());
		defs.appendChild(style);
	}

	private void emit(final String uri, final String mimeType, final byte[] bytes) throws IOException {
		if (this.results == null) {
			throw new IOException("Results is not set");
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
		final var metadata = new SimpleSourceMetadata(URI.create(uri), mimeType, null, bytes.length);
		final FragmentedOutput builder = this.results.nextBuilder(metadata);
		try {
			final OutputStream out;
			if (builder instanceof SequentialOutput sequential) {
				out = new SequentialOutputAdapter(sequential);
			} else {
				builder.addFragment();
				out = new FragmentOutputAdapter(builder, 0);
			}
			try (out) {
				out.write(bytes);
			}
		} finally {
			builder.close();
		}
	}

	@Override
	public void finish() throws BrokenResultException, IOException {
		super.finish();
		this.resources.emitFonts();
		final String binding = this.getBoundSide() == null ? "single"
				: this.getBoundSide().name().toLowerCase(Locale.ROOT);
		this.emit("manifest.json", "application/json", this.resources.manifest(this.metadata, binding));
		this.results.end();
	}

	@Override
	public Visitor getVisitor(final GC gc) {
		if (gc == null) {
			return new NopVisitor(this);
		}
		if (this.visitor == null) {
			this.visitor = new PagedSVGVisitor(this, this.resources);
		}
		this.visitor.nextPage(gc, this.currentPage);
		return this.visitor;
	}

	@Override
	public void dispose() {
		if (this.fontManager != null) {
			this.fontManager.close();
			this.fontManager = null;
		}
		super.dispose();
	}
}
