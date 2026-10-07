package net.zamasoft.foliojet.css;

import java.net.URI;

import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.foliojet.css.parser.CSSException;

/**
 * Handles CSS processing instructions.
 *
 * @author MIYABE Tatsuhiko
 */
public class StyleApplier {
	private final UserAgent ua;

	private final StyleContext styleContext;

	private final HTMLStyle html;

	private final boolean changeDefaultNamespace;

	private URI baseURI;

	public StyleApplier(UserAgent ua, StyleContext styleContext) {
		this.html = new HTMLStyle();
		this.ua = ua;
		this.styleContext = styleContext;

		this.changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		this.setBaseURI(ua.getDocumentContext().getBaseURI());
	}

	public StyleContext getStyleContext() {
		return this.styleContext;
	}

	public void setBaseURI(URI uri) {
		assert uri != null;
		this.baseURI = uri;
	}

	public URI getBaseURI() {
		return this.baseURI;
	}

	public void startStyle(CSSStyle style) {
		CSSElement ce = style.getCSSElement();
		// Style declarations from stylesheets.
		// **Receive the UA default stylesheet separately** so that defaults from HTML attributes
		// (presentational hints) outrank UA defaults but rank below author styles,
		// following the HTML specification's cascade order (2026-08-03).
		this.styleContext.startElement(ce);
		final Declaration[] uaDeclaration = new Declaration[1];
		// Reversal when combining @layer and !important (CSS Cascade 5).
		// Populated only when layered rules exist (2026-08-03).
		final Declaration[] importantDeclaration = new Declaration[1];
		Declaration declaration = this.styleContext.merge(null, uaDeclaration, importantDeclaration);

		// Inline style declarations
		String inlineStyleDecl;
		if (this.changeDefaultNamespace) {
			inlineStyleDecl = ce.atts.getValue(XHTML.STYLE_ATTR.lName);
		} else {
			// **Elements inside foreign content such as SVG/MathML do not have style
			// in the XHTML namespace** (per the HTML5 parsing specification; found on 2026-08-06
			// while investigating broken inline SVG sizing). Also try an unqualified attribute.
			inlineStyleDecl = XHTML.getAttr(ce.atts, XHTML.STYLE_ATTR.lName);
		}
		if (inlineStyleDecl != null) {
			inlineStyleDecl = inlineStyleDecl.trim();
			try {
				declaration = DeclarationParser.parseInline(inlineStyleDecl, declaration,
						ElementPropertySet.getInstance(), this.ua, this.baseURI);
			} catch (CSSException e) {
				this.ua.message(MessageCodes.WARN_BAD_INLINE_CSS, inlineStyleDecl, e.getMessage());
			}
		}

		// UA default stylesheet (html-ua.css)
		if (uaDeclaration[0] != null) {
			uaDeclaration[0].applyProperties(style);
		}

		// HTML styles (defaults from attributes; stronger than UA defaults)
		this.html.applyStyle(style);

		// CSS styles
		if (declaration != null) {
			declaration.applyProperties(style);
		}
		// **Reverse layer order for !important** (CSS Cascade 5). After applying
		// the cascade once in normal order, apply only important declarations again in reverse order.
		// Later important declarations win, so the strongest is applied last.
		if (importantDeclaration[0] != null) {
			importantDeclaration[0].applyImportantProperties(style);
		}

		short display = Display.get(style);
		if (display == DisplayValue.TABLE_CELL && Width.getLength(style).getType() == LengthType.ABSOLUTE) {
			// Apply white-space: normal; when a width is specified.
			Length length;
			final CSSStyle pStyle = style.getParentStyle();
			if (pStyle != null && BlockFlow.get(pStyle).isVertical()) {
				length = Height.getLength(style);
			} else {
				length = Width.getLength(style);
			}
			if (length.getType() == LengthType.ABSOLUTE) {
				style.set(WhiteSpace.INFO, WhiteSpaceValue.NORMAL_VALUE);
			}
		}
	}

	public void endStyle() {
		this.styleContext.endElement();
	}
}
