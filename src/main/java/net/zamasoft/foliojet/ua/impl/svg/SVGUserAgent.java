package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Dimension;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.impl.pagedsvg.SelfContainedSVGPage;
import net.zamasoft.foliojet.ua.props.SvgTextMode;

public class SVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private Results results, xresults;
	private boolean middleStateSaved = false;

	private SVGGraphics2D svgGen;

	/**
	 * Output destination for self-contained SVG, used only with {@code output.svg.text: keep}
	 * (B-1, 2026-08-29). Bypasses Batik.
	 */
	private java.io.StringWriter directBuffer;

	private SelfContainedSVGPage directPage;

	private int page = 0;

	protected SVGUserAgent() {
		// ignore
	}

	public void setResults(Results results) {
		this.results = results;
	}

	public void prepare(PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS:
			if (!this.middleStateSaved) {
				this.xresults = this.results;
				this.middleStateSaved = true;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.reset();
			break;
		case LAST_PASS:
			if (this.middleStateSaved) {
				this.results = this.xresults;
				this.xresults = null;
				this.middleStateSaved = false;
			}
			this.reset();
			break;
		}
	}

	private void reset() {
		this.svgGen = null;
		this.directBuffer = null;
		this.directPage = null;
		this.closeOwnedFontManager();
		this.page = 0;
	}

	/** Whether to retain text as {@code <text>} (B-1, 2026-08-29). */
	private boolean keepsText() {
		return UAProps.OUTPUT_SVG_TEXT.get(this) == SvgTextMode.KEEP;
	}

	/**
	 * Defaults to <b>embedding</b> when preserving text (B-1, 2026-08-29;
	 * the same reason as page-split SVG). The shared default, {@code cid-keyed}, references
	 * external CID-keyed fonts in PDF, a mechanism SVG lacks. Without this change, all glyphs
	 * fall back to outlines, leaving no {@code <text>}.
	 * Honor the user's explicit setting.
	 */
	@Override
	protected boolean embedsFontsByDefault() {
		// Use the same default in outline mode too (2026-09-02). Previously, this applied only to keep; outline
		// used the shared default (cid-keyed first for print). SVG has no CID-keyed font data,
		// so AWT fallback fonts (different faces with hinted outlines) drew the glyphs,
		// making "日" 6% wider than the actual glyph with thicker vertical strokes (PLAN's "single SVG
		// outline path produces glyphs larger than the originals"). The embedding policy uses pdfg2d's
		// own outlines, matching PDF to 1/100 pt.
		return true;
	}

	/**
	 * Receives images <b>with original bytes unchanged</b> when preserving text.
	 * Images are embedded as {@code data:}, so JPEGs need not be re-encoded as PNGs.
	 */
	@Override
	public boolean keepsEncodedImages() {
		return this.keepsText() || super.keepsEncodedImages();
	}

	public FontManager getFontManager() {
		return this.ownedFontManager(false);
	}

	public void meta(String name, String content) {
		// ignore
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		if (this.keepsText()) {
			try {
				this.directBuffer = new java.io.StringWriter(1 << 14);
				this.directPage = new SelfContainedSVGPage(this.directBuffer, this.pageWidth, this.pageHeight,
						this.getFontManager());
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
			return this.directPage.gc();
		}
		Dimension dim = new Dimension((int) this.pageWidth, (int) this.pageHeight);

		DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
		Document doc = domImpl.createDocument(null, "svg", null);
		this.svgGen = new SVGGraphics2D(doc);
		this.svgGen.setSVGCanvasSize(dim);
		G2DGC gc = new G2DGC(this.svgGen, this.ownedFontManager(false));
		return gc;
	}

	public void closePage(GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		String mimeType = UAProps.OUTPUT_TYPE.getString(this);
		SourceMetadata metaSource = new SimpleSourceMetadata(URI.create("#" + (++this.page)), mimeType, null, -1);
		FragmentedOutput builder = this.results.nextBuilder(metaSource);
		try {
			OutputStream out;
			if (builder instanceof SequentialOutput) {
				out = new SequentialOutputAdapter((SequentialOutput) builder);
			} else {
				builder.addFragment();
				out = new FragmentOutputAdapter(builder, 0);
			}
			try (Writer writer = new OutputStreamWriter(out, "UTF-8")) {
				if (this.directPage != null) {
					// Subsets are built inside close. Close before streaming.
					this.directPage.close();
					writer.write(this.directBuffer.toString());
				} else {
					this.svgGen.stream(writer, true);
				}
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.directBuffer = null;
			this.directPage = null;
			builder.close();
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	public void finish() throws BrokenResultException, IOException {
		super.finish();
		this.results.end();
	}

	public Visitor getVisitor(GC gc) {
		return new NopVisitor(this);
	}
}
