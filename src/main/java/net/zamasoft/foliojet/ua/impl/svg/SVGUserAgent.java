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
import net.zamasoft.foliojet.ua.impl.pagedsvg.SelfContainedSVGPage;
import net.zamasoft.foliojet.ua.props.SvgTextMode;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;

public class SVGUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private Results results, xresults;
	private boolean middleStateSaved = false;

	private FontManagerImpl fontManager;

	private SVGGraphics2D svgGen;

	/**
	 * {@code output.svg.text: keep}のときだけ使う、1枚で完結するSVGの
	 * 書き出し先(B-1、2026-08-29)。Batikを通さない。
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
		this.fontManager = null;
		this.page = 0;
	}

	/** 文字を{@code <text>}のまま残すかどうか(B-1、2026-08-29)。 */
	private boolean keepsText() {
		return UAProps.OUTPUT_SVG_TEXT.get(this) == SvgTextMode.KEEP;
	}

	/**
	 * 文字を残すときは<b>埋め込み</b>を既定にします(B-1、2026-08-29——
	 * ページ分割SVGと同じ理由)。共通の既定{@code cid-keyed}はPDFの外部
	 * CIDフォントを参照する方針で、SVGには存在しない仕組みなので、
	 * そのままだと字形がすべてアウトラインへ落ちて{@code <text>}が
	 * 1つも残らない。利用者が明示した場合はそちらに従う。
	 */
	@Override
	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.getProperty(UAProps.OUTPUT_PDF_FONTS_POLICY.name) != null) {
			return super.getDefaultFontPolicy();
		}
		// outline モードも同じ既定にする(2026-09-02)。以前は keep だけで、outline は
		// 共通の既定(print では cid-keyed 優先)のまま組んでいた。SVG に CID-keyed の
		// 実体は無いので AWT の代替フォント(別の面・ヒント済みの輪郭)で描かれ、
		// 「日」が本物より 6% 広く縦画が太い字形になっていた(PLAN の「単一SVGの
		// outline 経路の字形が本物より大きい」)。埋め込み方針なら pdfg2d 自身の
		// 輪郭で、PDF と 1/100pt まで一致する
		return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
	}

	/**
	 * 文字を残すときは画像を<b>元のバイト列のまま</b>受け取ります。
	 * {@code data:}で埋めるので、JPEGをPNGへ焼き直す必要がない。
	 */
	@Override
	public boolean keepsEncodedImages() {
		return this.keepsText() || super.keepsEncodedImages();
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
		G2DGC gc = new G2DGC(this.svgGen, this.fontManager);
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
					// サブセットの組み立ては close の中。閉じてから流す
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
