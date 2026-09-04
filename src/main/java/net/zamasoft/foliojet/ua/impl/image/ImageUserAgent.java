package net.zamasoft.foliojet.ua.impl.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
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

public class ImageUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	/**
	 * ラスタ画像出力の既定のフォント方針は<b>埋め込み</b>です(2026-09-03、
	 * ユーザー判断。単一SVG・ページ分割SVGと同じ)。
	 *
	 * <p>
	 * 共通の既定{@code cid-keyed}はPDFの外部参照CIDフォントを前提にした方針で、
	 * 画像にはその仕組みが無い。字形データを持たないCID-keyedフォントは
	 * AWTのシステムフォント({@code SystemCIDFont})の代替に落ち、別の面の
	 * ヒント済み輪郭で描かれていた(単一SVGで実測: 「日」が本物より6%広く
	 * 縦画が太い)。埋め込み方針ならpdfg2d自身の輪郭で、PDFと同じ字形になる。
	 * 利用者が{@code output.pdf.fonts.policy}を明示した場合はそちらに従う。
	 * </p>
	 */
	@Override
	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.getProperty(UAProps.OUTPUT_PDF_FONTS_POLICY.name) != null) {
			return super.getDefaultFontPolicy();
		}
		return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
	}

	private Results results, xresults;
	private boolean middleStateSaved = false;

	protected FontManagerImpl fontManager;

	protected BufferedImage image;

	protected int page = 0;

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
		this.image = null;
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
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		final Point2D size = new Point2D.Double(this.pageWidth, this.pageHeight);
		final double ppi = UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this);
		final double pxPerPt = ppi / 72;
		final AffineTransform at = AffineTransform.getScaleInstance(pxPerPt, pxPerPt);
		at.transform(size, size);
		final int w = (int) size.getX();
		final int h = (int) size.getY();
		this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = (Graphics2D) this.image.getGraphics();

		// 背景クリア
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, w, h);
		g2d.setColor(Color.BLACK);
		g2d.setTransform(at);

		// オブジェクトとテキストのアンチエイリアス
		if (UAProps.OUTPUT_IMAGE_ANTIALIAS.getBoolean(this)) {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		} else {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		}
		return new G2DGC(g2d, this.getFontManager());
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
			try (FileCacheImageOutputStream iout = new FileCacheImageOutputStream(out, null)) {
				Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mimeType);
				ImageWriter writer = (ImageWriter) i.next();
				try {
					writer.setOutput(iout);
					writer.write(this.image);
				} finally {
					writer.dispose();
				}
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
