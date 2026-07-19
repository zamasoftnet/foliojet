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
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.foliojet.ua.PrepareMode;

public class SVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private Results results, xresults;

	private FontManagerImpl fontManager;

	private SVGGraphics2D svgGen;

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
			if (this.results != NopResults.SHARED_INSTANCE) {
				this.xresults = this.results;
				this.results = NopResults.SHARED_INSTANCE;
			}
			this.reset();
			break;
		case LAST_PASS:
			this.results = this.xresults;
			this.reset();
			break;
		}
	}

	private void reset() {
		this.svgGen = null;
		this.fontManager = null;
		this.page = 0;
	}

	public FontManager getFontManager() {
		if (this.fontManager == null) {
			this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
		}
		return this.fontManager;
	}

	public void meta(String name, String content) {
		// ignore
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		Dimension dim = new Dimension((int) this.pageWidth, (int) this.pageHeight);

		DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
		Document doc = domImpl.createDocument(null, "svg", null);
		this.svgGen = new SVGGraphics2D(doc);
		this.svgGen.setSVGCanvasSize(dim);
		G2DGC gc = new G2DGC(this.svgGen, this.fontManager);
		return gc;
	}

	public void closePage(GC gc) throws IOException {
		super.closePage(gc);
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
				this.svgGen.stream(writer, true);
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
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
