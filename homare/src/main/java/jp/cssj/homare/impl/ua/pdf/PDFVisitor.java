package jp.cssj.homare.impl.ua.pdf;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.URI;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.impl.ua.AbstractVisitor;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.props.OutputPdfVersion;
import jp.cssj.homare.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.annot.LinkAnnot;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

public class PDFVisitor extends AbstractVisitor {
	private PDFGC gc;

	protected PDFVisitor(UserAgent ua) {
		super(ua);
		boolean links = UAProps.OUTPUT_PDF_HYPERLINKS.getBoolean(this.ua);
		if (links && UAProps.OUTPUT_PDF_VERSION.getCode(this.ua) == OutputPdfVersion.V1_4X1) {
			this.ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_HYPERLINKS.name,
					String.valueOf(true), "PDF/X-1a");
			links = false;
		}
		this.setHyperlinks(links);
		this.setFragments(UAProps.OUTPUT_PDF_HYPERLINKS_FRAGMENT.getBoolean(this.ua));
		this.setBookmarks(UAProps.OUTPUT_PDF_BOOKMARKS.getBoolean(this.ua));
	}

	protected void addLink(Shape s, URI uri, CSSElement ce) {
		PDFPageOutput pdfOut = (PDFPageOutput) this.gc.getPDFGraphicsOutput();
		AffineTransform at = this.gc.getTransform();
		if (at != null) {
			s = at.createTransformedShape(s);
		}

		LinkAnnot link = new LinkAnnot();
		link.setShape(s);
		link.setURI(uri);
		try {
			pdfOut.addAnnotation(link);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	protected void addFragment(String id, Point2D location) {
		PDFPageOutput pdfOut = (PDFPageOutput) this.gc.getPDFGraphicsOutput();
		AffineTransform at = gc.getTransform();
		if (at != null) {
			at.transform(location, location);
		}
		try {
			pdfOut.addFragment(id, location);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	protected void startBookmark(String title, Point2D location) {
		PDFPageOutput pdfOut = (PDFPageOutput) this.gc.getPDFGraphicsOutput();
		AffineTransform at = gc.getTransform();
		if (at != null) {
			at.transform(location, location);
		}
		try {
			pdfOut.startBookmark(title, location);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	protected void endBookmark() {
		PDFPageOutput pdfOut = (PDFPageOutput) this.gc.getPDFGraphicsOutput();
		try {
			pdfOut.endBookmark();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	public void nextPage(PDFGC gc) {
		super.nextPage();
		this.gc = gc;
	}
};
