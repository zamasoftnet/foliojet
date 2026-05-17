package jp.cssj.homare.css;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;

import jp.cssj.homare.css.html.HTMLStyle;
import jp.cssj.homare.css.property.ElementPropertySet;
import jp.cssj.homare.css.value.DisplayValue;
import jp.cssj.homare.css.value.WhiteSpaceValue;
import jp.cssj.homare.impl.css.property.Display;
import jp.cssj.homare.impl.css.property.Height;
import jp.cssj.homare.impl.css.property.WhiteSpace;
import jp.cssj.homare.impl.css.property.Width;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.style.box.params.Length;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.props.UAProps;
import jp.cssj.homare.xml.xhtml.XHTML;
import net.zamasoft.pdfg2d.sac.css.CSSException;
import net.zamasoft.pdfg2d.sac.css.InputSource;
import net.zamasoft.pdfg2d.sac.parser.Parser;

/**
 * CSSに関する処理命令を処理します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: StyleApplier.java 1608 2021-04-18 03:57:50Z miyabe $
 */
public class StyleApplier {
	private final UserAgent ua;

	private final StyleContext styleContext;

	private final Parser declParser;

	private final DeclarationBuilder declBuilder;

	private final HTMLStyle html;

	private final boolean changeDefaultNamespace;

	public StyleApplier(UserAgent ua, StyleContext styleContext) {
		this.html = new HTMLStyle();
		this.ua = ua;
		this.styleContext = styleContext;

		this.declBuilder = new DeclarationBuilder(ua);
		this.declBuilder.setPropertySet(ElementPropertySet.getInstance());

		this.declParser = new Parser();
		this.declParser.setDocumentHandler(this.declBuilder);

		this.changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		this.setBaseURI(ua.getDocumentContext().getBaseURI());
	}

	public StyleContext getStyleContext() {
		return this.styleContext;
	}

	public void setBaseURI(URI uri) {
		assert uri != null;
		this.declBuilder.setURI(uri);
	}

	public URI getBaseURI() {
		return this.declBuilder.getURI();
	}

	public void startStyle(CSSStyle style) {
		CSSElement ce = style.getCSSElement();
		// スタイルシートのスタイル宣言
		this.styleContext.startElement(ce);
		Declaration declaration = this.styleContext.merge(null);

		// インラインスタイル宣言
		this.declBuilder.setDeclaration(declaration);
		String inlineStyleDecl;
		if (this.changeDefaultNamespace) {
			inlineStyleDecl = ce.atts.getValue(XHTML.STYLE_ATTR.lName);
		} else {
			inlineStyleDecl = ce.atts.getValue(XHTML.URI, XHTML.STYLE_ATTR.lName);
		}
		if (inlineStyleDecl != null) {
			try {
				this.declParser.parseStyleDeclaration(new InputSource(new StringReader(inlineStyleDecl)));
				declaration = this.declBuilder.getDeclaration();
			} catch (CSSException e) {
				this.ua.message(MessageCodes.WARN_BAD_INLINE_CSS, inlineStyleDecl, e.getMessage());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// HTMLスタイル
		this.html.applyStyle(style);

		// CSSスタイル
		if (declaration != null) {
			declaration.applyProperties(style);
		}

		short display = Display.get(style);
		if (display == DisplayValue.TABLE_CELL && Width.getLength(style).getType() == Length.TYPE_ABSOLUTE) {
			// 幅指定されている場合はwhite-space: normal;を適用する
			Length length;
			final CSSStyle pStyle = style.getParentStyle();
			if (pStyle != null && StyleUtils.isVertical(BlockFlow.get(pStyle))) {
				length = Height.getLength(style);
			} else {
				length = Width.getLength(style);
			}
			if (length.getType() == Length.TYPE_ABSOLUTE) {
				style.set(WhiteSpace.INFO, WhiteSpaceValue.NORMAL_VALUE);
			}
		}
	}

	public void endStyle() {
		this.styleContext.endElement();
	}
}