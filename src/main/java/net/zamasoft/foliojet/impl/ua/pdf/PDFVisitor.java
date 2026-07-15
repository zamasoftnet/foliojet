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
import net.zamasoft.pdfg2d.pdf.form.ChoiceField;
import net.zamasoft.pdfg2d.pdf.form.FormField;
import net.zamasoft.pdfg2d.pdf.form.PushButtonField;
import net.zamasoft.pdfg2d.pdf.form.RadioGroup;
import net.zamasoft.pdfg2d.pdf.form.TextField;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

public class PDFVisitor extends AbstractVisitor {
	private PDFGC gc;

	/** Sequence for naming controls that lack a {@code name} attribute. */
	private int fieldSeq = 0;

	/** Cleared once the target profile is found to reject forms (PDF/X). */
	private boolean formsSupported = true;

	/** {@code <select>} controls awaiting their options before emission. */
	private final java.util.List<SelectBuilder> pendingSelects = new java.util.ArrayList<>();

	/** Radio buttons grouped by field name, awaiting emission at page end. */
	private final java.util.Map<String, RadioBuilder> pendingRadios = new java.util.LinkedHashMap<>();

	/** Accumulates the buttons of one radio group (shared field name). */
	private static final class RadioBuilder {
		final String tooltip;
		final boolean disabled;
		String selectedValue;
		final java.util.List<net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button> buttons = new java.util.ArrayList<>();

		RadioBuilder(String tooltip, boolean disabled) {
			this.tooltip = tooltip;
			this.disabled = disabled;
		}
	}

	private void addRadioButton(String name, Rectangle2D.Double rect, String value, boolean checked, String tooltip,
			boolean disabled) {
		final boolean[] created = { false };
		final RadioBuilder group = this.pendingRadios.computeIfAbsent(name, n -> {
			created[0] = true;
			return new RadioBuilder(tooltip, disabled);
		});
		// Each button needs a distinct on-value; fall back to its index.
		final String onValue = (value != null && !value.isEmpty()) ? value : String.valueOf(group.buttons.size());
		group.buttons.add(new net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button(rect, onValue));
		if (checked) {
			group.selectedValue = onValue;
		}
		if (created[0]) {
			// Emit the whole group (all buttons collected by paint time) at the
			// position of its first button, wrapped in one Form element.
			final String fieldName = name;
			this.drawer.visitDrawable(new PDFOutputDrawable(out -> {
				final RadioGroup rg = new RadioGroup(fieldName, group.tooltip, group.selectedValue, group.disabled,
						false, group.buttons);
				out.beginStructElement("Form");
				try {
					out.addRadioGroup(rg);
				} catch (UnsupportedOperationException e) {
					this.formsSupported = false;
				} finally {
					out.endStructElement();
				}
			}), 0, 0);
		}
	}

	/** Accumulates a {@code <select>} and its {@code <option>} children. */
	private static final class SelectBuilder {
		final String name;
		final Rectangle2D.Double rect;
		final String tooltip;
		final boolean disabled;
		final boolean combo;
		final java.util.List<String> options = new java.util.ArrayList<>();
		String selected;

		SelectBuilder(String name, Rectangle2D.Double rect, String tooltip, boolean disabled, boolean combo) {
			this.name = name;
			this.rect = rect;
			this.tooltip = tooltip;
			this.disabled = disabled;
			this.combo = combo;
		}
	}

