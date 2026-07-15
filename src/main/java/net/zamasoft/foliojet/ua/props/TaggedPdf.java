package net.zamasoft.foliojet.ua.props;

import java.util.Locale;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Helpers for tagged-PDF output: whether logical structure is being produced,
 * and the mapping from an HTML element to a PDF structure type (ISO 32000
 * table 333 / PDF/UA).
 *
 * @author MIYABE Tatsuhiko
 */
public final class TaggedPdf {

	private TaggedPdf() {
		// static use only
	}

	/**
	 * Returns whether the target output produces a tagged PDF: either
	 * {@code output.pdf.tagged} is set, or the version is a profile that
	 * mandates logical structure (PDF/A-2a, PDF/A-3a, PDF/UA-1).
	 *
	 * @param ua the user agent
	 * @return {@code true} if a structure tree is being produced
	 */
	public static boolean isActive(final UserAgent ua) {
		return UAProps.OUTPUT_PDF_VERSION.get(ua).requiresTagging() || UAProps.OUTPUT_PDF_TAGGED.getBoolean(ua);
	}

	/**
	 * Convenience for box draw code: returns the structure role to open for the
	 * given box element when tagging is active, or {@code null} otherwise.
	 *
	 * @param ua      the user agent
	 * @param element the box's element (a {@link CSSElement} or {@code null})
	 * @return the role to open, or {@code null} to open nothing
	 */
	public static String roleIfActive(final UserAgent ua, final Object element) {
		if (!(element instanceof CSSElement ce) || !isActive(ua)) {
			return null;
		}
		return blockRole(ce);
	}

	/**
	 * Returns the table-header {@code Scope} for a {@code <th>} element, from
	 * its HTML {@code scope} attribute, defaulting to {@code "Column"} (the
	 * common case of a heading row).
	 *
	 * @param element the {@code <th>} element
	 * @return {@code "Row"}, {@code "Column"} or {@code "Both"}
	 */
	public static String headerScope(final Object element) {
		if (element instanceof CSSElement ce && ce.atts != null) {
			final String scope = ce.atts.getValue("scope");
			if (scope != null) {
				switch (scope.trim().toLowerCase(Locale.ROOT)) {
				case "row":
				case "rowgroup":
					return "Row";
				case "col":
				case "colgroup":
					return "Column";
				}
			}
		}
		return "Column";
	}

	/**
	 * Returns the PDF structure type for a block-level HTML element, or
	 * {@code null} when the element should not open its own structure element
	 * (its content attaches to the enclosing element instead).
	 *
	 * @param ce the element, or {@code null}
	 * @return the structure role (e.g. {@code "H1"}, {@code "P"}, {@code "L"}),
	 *         or {@code null}
	 */
	public static String blockRole(final CSSElement ce) {
		if (ce == null || ce.lName == null) {
			return null;
		}
		switch (ce.lName.toLowerCase(Locale.ROOT)) {
		case "h1":
			return "H1";
		case "h2":
			return "H2";
		case "h3":
			return "H3";
		case "h4":
			return "H4";
		case "h5":
			return "H5";
		case "h6":
			return "H6";
		case "p":
			return "P";
		case "ul":
		case "ol":
		case "dl":
			return "L";
		case "li":
			return "LI";
		case "dt":
			return "Lbl";
		case "dd":
			return "LBody";
		case "blockquote":
			return "BlockQuote";
		case "caption":
		case "figcaption":
			return "Caption";
		case "img":
		case "svg":
		case "object":
			return "Figure";
		case "table":
			return "Table";
		case "tr":
			return "TR";
		case "td":
			return "TD";
		case "th":
			return "TH";
		case "thead":
		case "tbody":
		case "tfoot":
			return "TBody";
		case "section":
		case "article":
		case "aside":
		case "nav":
		case "header":
		case "footer":
		case "main":
			return "Sect";
		case "div":
		case "form":
		case "fieldset":
			return "Div";
		default:
			return null;
		}
	}
}
