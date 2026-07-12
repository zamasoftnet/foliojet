package net.zamasoft.foliojet.impl.ua.pdf;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.xml.sax.Attributes;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.impl.ua.AbstractVisitor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPdfVersion;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.annot.LinkAnnot;
import net.zamasoft.pdfg2d.pdf.form.CheckBoxField;
import net.zamasoft.pdfg2d.pdf.form.FormField;
import net.zamasoft.pdfg2d.pdf.form.PushButtonField;
import net.zamasoft.pdfg2d.pdf.form.TextField;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

public class PDFVisitor extends AbstractVisitor {
	private PDFGC gc;

	/** Sequence for naming controls that lack a {@code name} attribute. */
	private int fieldSeq = 0;

	/** Cleared once the target profile is found to reject forms (PDF/X). */
	private boolean formsSupported = true;

	protected PDFVisitor(UserAgent ua) {
		super(ua);
		final short version = UAProps.OUTPUT_PDF_VERSION.getCode(this.ua);
		final boolean pdfx = version == OutputPdfVersion.V1_4X1 || version == OutputPdfVersion.V1_6X4
				|| version == OutputPdfVersion.V2_0X6;
		boolean links = UAProps.OUTPUT_PDF_HYPERLINKS.getBoolean(this.ua);
		if (links && version == OutputPdfVersion.V1_4X1) {
			this.ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_HYPERLINKS.name,
					String.valueOf(true), "PDF/X-1a");
			links = false;
		}
		this.setHyperlinks(links);
		this.setFragments(UAProps.OUTPUT_PDF_HYPERLINKS_FRAGMENT.getBoolean(this.ua));
		this.setBookmarks(UAProps.OUTPUT_PDF_BOOKMARKS.getBoolean(this.ua));

		boolean forms = UAProps.OUTPUT_PDF_FORMS.getBoolean(this.ua);
		if (forms && pdfx) {
			// PDF/X forbids interactive form fields; keep the static appearance.
			this.ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_FORMS.name,
					String.valueOf(true), "PDF/X");
			forms = false;
		}
		this.setForms(forms);
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

	@Override
	protected void addFormField(Shape rectShape, IBox box, CSSElement ce) {
		if (!this.formsSupported) {
			return;
		}
		final PDFPageOutput pdfOut = (PDFPageOutput) this.gc.getPDFGraphicsOutput();
		final Rectangle2D b = rectShape.getBounds2D();
		final Rectangle2D.Double rect = new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());

		final Attributes atts = ce.atts;
		final String lName = ce.lName.toLowerCase(Locale.ROOT);
		String name = atts.getValue("name");
		if (name == null || name.isEmpty()) {
			name = "field" + (++this.fieldSeq);
		}
		final String tooltip = atts.getValue("title");
		final boolean disabled = atts.getValue("disabled") != null;

		final FormField field;
		if (lName.equals("textarea")) {
			final StringBuilder sb = new StringBuilder();
			box.getText(sb);
			field = new TextField(name, rect, sb.toString(), tooltip, 0, true, 0, disabled, false);
		} else {
			// <input>
			final String type = atts.getValue("type");
			final String t = (type == null) ? "text" : type.trim().toLowerCase(Locale.ROOT);
			final boolean checked = atts.getValue("checked") != null;
			final String value = atts.getValue("value");
			switch (t) {
			case "checkbox":
				field = new CheckBoxField(name, rect, value != null ? value : "On", checked, false, tooltip, disabled,
						false);
				break;
			case "radio":
				field = new CheckBoxField(name, rect, value != null ? value : "On", checked, true, tooltip, disabled,
						false);
				break;
			case "submit":
			case "reset":
			case "button":
				field = new PushButtonField(name, rect, value, tooltip, disabled);
				break;
			case "hidden":
			case "file":
			case "image":
				// Not represented as a simple interactive field.
				return;
			default:
				// text, password, and text-like HTML5 types (search, email, ...)
				int maxLen = 0;
				final String ml = atts.getValue("maxlength");
				if (ml != null) {
					try {
						maxLen = Integer.parseInt(ml.trim());
					} catch (NumberFormatException e) {
						// ignore a malformed maxlength
					}
				}
				field = new TextField(name, rect, value, tooltip, 0, false, maxLen, disabled, false);
			}
		}
		try {
			pdfOut.addFormField(field);
		} catch (UnsupportedOperationException e) {
			// The target profile forbids interactive forms; stop trying.
			this.formsSupported = false;
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