	protected PDFVisitor(UserAgent ua) {
		super(ua);
		OutputPdfVersion version = UAProps.OUTPUT_PDF_VERSION.get(this.ua);
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

	protected void addLink(Shape s, URI uri, CSSElement ce, String contents) {
		AffineTransform at = this.gc.getTransform();
		if (at != null) {
			s = at.createTransformedShape(s);
		}

		final LinkAnnot link = new LinkAnnot();
		link.setShape(s);
		link.setURI(uri);
		// PDF/UA: a link annotation needs an alternate description (/Contents);
		// fall back to the target URI when there is no link text.
		link.setContents((contents != null && !contents.isEmpty()) ? contents : uri.toString());
		// Emit at paint time and in document order so that, when tagged, the
		// annotation nests in a Link structure element under the enclosing block
		// rather than at the document root. No-op when untagged.
		this.drawer.visitDrawable(new PDFOutputDrawable(out -> {
			out.beginStructElement("Link");
			try {
				out.addAnnotation(link);
			} finally {
				out.endStructElement();
			}
		}), 0, 0);
	}

	@Override
	protected void addFormField(Shape rectShape, IBox box, CSSElement ce) {
		if (!this.formsSupported) {
			return;
		}
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
				// Collected and emitted as one grouped field at page end.
				this.addRadioButton(name, rect, value, checked, tooltip, disabled);
				return;
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
		this.emit(field);
	}

	@Override
	protected void beginSelect(Shape rectShape, CSSElement ce) {
		if (!this.formsSupported) {
			return;
		}
		final Attributes atts = ce.atts;
		final Rectangle2D b = rectShape.getBounds2D();
		final Rectangle2D.Double rect = new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());
		String name = atts.getValue("name");
		if (name == null || name.isEmpty()) {
			name = "field" + (++this.fieldSeq);
		}
		// A list box has size>1 or is multiple; otherwise it is a drop-down.
		boolean combo = atts.getValue("multiple") == null;
		final String size = atts.getValue("size");
		if (size != null) {
			try {
				if (Integer.parseInt(size.trim()) > 1) {
					combo = false;
				}
			} catch (NumberFormatException e) {
				// ignore a malformed size
			}
		}
		final SelectBuilder select = new SelectBuilder(name, rect, atts.getValue("title"),
				atts.getValue("disabled") != null, combo);
		this.pendingSelects.add(select);
		// Emit at paint time; the option list is complete by then.
		this.drawer.visitDrawable(new PDFOutputDrawable(out -> {
			String selected = select.selected;
			if (selected == null && select.combo && !select.options.isEmpty()) {
				selected = select.options.get(0);
			}
			final ChoiceField field = new ChoiceField(select.name, select.rect, select.options, selected, select.combo,
					select.tooltip, 0, select.disabled, false);
			out.beginStructElement("Form");
			try {
				out.addFormField(field);
			} catch (UnsupportedOperationException e) {
				this.formsSupported = false;
			} finally {
				out.endStructElement();
			}
		}), 0, 0);
	}

	@Override
	protected void addSelectOption(CSSElement optionCe, IBox optionBox) {
		if (this.pendingSelects.isEmpty()) {
			return;
		}
		final SelectBuilder select = this.pendingSelects.get(this.pendingSelects.size() - 1);
		final StringBuilder sb = new StringBuilder();
		optionBox.getText(sb);
		// Collapse whitespace so the option label matches its visible text.
		final String label = sb.toString().trim().replaceAll("\\s+", " ");
		final String value = optionCe.atts.getValue("value");
		final String option = (value != null) ? value : label;
		select.options.add(option);
		if (optionCe.atts.getValue("selected") != null) {
			select.selected = option;
		}
	}

	private void emit(FormField field) {
		if (!this.formsSupported) {
			return;
		}
		// Emit at paint time and in document order so the widget nests in a Form
		// structure element under the enclosing block (PDF/UA). No-op untagged.
		this.drawer.visitDrawable(new PDFOutputDrawable(out -> {
			out.beginStructElement("Form");
			try {
				out.addFormField(field);
			} catch (UnsupportedOperationException e) {
				// The target profile forbids interactive forms; stop trying.
				this.formsSupported = false;
			} finally {
				out.endStructElement();
			}
		}), 0, 0);
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
		// Per-page form collections; their builders live on until the page is
		// painted (paint-time drawables hold direct references), so clearing at
		// the start of the next page is safe.
		this.pendingSelects.clear();
		this.pendingRadios.clear();
		this.gc = gc;
	}
};
