package net.zamasoft.foliojet.css;

import java.net.URI;

import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.impl.css.property.box.Display;
import net.zamasoft.foliojet.impl.css.property.box.Height;
import net.zamasoft.foliojet.impl.css.property.text.WhiteSpace;
import net.zamasoft.foliojet.impl.css.property.box.Width;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.foliojet.css.parser.CSSException;

/**
 * CSSに関する処理命令を処理します。
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
		// スタイルシートのスタイル宣言
		this.styleContext.startElement(ce);
		Declaration declaration = this.styleContext.merge(null);

		// インラインスタイル宣言
		String inlineStyleDecl;
		if (this.changeDefaultNamespace) {
			inlineStyleDecl = ce.atts.getValue(XHTML.STYLE_ATTR.lName);
		} else {
			inlineStyleDecl = ce.atts.getValue(XHTML.URI, XHTML.STYLE_ATTR.lName);
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

		// HTMLスタイル
		this.html.applyStyle(style);

		// CSSスタイル
		if (declaration != null) {
			declaration.applyProperties(style);
		}

		short display = Display.get(style);
		if (display == DisplayValue.TABLE_CELL && Width.getLength(style).getType() == LengthType.ABSOLUTE) {
			// 幅指定されている場合はwhite-space: normal;を適用する
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
