package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Dimension;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
import net.zamasoft.foliojet.ua.ImageMetricsXML;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.ua.props.PagedSvgResourcePolicy;
import net.zamasoft.foliojet.ua.props.PagedSvgWriter;
import net.zamasoft.foliojet.ua.props.UAProps;
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
	/** 独自書き出しのときだけ使う。Batikのときはnull。 */
	private java.io.StringWriter directBuffer;
	private SVGPageOutput directPage;
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
		this.directBuffer = null;
		this.directPage = null;
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
		if (number == 1) {
			this.resources.setResourcePolicies(UAProps.OUTPUT_PAGED_SVG_FONTS.get(this),
					UAProps.OUTPUT_PAGED_SVG_IMAGES.get(this));
			this.resources.setResourceMode(UAProps.OUTPUT_PAGED_SVG_RESOURCES.get(this));
		}
		this.currentPage = new PagedSVGResources.PageData(number, this.pageWidth, this.pageHeight);
		if (UAProps.OUTPUT_PAGED_SVG_WRITER.get(this) == PagedSvgWriter.DIRECT) {
			// DOMを作らず書き出す。ページの内容はここでは確定しないので、
			// 結果への書き出しは closePage で行う(ハッシュを流しながら
			// 取るため、結果1件は1回のストリームで書き切る必要がある)
			try {
				this.directBuffer = new java.io.StringWriter(1 << 14);
				this.directPage = new SVGPageOutput(this.directBuffer, this.pageWidth, this.pageHeight);
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
			return new DirectPagedSVGGC(this.directPage.writer(), this.getFontManager(), this.resources,
					this.currentPage);
		}
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
			if (this.directPage != null) {
				this.closeDirectPage();
				return;
			}
			final Element root = this.svg.getRoot();
			root.setAttribute("width", PagedSVGResources.number(this.currentPage.width));
			root.setAttribute("height", PagedSVGResources.number(this.currentPage.height));
			root.setAttribute("viewBox", "0 0 " + PagedSVGResources.number(this.currentPage.width) + " "
					+ PagedSVGResources.number(this.currentPage.height));
			this.addFontFaces(root);
			final String stem = String.format(Locale.ROOT, "pages/%04d", this.currentPage.number);
			final String svgUri = stem + ".svg";
			// **溜めずに流す**(2026-08-16)。以前はページSVG全体を
			// ByteArrayOutputStreamへ作ってから書いていた。直列化と
			// SHA-256は流しながらできる。圧縮は行わない——配信の都合は
			// 受け手のほうがよく知っているので、クライアント側に任せる
			final Element streamed = root;
			final String svgSha = this.emit(svgUri, "image/svg+xml", out -> {
				try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
					this.svg.stream(streamed, writer, true, true);
				}
			});

			final String jsonUri = stem + ".json";
			final PagedSVGResources.PageData page = this.currentPage;
			final String jsonSha = this.emit(jsonUri, "application/json", out -> {
				try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
					page.writeJson(writer);
				}
			});
			this.resources.addPage(new PagedSVGResources.PageAsset(this.currentPage.number, this.currentPage.width,
					this.currentPage.height, svgUri, svgSha, jsonUri, jsonSha));
		} catch (final IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.svg = null;
			this.directBuffer = null;
			this.directPage = null;
			this.currentPage = null;
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	/**
	 * 独自書き出しのページを閉じます。
	 *
	 * <p>
	 * 中身は{@link java.io.StringWriter}へ組み立ててから1回で書き出します。
	 * DOMを作らない点はそのままですが、<b>結果1件を1回のストリームで
	 * 書き切らないとSHA-256を流しながら取れない</b>ためです。Batik版は
	 * ページ全体のDOMを保持していたので、これでも保持量は減ります。
	 * </p>
	 */
	private void closeDirectPage() throws IOException {
		this.directPage.close();
		final String svg = this.directBuffer.toString();
		this.directBuffer = null;
		this.directPage = null;

		final String stem = String.format(Locale.ROOT, "pages/%04d", this.currentPage.number);
		final String svgUri = stem + ".svg";
		final String svgSha = this.emit(svgUri, "image/svg+xml", out -> {
			try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				writer.write(svg);
			}
		});
		final String jsonUri = stem + ".json";
		final PagedSVGResources.PageData page = this.currentPage;
		final String jsonSha = this.emit(jsonUri, "application/json", out -> {
			try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				page.writeJson(writer);
			}
		});
		this.resources.addPage(new PagedSVGResources.PageAsset(this.currentPage.number, this.currentPage.width,
				this.currentPage.height, svgUri, svgSha, jsonUri, jsonSha));
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

	/** 結果1件の中身を書きます。溜めずに、渡された出力へ直接書くこと。 */
	@FunctionalInterface
	interface ContentWriter {
		void write(OutputStream out) throws IOException;
	}

	private void emit(final String uri, final String mimeType, final byte[] bytes) throws IOException {
		this.emit(uri, mimeType, out -> out.write(bytes));
	}

	/**
	 * 結果を1件書き出し、そのSHA-256を返します。
	 *
	 * <p>
	 * <b>中身を溜めません</b>(2026-08-16)。ダイジェスト計算も書きながら行い、
	 * 長さは申告しません({@code -1})。以前はページSVG全体を
	 * {@code ByteArrayOutputStream}へ作ってから書いていました。
	 * 画像出力・単一SVG出力は元から直接書いており、溜めていたのはここだけです。
	 * </p>
	 *
	 * <p>
	 * 返すSHA-256は<b>実際に書いたバイト列</b>に対する値なので、
	 * manifestの記載と実体が食い違いません。
	 * </p>
	 */
	private String emit(final String uri, final String mimeType, final ContentWriter content) throws IOException {
		if (this.results == null) {
			throw new IOException("Results is not set");
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
		final var metadata = new SimpleSourceMetadata(URI.create(uri), mimeType, null, -1);
		final FragmentedOutput builder = this.results.nextBuilder(metadata);
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try {
			final OutputStream raw;
			if (builder instanceof SequentialOutput sequential) {
				raw = new SequentialOutputAdapter(sequential);
			} else {
				builder.addFragment();
				raw = new FragmentOutputAdapter(builder, 0);
			}
			try (raw) {
				content.write(new UnclosableOutputStream(new DigestOutputStream(raw, digest)));
			}
		} finally {
			builder.close();
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * 書き手が{@code close()}しても下位を閉じない包み。
	 * {@code OutputStreamWriter}を{@code try}で閉じて内容を確実に流し切りつつ、
	 * 結果の境界はこちらの手順で閉じるためです。
	 */
	private static final class UnclosableOutputStream extends FilterOutputStream {
		UnclosableOutputStream(final OutputStream out) {
			super(out);
		}

		@Override
		public void write(final byte[] b, final int off, final int len) throws IOException {
			this.out.write(b, off, len);
		}

		@Override
		public void close() throws IOException {
			this.flush();
		}
	}

	@Override
	public void finish() throws BrokenResultException, IOException {
		super.finish();
		this.resources.emitFonts();
		// 測った画像の寸法を残す。次に同じ本を別の文字サイズ・画面サイズで
		// 組むとき input.image-metrics に渡せば、寸法しか要らないパスで
		// 画像を一度も開かずに済む。
		final var imageMetrics = this.getUAContext().getImageMetrics();
		if (imageMetrics.size() != 0) {
			this.emit("metrics.xml", "application/xml", ImageMetricsXML.write(imageMetrics, UAProps.OUTPUT_RESOLUTION.getDouble(this)));
		}
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
