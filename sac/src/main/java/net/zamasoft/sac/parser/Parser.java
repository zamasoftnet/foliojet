/*

 ============================================================================
 The Apache Software License, Version 1.1
 ============================================================================

 Copyright (C) 1999-2003 The Apache Software Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without modifica-
 tion, are permitted provided that the following conditions are met:

 1. Redistributions of  source code must  retain the above copyright  notice,
 this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 3. The end-user documentation included with the redistribution, if any, must
 include  the following  acknowledgment:  "This product includes  software
 developed  by the  Apache Software Foundation  (http://www.apache.org/)."
 Alternately, this  acknowledgment may  appear in the software itself,  if
 and wherever such third-party acknowledgments normally appear.

 4. The names "Batik" and  "Apache Software Foundation" must  not  be
 used to  endorse or promote  products derived from  this software without
 prior written permission. For written permission, please contact
 apache@apache.org.

 5. Products  derived from this software may not  be called "Apache", nor may
 "Apache" appear  in their name,  without prior written permission  of the
 Apache Software Foundation.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED.  IN NO  EVENT SHALL  THE
 APACHE SOFTWARE  FOUNDATION  OR ITS CONTRIBUTORS  BE LIABLE FOR  ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLU-
 DING, BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 OF USE, DATA, OR  PROFITS; OR BUSINESS  INTERRUPTION)  HOWEVER CAUSED AND ON
 ANY  THEORY OF LIABILITY,  WHETHER  IN CONTRACT,  STRICT LIABILITY,  OR TORT
 (INCLUDING  NEGLIGENCE OR  OTHERWISE) ARISING IN  ANY WAY OUT OF THE  USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 This software  consists of voluntary contributions made  by many individuals
 on  behalf of the Apache Software  Foundation. For more  information on the
 Apache Software Foundation, please see <http://www.apache.org/>.

 */

package net.zamasoft.sac.parser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.StringTokenizer;

import net.zamasoft.sac.css.CSSException;
import net.zamasoft.sac.css.CSSParseException;
import net.zamasoft.sac.css.Condition;
import net.zamasoft.sac.css.ConditionFactory;
import net.zamasoft.sac.css.DocumentHandler;
import net.zamasoft.sac.css.ErrorHandler;
import net.zamasoft.sac.css.InputSource;
import net.zamasoft.sac.css.LexicalUnit;
import net.zamasoft.sac.css.SACMediaList;
import net.zamasoft.sac.css.Selector;
import net.zamasoft.sac.css.SelectorFactory;
import net.zamasoft.sac.css.SelectorList;
import net.zamasoft.sac.css.SimpleSelector;
import net.zamasoft.sac.i18n.Localizable;
import net.zamasoft.sac.i18n.LocalizableSupport;
import net.zamasoft.sac.util.EncodingUtilities;

/**
 * This class implements the {@link net.zamasoft.sac.css.Parser}interface.
 * 
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion </a>
 * @version $Id: Parser.java 1555 2018-04-26 04:15:29Z miyabe $
 */
public class Parser implements ExtendedParser, Localizable {

	/**
	 * The default resource bundle base name.
	 */
	public final static String BUNDLE_CLASSNAME = Parser.class.getPackage().getName() + ".resources.Messages";

	/**
	 * The localizable support.
	 */
	protected LocalizableSupport localizableSupport = new LocalizableSupport(BUNDLE_CLASSNAME,
			Parser.class.getClassLoader());

	/**
	 * The scanner used to scan the input source.
	 */
	protected Scanner scanner;

	/**
	 * The current lexical unit.
	 */
	protected int current;

	/**
	 * The document handler.
	 */
	protected DocumentHandler documentHandler = DefaultDocumentHandler.INSTANCE;

	/**
	 * The selector factory.
	 */
	protected SelectorFactory selectorFactory = DefaultSelectorFactory.INSTANCE;

	/**
	 * The condition factory.
	 */
	protected ConditionFactory conditionFactory = DefaultConditionFactory.INSTANCE;

	/**
	 * The error handler.
	 */
	protected ErrorHandler errorHandler = DefaultErrorHandler.INSTANCE;

	/**
	 * To store the current pseudo element.
	 */
	protected String pseudoElement;

	/**
	 * The document URI.
	 */
	protected String documentURI;

	/**
	 * Mapping from prefix to URI.
	 */
	protected Map<String, String> prefixToURI = new HashMap<String, String>();

	protected String defaultCharset = "UTF-8";

	public String getDefaultCharset() {
		return this.defaultCharset;
	}

	public void setDefaultCharset(String defaultCharset) {
		this.defaultCharset = defaultCharset;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#getParserVersion()} .
	 * 
	 * @return "http://www.w3.org/TR/REC-CSS2".
	 */
	public String getParserVersion() {
		return "http://www.w3.org/TR/REC-CSS2";
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#setLocale(Locale)}.
	 */
	public void setLocale(Locale locale) throws CSSException {
		this.localizableSupport.setLocale(locale);
	}

	/**
	 * Implements {@link net.zamasoft.sac.i18n.Localizable#getLocale()}.
	 */
	public Locale getLocale() {
		return this.localizableSupport.getLocale();
	}

	/**
	 * Implements
	 * {@link net.zamasoft.sac.i18n.Localizable#formatMessage(String,Object[])} .
	 */
	public String formatMessage(String key, Object[] args) throws MissingResourceException {
		return this.localizableSupport.formatMessage(key, args);
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#setDocumentHandler(DocumentHandler)}.
	 */
	public void setDocumentHandler(DocumentHandler handler) {
		this.documentHandler = handler;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#setSelectorFactory(SelectorFactory)}.
	 */
	public void setSelectorFactory(SelectorFactory factory) {
		this.selectorFactory = factory;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#setConditionFactory(ConditionFactory)}.
	 */
	public void setConditionFactory(ConditionFactory factory) {
		this.conditionFactory = factory;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#setErrorHandler(ErrorHandler)}.
	 */
	public void setErrorHandler(ErrorHandler handler) {
		this.errorHandler = handler;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parseStyleSheet(InputSource)}.
	 */
	public void parseStyleSheet(InputSource source) throws CSSException, IOException {
		boolean checkCharset;
		this.documentURI = source.getURI();
		if (this.documentURI == null) {
			this.documentURI = "";
		}

		InputStream is = null;
		Reader r = source.getCharacterStream();
		if (r != null) {
			this.scanner = new Scanner(r);
			checkCharset = false;
		} else {
			is = source.getByteStream();
			if (is == null) {
				String uri = source.getURI();
				if (uri == null) {
					throw new CSSException(this.formatMessage("empty.source", null));
				}

				URL url = new URL(uri);
				is = url.openStream();
			}
			String charset = EncodingUtilities.javaEncoding(source.getEncoding());
			checkCharset = (charset == null);
			if (checkCharset) {
				if (!is.markSupported()) {
					is = new BufferedInputStream(is);
				}
				is.mark(8192);// InputStreamReaderのバッファサイズに負けないため
			}
			if (charset == null) {
				charset = "UTF-8";
			}
			try {
				r = new InputStreamReader(is, charset);
			} catch (UnsupportedEncodingException e) {
				r = new InputStreamReader(is, "UTF-8");
			}
			this.scanner = new Scanner(r);
		}

		try {
			this.parseStyleSheet(source, false, checkCharset);
		} catch (ChangeCharacterEncodingException e) {
			try {
				is.reset();
				try {
					r = new InputStreamReader(is, e.getCharacterEncoding());
				} catch (UnsupportedEncodingException e1) {
					r = new InputStreamReader(is, "UTF-8");
				}
				this.scanner = new Scanner(r);
				this.parseStyleSheet(source, true, false);
			} catch (ChangeCharacterEncodingException e1) {
				// ignore
			}
		}
	}

	protected void parseStyleSheet(InputSource source, boolean rescan, boolean checkCharset)
			throws CSSException, IOException, ChangeCharacterEncodingException {
		boolean reset = false;
		try {
			if (!rescan) {
				this.documentHandler.startDocument(source);
			}

			try {
				this.current = this.scanner.next();
			} catch (ParseException e) {
				throw new CSSException();
			}
			skipSpacesAndCDOCDC();

			if (this.current == LexicalUnits.CHARSET_SYMBOL) {
				if (nextIgnoreSpaces() != LexicalUnits.STRING) {
					reportError("charset.string");
				} else {
					String charset = EncodingUtilities.javaEncoding(this.scanner.getStringValue());
					if (charset != null && checkCharset) {
						reset = true;
						throw new ChangeCharacterEncodingException(charset);
					}
					if (nextIgnoreSpaces() != LexicalUnits.SEMI_COLON) {
						reportError("semicolon");
					}
					next();
				}
			}

			skipSpacesAndCDOCDC();
			for (;;) {
				if (this.current == LexicalUnits.IMPORT_SYMBOL) {
					nextIgnoreSpaces();
					parseImportRule();
					nextIgnoreSpaces();
				} else {
					break;
				}
			}

			loop: for (;;) {
				switch (this.current) {
				case LexicalUnits.NAMESPACE_SYMBOL:
					nextIgnoreSpaces();
					parseNamespaceRule();
					break;
				case LexicalUnits.PAGE_SYMBOL:
					nextIgnoreSpaces();
					parsePageRule();
					break;
				case LexicalUnits.MEDIA_SYMBOL:
					nextIgnoreSpaces();
					parseMediaRule();
					break;
				case LexicalUnits.FONT_FACE_SYMBOL:
					nextIgnoreSpaces();
					parseFontFaceRule();
					break;
				case LexicalUnits.AT_KEYWORD:
					nextIgnoreSpaces();
					parseAtRule();
					break;
				case LexicalUnits.EOF:
					break loop;
				default:
					parseRuleSet();
				}
				skipSpacesAndCDOCDC();
			}
		} finally {
			if (!reset) {
				this.documentHandler.endDocument(source);
			}
			this.scanner = null;
		}
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parseStyleSheet(String)}.
	 */
	public void parseStyleSheet(String uri) throws CSSException, IOException {
		this.parseStyleSheet(new InputSource(uri));
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parseStyleDeclaration(InputSource)}.
	 */
	public void parseStyleDeclaration(InputSource source) throws CSSException, IOException {
		this.scanner = createScanner(source);
		parseStyleDeclarationInternal();
	}

	/**
	 * Parses a style declaration using the current scanner.
	 */
	protected void parseStyleDeclarationInternal() throws CSSException, IOException {
		this.nextIgnoreSpaces();
		try {
			this.parseStyleDeclaration(false);
		} finally {
			this.scanner = null;
		}
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parseRule(InputSource)}.
	 */
	public void parseRule(InputSource source) throws CSSException, IOException {
		this.scanner = this.createScanner(source);
		this.parseRuleInternal();
	}

	/**
	 * Parses a rule using the current scanner.
	 */
	protected void parseRuleInternal() throws CSSException, IOException {
		this.nextIgnoreSpaces();
		this.parseRule();
		this.scanner = null;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parseSelectors(InputSource)}.
	 */
	public SelectorList parseSelectors(InputSource source) throws CSSException, IOException {
		this.scanner = this.createScanner(source);
		return this.parseSelectorsInternal();
	}

	/**
	 * Parses selectors using the current scanner.
	 */
	protected SelectorList parseSelectorsInternal() throws CSSException, IOException {
		this.nextIgnoreSpaces();
		SelectorList ret = this.parseSelectorList();
		this.scanner = null;
		return ret;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parsePropertyValue(InputSource)}.
	 */
	public LexicalUnit parsePropertyValue(InputSource source) throws CSSException, IOException {
		this.scanner = this.createScanner(source);
		return this.parsePropertyValueInternal();
	}

	/**
	 * Parses property value using the current scanner.
	 */
	protected LexicalUnit parsePropertyValueInternal() throws CSSException, IOException {
		this.nextIgnoreSpaces();

		LexicalUnit exp = null;

		try {
			exp = this.parseExpression(false);
		} catch (CSSParseException e) {
			this.reportError(e);
			this.skipSemiColon();
			throw e;
		}

		CSSParseException exception = null;
		if (this.current != LexicalUnits.EOF)
			exception = this.createCSSParseException("eof.expected");

		this.scanner = null;

		if (exception != null) {
			this.errorHandler.fatalError(exception);
		}
		return exp;
	}

	/**
	 * <b>SAC </b>: Implements
	 * {@link net.zamasoft.sac.css.Parser#parsePriority(InputSource)}.
	 */
	public boolean parsePriority(InputSource source) throws CSSException, IOException {
		this.scanner = this.createScanner(source);
		return this.parsePriorityInternal();
	}

	/**
	 * Parses the priority using the current scanner.
	 */
	protected boolean parsePriorityInternal() throws CSSException, IOException {
		this.nextIgnoreSpaces();

		this.scanner = null;

		switch (this.current) {
		case LexicalUnits.EOF:
			return false;
		case LexicalUnits.IMPORT_SYMBOL:
			return true;
		default:
			this.reportError("token", new Object[] { new Integer(current) });
			this.skipSemiColon();
			return false;
		}
	}

	/**
	 * Parses a rule.
	 */
	protected void parseRule() throws IOException {
		switch (this.scanner.getType()) {
		case LexicalUnits.IMPORT_SYMBOL:
			this.nextIgnoreSpaces();
			this.parseImportRule();
			break;
		case LexicalUnits.AT_KEYWORD:
			this.nextIgnoreSpaces();
			this.parseAtRule();
			break;
		case LexicalUnits.FONT_FACE_SYMBOL:
			this.nextIgnoreSpaces();
			this.parseFontFaceRule();
			break;
		case LexicalUnits.MEDIA_SYMBOL:
			this.nextIgnoreSpaces();
			this.parseMediaRule();
			break;
		case LexicalUnits.PAGE_SYMBOL:
			this.nextIgnoreSpaces();
			this.parsePageRule();
			break;
		case LexicalUnits.NAMESPACE_SYMBOL:
			this.nextIgnoreSpaces();
			this.parseNamespaceRule();
			break;
		default:
			this.parseRuleSet();
		}
	}

	/**
	 * Parses an unknown rule.
	 */
	protected void parseAtRule() throws IOException {
		this.scanner.scanAtRule();
		this.documentHandler.ignorableAtRule(this.scanner.getStringValue());
		this.nextIgnoreSpaces();
	}

	/**
	 * Parses an import rule. Assumes the current token is '@import'.
	 */
	protected void parseImportRule() throws IOException {
		String uri = null;
		switch (this.current) {
		default:
			this.reportError("string.or.uri");
			return;
		case LexicalUnits.STRING:
		case LexicalUnits.URI:
			uri = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
		}

		CSSSACMediaList ml;
		if (this.current != LexicalUnits.IDENTIFIER) {
			ml = new CSSSACMediaList();
			ml.append("all");
		} else {
			ml = this.parseMediaList();
		}

		this.documentHandler.importStyle(uri, ml, null);

		if (this.current != LexicalUnits.SEMI_COLON) {
			this.reportError("semicolon");
		}
	}

	/**
	 * Parses an namespace rule.
	 */
	protected void parseNamespaceRule() throws IOException {
		String prefix = "";
		String uri = "";
		switch (this.current) {
		default:
			this.reportError("string.or.uri.or.identifer");
			return;
		case LexicalUnits.STRING:
		case LexicalUnits.URI:
			uri = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			break;

		case LexicalUnits.IDENTIFIER:
			prefix = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			switch (this.current) {
			default:
				reportError("string.or.uri");
				return;
			case LexicalUnits.STRING:
			case LexicalUnits.URI:
				uri = this.scanner.getStringValue();
				this.nextIgnoreSpaces();
				break;
			}
			break;
		}

		if (this.current != LexicalUnits.SEMI_COLON) {
			this.reportError("semicolon");
		} else {
			this.next();
		}

		this.prefixToURI.put(prefix, uri);
	}

	/**
	 * Parses a media list.
	 */
	protected CSSSACMediaList parseMediaList() throws IOException {
		CSSSACMediaList result = new CSSSACMediaList();
		result.append(scanner.getStringValue());
		this.nextIgnoreSpaces();

		while (current == LexicalUnits.COMMA) {
			this.nextIgnoreSpaces();

			switch (current) {
			default:
				this.reportError("identifier");
				break;
			case LexicalUnits.IDENTIFIER:
				result.append(scanner.getStringValue());
				this.nextIgnoreSpaces();
			}
		}
		return result;
	}

	/**
	 * Parses a font-face rule.
	 */
	protected void parseFontFaceRule() throws IOException {
		try {
			this.documentHandler.startFontFace();

			if (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
				this.reportError("left.curly.brace");
				this.skipRightCurlyBrace();
			} else {
				this.nextIgnoreSpaces();
				this.parseStyleDeclaration(true);
			}
		} finally {
			this.documentHandler.endFontFace();
		}
	}

	/**
	 * Parses a page rule.
	 */
	protected void parsePageRule() throws IOException {
		String page = null;
		String ppage = null;

		if (this.current == LexicalUnits.IDENTIFIER) {
			page = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
		}
		if (this.current == LexicalUnits.COLON) {
			this.nextIgnoreSpaces();

			if (this.current != LexicalUnits.IDENTIFIER) {
				this.reportError("identifier");
				this.skipRightCurlyBrace();
				return;
			}
			ppage = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
		}

		try {
			this.documentHandler.startPage(page, ppage);

			if (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
				this.reportError("left.curly.brace");
				this.skipRightCurlyBrace();
			} else {
				this.nextIgnoreSpaces();
				this.parseStyleDeclaration(true);
			}
		} finally {
			this.documentHandler.endPage(page, ppage);
		}
	}

	/**
	 * Parses a -cssj-page-content rule.
	 */
	protected void parsePageContentRule() throws IOException {
		String page = null;

		if (this.current == LexicalUnits.IDENTIFIER) {
			page = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
		}

		try {
			this.documentHandler.startPage(page, "-cssj-page-content");

			if (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
				this.reportError("left.curly.brace");
				this.skipRightCurlyBrace();
			} else {
				this.nextIgnoreSpaces();
				this.parseStyleDeclaration(true);
			}
		} finally {
			this.documentHandler.endPage(page, "-cssj-page-content");
		}
	}

	/**
	 * Parses a media rule.
	 */
	protected void parseMediaRule() throws IOException {
		if (this.current != LexicalUnits.IDENTIFIER) {
			this.skipLeftCurlyBrace();
			this.reportError("identifier");
			this.skipRightCurlyBrace();
			return;
		}

		CSSSACMediaList ml = this.parseMediaList();
		try {
			this.documentHandler.startMedia(ml);

			if (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
				this.skipLeftCurlyBrace();
				this.reportError("left.curly.brace");
				this.skipRightCurlyBrace();
			} else {
				this.nextIgnoreSpaces();

				loop: for (;;) {
					switch (current) {
					case LexicalUnits.EOF:
					case LexicalUnits.RIGHT_CURLY_BRACE:
						break loop;
					default:
						this.parseRuleSet();
					}
				}

				this.nextIgnoreSpaces();
			}
		} finally {
			this.documentHandler.endMedia(ml);
		}
	}

	private void skipLeftCurlyBrace() throws IOException {
		while (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
			if (this.current == LexicalUnits.EOF) {
				return;
			}
			this.nextIgnoreSpaces();
		}
		this.nextIgnoreSpaces();
	}

	private void skipRightCurlyBrace() throws IOException {
		int cbraces = 1;
		for (;;) {
			switch (current) {
			case LexicalUnits.EOF:
				return;
			case LexicalUnits.RIGHT_CURLY_BRACE:
				if (--cbraces == 0) {
					this.nextIgnoreSpaces();
					return;
				}
				break;
			case LexicalUnits.LEFT_CURLY_BRACE:
				cbraces++;
			}
			this.nextIgnoreSpaces();
		}
	}

	private void skipSemiColon() throws IOException {
		int cbraces = 1;
		for (;;) {
			switch (this.current) {
			case LexicalUnits.EOF:
				return;
			case LexicalUnits.SEMI_COLON:
				if (cbraces - 1 == 0) {
					this.nextIgnoreSpaces();
					return;
				}
				break;
			case LexicalUnits.RIGHT_CURLY_BRACE:
				if (--cbraces == 0) {
					return;
				}
				break;
			case LexicalUnits.LEFT_CURLY_BRACE:
				cbraces++;
			}
			this.nextIgnoreSpaces();
		}
	}

	/**
	 * Parses a ruleset.
	 */
	protected void parseRuleSet() throws IOException {
		SelectorList sl = null;

		try {
			sl = this.parseSelectorList();
		} catch (CSSParseException e) {
			this.skipLeftCurlyBrace();
			this.reportError(e);
			this.skipRightCurlyBrace();
			return;
		}

		if (this.current != LexicalUnits.LEFT_CURLY_BRACE) {
			this.skipLeftCurlyBrace();
			this.reportError("left.curly.brace");
			this.skipRightCurlyBrace();
		} else {
			try {
				this.documentHandler.startSelector(sl);
				this.nextIgnoreSpaces();
				this.parseStyleDeclaration(true);
			} finally {
				this.documentHandler.endSelector(sl);
			}
		}
	}

	/**
	 * Parses a selector list
	 */
	protected SelectorList parseSelectorList() throws IOException {
		CSSSelectorList result = new CSSSelectorList();
		result.append(parseSelector());

		for (;;) {
			if (this.current != LexicalUnits.COMMA) {
				return result;
			}
			this.nextIgnoreSpaces();
			result.append(parseSelector());
		}
	}

	/**
	 * Parses a selector.
	 */
	protected Selector parseSelector() throws IOException {
		switch (this.current) {
		case LexicalUnits.COMMA:
		case LexicalUnits.LEFT_CURLY_BRACE:
			throw this.createCSSParseException("selector");
		}

		this.pseudoElement = null;
		SimpleSelector ss = this.parseSimpleSelector();
		Selector result = ss;

		loop: for (;;) {
			switch (this.current) {
			default:
				break loop;
			case LexicalUnits.IDENTIFIER:
			case LexicalUnits.ANY:
			case LexicalUnits.HASH:
			case LexicalUnits.DOT:
			case LexicalUnits.LEFT_BRACKET:
			case LexicalUnits.COLON:
				result = this.selectorFactory.createDescendantSelector(result, this.parseSimpleSelector());
				break;
			case LexicalUnits.PLUS:
				nextIgnoreSpaces();
				result = this.selectorFactory.createDirectAdjacentSelector((short) 1, result,
						this.parseSimpleSelector());
				break;
			case LexicalUnits.PRECEDE:
				nextIgnoreSpaces();
				result = this.selectorFactory.createChildSelector(result, this.parseSimpleSelector());
			}
		}
		if (this.pseudoElement != null) {
			result = this.selectorFactory.createChildSelector(result,
					this.selectorFactory.createPseudoElementSelector(null, this.pseudoElement));
		}
		return result;
	}

	/**
	 * Parses a simple selector.
	 */
	protected SimpleSelector parseSimpleSelector() throws IOException {
		SimpleSelector result;
		if (this.pseudoElement != null) {
			result = this.selectorFactory.createPseudoElementSelector(null, pseudoElement);
			this.pseudoElement = null;
		} else {
			String prefix = null;
			String lName = null;
			switch (this.current) {
			case LexicalUnits.DASH:
				lName = "";
				break;
			case LexicalUnits.IDENTIFIER:
				lName = this.scanner.getStringValue();
			case LexicalUnits.ANY:
				this.next();
				break;
			}
			switch (this.current) {
			case LexicalUnits.DASH:
				prefix = lName;
				this.next();
				switch (this.current) {
				case LexicalUnits.IDENTIFIER:
					lName = this.scanner.getStringValue();
					next();
					break;
				case LexicalUnits.ANY:
					next();
				default:
					lName = null;
					break;
				}
			}
			String uri = ((prefix == null) ? null : (String) this.prefixToURI.get(prefix));
			result = this.selectorFactory.createElementSelector(uri, lName);
		}

		Condition cond = null;
		loop: for (;;) {
			Condition c = null;
			switch (this.current) {
			case LexicalUnits.HASH:
				c = this.conditionFactory.createIdCondition(this.scanner.getStringValue());
				this.next();
				break;
			case LexicalUnits.DOT:
				if (this.next() != LexicalUnits.IDENTIFIER) {
					throw this.createCSSParseException("identifier");
				}
				c = this.conditionFactory.createClassCondition(null, this.scanner.getStringValue());
				this.next();
				break;
			case LexicalUnits.REAL:
			case LexicalUnits.DIMENSION:
			case LexicalUnits.PERCENTAGE:
			case LexicalUnits.DEG:
			case LexicalUnits.RAD:
			case LexicalUnits.GRAD:
			case LexicalUnits.HZ:
			case LexicalUnits.KHZ:
			case LexicalUnits.MS:
			case LexicalUnits.MM:
			case LexicalUnits.CM:
			case LexicalUnits.PT:
			case LexicalUnits.PC:
			case LexicalUnits.EM:
			case LexicalUnits.EX:
			case LexicalUnits.IN:
			case LexicalUnits.S:
			case LexicalUnits.REM:
			case LexicalUnits.CH:
				break loop;
			case LexicalUnits.LEFT_BRACKET:
				nextIgnoreSpaces();
				String prefix = null;
				String lName = null;
				switch (this.current) {
				case LexicalUnits.DASH:
					lName = "";
					break;
				case LexicalUnits.IDENTIFIER:
					lName = this.scanner.getStringValue();
				case LexicalUnits.ANY:
					this.nextIgnoreSpaces();
					break;
				default:
					throw this.createCSSParseException("dash.or.identifier.or.any");
				}
				switch (this.current) {
				case LexicalUnits.DASH:
					prefix = lName;
					if (this.nextIgnoreSpaces() != LexicalUnits.IDENTIFIER) {
						throw this.createCSSParseException("identifier");
					}
					lName = this.scanner.getStringValue();
					this.nextIgnoreSpaces();
				}
				if (lName == null) {
					throw this.createCSSParseException("identifier");
				}
				String uri = ((prefix == null) ? null : (String) this.prefixToURI.get(prefix));

				int op = this.current;
				switch (op) {
				default:
					throw this.createCSSParseException("right.bracket");
				case LexicalUnits.RIGHT_BRACKET:
					this.nextIgnoreSpaces();
					c = this.conditionFactory.createAttributeCondition(lName, uri, false, null);
					break;
				case LexicalUnits.EQUAL:
				case LexicalUnits.INCLUDES:
				case LexicalUnits.DASHMATCH:
					String val = null;
					switch (nextIgnoreSpaces()) {
					default:
						throw this.createCSSParseException("identifier.or.string");
					case LexicalUnits.STRING:
					case LexicalUnits.IDENTIFIER:
						val = this.scanner.getStringValue();
						nextIgnoreSpaces();
					}
					if (this.current != LexicalUnits.RIGHT_BRACKET) {
						throw this.createCSSParseException("right.bracket");
					}
					this.next();
					switch (op) {
					case LexicalUnits.EQUAL:
						c = this.conditionFactory.createAttributeCondition(lName, uri, true, val);
						break;
					case LexicalUnits.INCLUDES:
						c = this.conditionFactory.createOneOfAttributeCondition(lName, uri, false, val);
						break;
					default:
						c = this.conditionFactory.createBeginHyphenAttributeCondition(lName, uri, false, val);
					}
				}
				break;
			case LexicalUnits.COLON:
				switch (nextIgnoreSpaces()) {
				case LexicalUnits.IDENTIFIER:
					String val = this.scanner.getStringValue();
					if (isPseudoElement(val)) {
						if (this.pseudoElement != null) {
							throw this.createCSSParseException("duplicate.pseudo.element");
						}
						this.pseudoElement = val;
						this.next();
						break loop;
					} else {
						c = this.conditionFactory.createPseudoClassCondition(null, val);
					}
					this.next();
					break;
				case LexicalUnits.FUNCTION:
					String func = this.scanner.getStringValue();
					if (nextIgnoreSpaces() != LexicalUnits.IDENTIFIER) {
						throw this.createCSSParseException("identifier");
					}
					String lang = this.scanner.getStringValue();
					if (nextIgnoreSpaces() != LexicalUnits.RIGHT_BRACE) {
						throw this.createCSSParseException("right.brace");
					}

					if (!func.equalsIgnoreCase("lang")) {
						throw this.createCSSParseException("pseudo.function");
					}

					c = this.conditionFactory.createLangCondition(lang);

					this.next();
					break;
				default:
					throw this.createCSSParseException("identifier");
				}
				break;
			default:
				break loop;
			}
			if (c != null) {
				if (cond == null) {
					cond = c;
				} else {
					cond = this.conditionFactory.createAndCondition(cond, c);
				}
			}
		}
		this.skipSpaces();
		if (cond != null) {
			result = this.selectorFactory.createConditionalSelector(result, cond);
		}
		return result;
	}

	/**
	 * Tells whether or not the given string represents a pseudo-element.
	 */
	protected boolean isPseudoElement(String s) {
		switch (s.charAt(0)) {
		case 'a':
		case 'A':
			return s.equalsIgnoreCase("after");
		case 'b':
		case 'B':
			return s.equalsIgnoreCase("before");
		case 'f':
		case 'F':
			return s.equalsIgnoreCase("first-letter") || s.equalsIgnoreCase("first-line");
		}
		return false;
	}

	/**
	 * Parses the given reader.
	 */
	protected void parseStyleDeclaration(boolean inSheet) throws IOException {
		for (;;) {
			switch (this.current) {
			case LexicalUnits.EOF:
				if (inSheet) {
					throw this.createCSSParseException("eof");
				}
				return;
			case LexicalUnits.RIGHT_CURLY_BRACE:
				if (!inSheet) {
					throw this.createCSSParseException("eof.expected");
				}
				this.nextIgnoreSpaces();
				return;
			case LexicalUnits.SEMI_COLON:
				this.nextIgnoreSpaces();
				continue;
			case LexicalUnits.PAGE_CONTENT_SYMBOL:
				this.nextIgnoreSpaces();
				this.parsePageContentRule();
				break;
			default:
				this.reportError(this.createCSSParseException("identifier"));
				this.skipSemiColon();
				continue;
			case LexicalUnits.IDENTIFIER:
			}

			String name = this.scanner.getStringValue();
			if (this.nextIgnoreSpaces() != LexicalUnits.COLON) {
				this.reportError(this.createCSSParseException("colon"));
				this.skipSemiColon();
				continue;
			}
			this.nextIgnoreSpaces();

			LexicalUnit exp;
			try {
				exp = this.parseExpression(false);
			} catch (CSSParseException e) {
				this.reportError(e);
				this.skipSemiColon();
				continue;
			}
			boolean important = false;
			if (this.current == LexicalUnits.IMPORTANT_SYMBOL) {
				important = true;
				this.nextIgnoreSpaces();
			}
			this.documentHandler.property(name, exp, important);
		}
	}

	/**
	 * Parses a CSS2 expression.
	 * 
	 * @param param
	 *            The type of the current lexical unit.
	 */
	protected LexicalUnit parseExpression(boolean param) throws IOException {
		LexicalUnit result = parseTerm(null);
		LexicalUnit curr = result;

		for (;;) {
			boolean op = false;
			switch (this.current) {
			case LexicalUnits.COMMA:
				op = true;
				curr = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_COMMA, curr);
				this.nextIgnoreSpaces();
				break;
			case LexicalUnits.DIVIDE:
				op = true;
				curr = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_SLASH, curr);
				this.nextIgnoreSpaces();
			}
			if (param) {
				if (this.current == LexicalUnits.RIGHT_BRACE) {
					if (op) {
						throw this.createCSSParseException("token", new Object[] { new Integer(current) });
					}
					return result;
				}
				curr = parseTerm(curr);
			} else {
				switch (this.current) {
				case LexicalUnits.IMPORTANT_SYMBOL:
				case LexicalUnits.SEMI_COLON:
				case LexicalUnits.RIGHT_CURLY_BRACE:
				case LexicalUnits.EOF:
					if (op) {
						throw this.createCSSParseException("token", new Object[] { new Integer(current) });
					}
					return result;
				default:
					curr = parseTerm(curr);
				}
			}
		}
	}

	/**
	 * Parses a CSS2 term.
	 */
	protected LexicalUnit parseTerm(LexicalUnit prev) throws IOException {
		boolean plus = true;
		boolean sgn = false;

		switch (this.current) {
		case LexicalUnits.MINUS:
			plus = false;
		case LexicalUnits.PLUS:
			this.next();
			sgn = true;
		default:
			switch (current) {
			case LexicalUnits.INTEGER:
				String sval = this.scanner.getStringValue();
				if (!plus)
					sval = "-" + sval;
				try {
					int val = Integer.parseInt(sval);
					this.nextIgnoreSpaces();
					return CSSLexicalUnit.createInteger(val, prev);
				} catch (NumberFormatException e) {
					throw createCSSParseException("token", new Object[] { new Integer(current) });
				}
			case LexicalUnits.REAL:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_REAL, number(plus), prev);
			case LexicalUnits.PERCENTAGE:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_PERCENTAGE, number(plus), prev);
			case LexicalUnits.PT:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_POINT, number(plus), prev);
			case LexicalUnits.PC:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_PICA, number(plus), prev);
			case LexicalUnits.PX:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_PIXEL, number(plus), prev);
			case LexicalUnits.CM:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_CENTIMETER, number(plus), prev);
			case LexicalUnits.MM:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_MILLIMETER, number(plus), prev);
			case LexicalUnits.IN:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_INCH, number(plus), prev);
			case LexicalUnits.EM:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_EM, number(plus), prev);
			case LexicalUnits.EX:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_EX, number(plus), prev);
			case LexicalUnits.DEG:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_DEGREE, number(plus), prev);
			case LexicalUnits.RAD:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_RADIAN, number(plus), prev);
			case LexicalUnits.GRAD:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_GRADIAN, number(plus), prev);
			case LexicalUnits.S:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_SECOND, number(plus), prev);
			case LexicalUnits.MS:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_MILLISECOND, number(plus), prev);
			case LexicalUnits.HZ:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_HERTZ, number(plus), prev);
			case LexicalUnits.KHZ:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_KILOHERTZ, number(plus), prev);
			case LexicalUnits.REM:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_REM, number(plus), prev);
			case LexicalUnits.CH:
				return CSSLexicalUnit.createFloat(LexicalUnit.SAC_CH, number(plus), prev);
			case LexicalUnits.DIMENSION:
				return this.dimension(plus, prev);
			case LexicalUnits.FUNCTION:
				return this.parseFunction(prev);
			}
			if (sgn) {
				throw this.createCSSParseException("token", new Object[] { new Integer(current) });
			}
		}
		switch (this.current) {
		case LexicalUnits.STRING:
			String val = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			return CSSLexicalUnit.createString(LexicalUnit.SAC_STRING_VALUE, val, prev);
		case LexicalUnits.UNICODE_RANGE:
			val = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			return CSSLexicalUnit.createString(LexicalUnit.SAC_UNICODERANGE, val, prev);
		case LexicalUnits.IDENTIFIER:
			val = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			if (val.equalsIgnoreCase("inherit")) {
				return CSSLexicalUnit.createSimple(LexicalUnit.SAC_INHERIT, prev);
			} else {
				return CSSLexicalUnit.createString(LexicalUnit.SAC_IDENT, val, prev);
			}
		case LexicalUnits.URI:
			val = this.scanner.getStringValue();
			this.nextIgnoreSpaces();
			return CSSLexicalUnit.createString(LexicalUnit.SAC_URI, val, prev);
		case LexicalUnits.HASH:
			return this.hexcolor(prev);
		default:
			throw this.createCSSParseException("token", new Object[] { new Integer(current) });
		}
	}

	/**
	 * Parses a CSS2 function.
	 */
	protected LexicalUnit parseFunction(LexicalUnit prev) throws IOException {
		String name = this.scanner.getStringValue();
		this.nextIgnoreSpaces();

		LexicalUnit params = this.parseExpression(true);

		if (this.current != LexicalUnits.RIGHT_BRACE) {
			throw this.createCSSParseException("token", new Object[] { new Integer(this.current) });
		}
		this.nextIgnoreSpaces();

		predefined: switch (name.charAt(0)) {
		case 'r':
		case 'R':
			LexicalUnit lu;
			if (name.equalsIgnoreCase("rgb")) {
				lu = params;
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu != null) {
					break;
				}
				return CSSLexicalUnit.createPredefinedFunction(LexicalUnit.SAC_RGBCOLOR, params, prev);
			} else if (name.equalsIgnoreCase("rect")) {
				lu = params;
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
					if (lu.getIntegerValue() != 0) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_IDENT:
					if (!lu.getStringValue().equalsIgnoreCase("auto")) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_EM:
				case LexicalUnit.SAC_EX:
				case LexicalUnit.SAC_PIXEL:
				case LexicalUnit.SAC_CENTIMETER:
				case LexicalUnit.SAC_MILLIMETER:
				case LexicalUnit.SAC_INCH:
				case LexicalUnit.SAC_POINT:
				case LexicalUnit.SAC_PICA:
				case LexicalUnit.SAC_PERCENTAGE:
				case LexicalUnit.SAC_REM:
				case LexicalUnit.SAC_CH:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
					if (lu.getIntegerValue() != 0) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_IDENT:
					if (!lu.getStringValue().equalsIgnoreCase("auto")) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_EM:
				case LexicalUnit.SAC_EX:
				case LexicalUnit.SAC_PIXEL:
				case LexicalUnit.SAC_CENTIMETER:
				case LexicalUnit.SAC_MILLIMETER:
				case LexicalUnit.SAC_INCH:
				case LexicalUnit.SAC_POINT:
				case LexicalUnit.SAC_PICA:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
					if (lu.getIntegerValue() != 0) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_IDENT:
					if (!lu.getStringValue().equalsIgnoreCase("auto")) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_EM:
				case LexicalUnit.SAC_EX:
				case LexicalUnit.SAC_PIXEL:
				case LexicalUnit.SAC_CENTIMETER:
				case LexicalUnit.SAC_MILLIMETER:
				case LexicalUnit.SAC_INCH:
				case LexicalUnit.SAC_POINT:
				case LexicalUnit.SAC_PICA:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_INTEGER:
					if (lu.getIntegerValue() != 0) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_IDENT:
					if (!lu.getStringValue().equalsIgnoreCase("auto")) {
						break predefined;
					}
					lu = lu.getNextLexicalUnit();
					break;
				case LexicalUnit.SAC_EM:
				case LexicalUnit.SAC_EX:
				case LexicalUnit.SAC_PIXEL:
				case LexicalUnit.SAC_CENTIMETER:
				case LexicalUnit.SAC_MILLIMETER:
				case LexicalUnit.SAC_INCH:
				case LexicalUnit.SAC_POINT:
				case LexicalUnit.SAC_PICA:
				case LexicalUnit.SAC_PERCENTAGE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu != null) {
					break;
				}
				return CSSLexicalUnit.createPredefinedFunction(LexicalUnit.SAC_RECT_FUNCTION, params, prev);
			}
			break;
		case 'c':
		case 'C':
			if (name.equalsIgnoreCase("counter")) {
				lu = params;
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_IDENT:
					lu = lu.getNextLexicalUnit();
				}
				if (lu != null) {
					switch (lu.getLexicalUnitType()) {
					default:
						break predefined;
					case LexicalUnit.SAC_OPERATOR_COMMA:
						lu = lu.getNextLexicalUnit();
					}
					if (lu == null) {
						break;
					}
					switch (lu.getLexicalUnitType()) {
					default:
						break predefined;
					case LexicalUnit.SAC_IDENT:
						lu = lu.getNextLexicalUnit();
					}
					if (lu != null) {
						break;
					}
				}
				return CSSLexicalUnit.createPredefinedFunction(LexicalUnit.SAC_COUNTER_FUNCTION, params, prev);
			} else if (name.equalsIgnoreCase("counters")) {
				lu = params;
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_IDENT:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_OPERATOR_COMMA:
					lu = lu.getNextLexicalUnit();
				}
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_STRING_VALUE:
					lu = lu.getNextLexicalUnit();
				}
				if (lu != null) {
					switch (lu.getLexicalUnitType()) {
					default:
						break predefined;
					case LexicalUnit.SAC_OPERATOR_COMMA:
						lu = lu.getNextLexicalUnit();
					}
					if (lu == null) {
						break;
					}
					switch (lu.getLexicalUnitType()) {
					default:
						break predefined;
					case LexicalUnit.SAC_IDENT:
						lu = lu.getNextLexicalUnit();
					}
					if (lu != null) {
						break;
					}
				}
				return CSSLexicalUnit.createPredefinedFunction(LexicalUnit.SAC_COUNTERS_FUNCTION, params, prev);
			}
			break;
		case 'a':
		case 'A':
			if (name.equalsIgnoreCase("attr")) {
				lu = params;
				if (lu == null) {
					break;
				}
				switch (lu.getLexicalUnitType()) {
				default:
					break predefined;
				case LexicalUnit.SAC_IDENT:
					lu = lu.getNextLexicalUnit();
				}
				if (lu != null) {
					break;
				}
				return CSSLexicalUnit.createString(LexicalUnit.SAC_ATTR, params.getStringValue(), prev);
			}
		}

		return CSSLexicalUnit.createFunction(name, params, prev);
	}

	/**
	 * Converts a hash unit to a RGB color.
	 */
	protected LexicalUnit hexcolor(LexicalUnit prev) throws IOException {
		String val = this.scanner.getStringValue();
		int len = val.length();
		LexicalUnit params = null;
		switch (len) {
		case 3:
			char rc = Character.toLowerCase(val.charAt(0));
			char gc = Character.toLowerCase(val.charAt(1));
			char bc = Character.toLowerCase(val.charAt(2));
			if (!ScannerUtilities.isCSSHexadecimalCharacter(rc) || !ScannerUtilities.isCSSHexadecimalCharacter(gc)
					|| !ScannerUtilities.isCSSHexadecimalCharacter(bc)) {
				throw createCSSParseException("rgb.color", new Object[] { val });
			}
			int t;
			int r = t = (rc >= '0' && rc <= '9') ? rc - '0' : rc - 'a' + 10;
			t <<= 4;
			r |= t;
			int g = t = (gc >= '0' && gc <= '9') ? gc - '0' : gc - 'a' + 10;
			t <<= 4;
			g |= t;
			int b = t = (bc >= '0' && bc <= '9') ? bc - '0' : bc - 'a' + 10;
			t <<= 4;
			b |= t;
			params = CSSLexicalUnit.createInteger(r, null);
			LexicalUnit tmp;
			tmp = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_COMMA, params);
			tmp = CSSLexicalUnit.createInteger(g, tmp);
			tmp = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_COMMA, tmp);
			tmp = CSSLexicalUnit.createInteger(b, tmp);
			break;
		case 6:
			char rc1 = Character.toLowerCase(val.charAt(0));
			char rc2 = Character.toLowerCase(val.charAt(1));
			char gc1 = Character.toLowerCase(val.charAt(2));
			char gc2 = Character.toLowerCase(val.charAt(3));
			char bc1 = Character.toLowerCase(val.charAt(4));
			char bc2 = Character.toLowerCase(val.charAt(5));
			if (!ScannerUtilities.isCSSHexadecimalCharacter(rc1) || !ScannerUtilities.isCSSHexadecimalCharacter(rc2)
					|| !ScannerUtilities.isCSSHexadecimalCharacter(gc1)
					|| !ScannerUtilities.isCSSHexadecimalCharacter(gc2)
					|| !ScannerUtilities.isCSSHexadecimalCharacter(bc1)
					|| !ScannerUtilities.isCSSHexadecimalCharacter(bc2)) {
				throw createCSSParseException("rgb.color");
			}
			r = (rc1 >= '0' && rc1 <= '9') ? rc1 - '0' : rc1 - 'a' + 10;
			r <<= 4;
			r |= (rc2 >= '0' && rc2 <= '9') ? rc2 - '0' : rc2 - 'a' + 10;
			g = (gc1 >= '0' && gc1 <= '9') ? gc1 - '0' : gc1 - 'a' + 10;
			g <<= 4;
			g |= (gc2 >= '0' && gc2 <= '9') ? gc2 - '0' : gc2 - 'a' + 10;
			b = (bc1 >= '0' && bc1 <= '9') ? bc1 - '0' : bc1 - 'a' + 10;
			b <<= 4;
			b |= (bc2 >= '0' && bc2 <= '9') ? bc2 - '0' : bc2 - 'a' + 10;
			params = CSSLexicalUnit.createInteger(r, null);
			tmp = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_COMMA, params);
			tmp = CSSLexicalUnit.createInteger(g, tmp);
			tmp = CSSLexicalUnit.createSimple(LexicalUnit.SAC_OPERATOR_COMMA, tmp);
			tmp = CSSLexicalUnit.createInteger(b, tmp);
			break;
		default:
			throw createCSSParseException("rgb.color", new Object[] { val });
		}
		nextIgnoreSpaces();
		return CSSLexicalUnit.createPredefinedFunction(LexicalUnit.SAC_RGBCOLOR, params, prev);
	}

	/**
	 * Creates a scanner, given an InputSource.
	 */
	protected Scanner createScanner(InputSource source) throws IOException {
		this.documentURI = source.getURI();
		if (this.documentURI == null) {
			this.documentURI = "";
		}

		Reader r = source.getCharacterStream();
		if (r != null) {
			return new Scanner(r);
		}
		String encoding = source.getEncoding();
		if (encoding == null) {
			encoding = this.defaultCharset;
		}

		InputStream is = source.getByteStream();
		if (is != null) {
			return new Scanner(new InputStreamReader(is, encoding));
		}

		String uri = source.getURI();
		if (uri == null) {
			throw new CSSException(formatMessage("empty.source", null));
		}

		URL url = new URL(uri);
		is = url.openStream();
		return new Scanner(new InputStreamReader(is, encoding));
	}

	/**
	 * Skips the white spaces.
	 */
	protected int skipSpaces() throws IOException {
		int lex = this.scanner.getType();
		while (lex == LexicalUnits.SPACE) {
			lex = next();
		}
		return lex;
	}

	/**
	 * Skips the white spaces and CDO/CDC units.
	 */
	protected int skipSpacesAndCDOCDC() throws IOException {
		loop: for (;;) {
			switch (this.current) {
			default:
				break loop;
			case LexicalUnits.COMMENT:
			case LexicalUnits.SPACE:
			case LexicalUnits.CDO:
			case LexicalUnits.CDC:
			}
			this.scanner.clearBuffer();
			next();
		}
		return this.current;
	}

	/**
	 * Converts the current lexical unit to a float.
	 */
	protected float number(boolean positive) throws IOException {
		try {
			float sgn = (positive) ? 1 : -1;
			String val = this.scanner.getStringValue();
			nextIgnoreSpaces();
			return sgn * Float.parseFloat(val);
		} catch (NumberFormatException e) {
			throw createCSSParseException("number.format");
		}
	}

	/**
	 * Converts the current lexical unit to a dimension.
	 */
	protected LexicalUnit dimension(boolean positive, LexicalUnit prev) throws IOException {
		try {
			float sgn = (positive) ? 1 : -1;
			String val = this.scanner.getStringValue();
			int i;
			loop: for (i = 0; i < val.length(); i++) {
				switch (val.charAt(i)) {
				default:
					break loop;
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
				case '.':
				}
			}
			this.nextIgnoreSpaces();
			return CSSLexicalUnit.createDimension(sgn * Float.parseFloat(val.substring(0, i)), val.substring(i), prev);
		} catch (NumberFormatException e) {
			throw this.createCSSParseException("number.format");
		}
	}

	/**
	 * Advances to the next token, ignoring comments.
	 */
	protected int next() throws IOException {
		try {
			for (;;) {
				this.scanner.clearBuffer();
				this.current = this.scanner.next();
				if (this.current == LexicalUnits.COMMENT) {
					this.documentHandler.comment(this.scanner.getStringValue());
				} else {
					break;
				}
			}
			return this.current;
		} catch (ParseException e) {
			this.reportError(e.getMessage());
			return this.current;
		}
	}

	/**
	 * Advances to the next token and skip the spaces, ignoring comments.
	 */
	protected int nextIgnoreSpaces() throws IOException {
		try {
			loop: for (;;) {
				this.scanner.clearBuffer();
				this.current = scanner.next();
				switch (this.current) {
				case LexicalUnits.COMMENT:
					this.documentHandler.comment(this.scanner.getStringValue());
					break;
				default:
					break loop;
				case LexicalUnits.SPACE:
				}
			}
		} catch (ParseException e) {
			this.reportError(e.getMessage());
		}
		return this.current;
	}

	/**
	 * Reports a parsing error.
	 */
	protected void reportError(String key) throws IOException {
		this.reportError(key, null);
	}

	/**
	 * Reports a parsing error.
	 */
	protected void reportError(String key, Object[] params) throws IOException {
		this.reportError(this.createCSSParseException(key, params));
	}

	/**
	 * Reports a parsing error.
	 */
	protected void reportError(CSSParseException e) throws IOException {
		this.errorHandler.error(e);
	}

	/**
	 * Creates a parse exception.
	 */
	protected CSSParseException createCSSParseException(String key) {
		return this.createCSSParseException(key, null);
	}

	/**
	 * Creates a parse exception.
	 */
	protected CSSParseException createCSSParseException(String key, Object[] params) {
		return new CSSParseException(formatMessage(key, params), this.documentURI, this.scanner.getLine(),
				this.scanner.getColumn());
	}

	// -----------------------------------------------------------------------
	// Extended methods
	// -----------------------------------------------------------------------

	/**
	 * Implements {@link ExtendedParser#parseStyleDeclaration(String)}.
	 */
	public void parseStyleDeclaration(String source) throws CSSException, IOException {
		this.scanner = new Scanner(source);
		this.parseStyleDeclarationInternal();
	}

	/**
	 * Implements {@link ExtendedParser#parseRule(String)}.
	 */
	public void parseRule(String source) throws CSSException, IOException {
		this.scanner = new Scanner(source);
		this.parseRuleInternal();
	}

	/**
	 * Implements {@link ExtendedParser#parseSelectors(String)}.
	 */
	public SelectorList parseSelectors(String source) throws CSSException, IOException {
		this.scanner = new Scanner(source);
		return this.parseSelectorsInternal();
	}

	/**
	 * Implements {@link ExtendedParser#parsePropertyValue(String)}.
	 */
	public LexicalUnit parsePropertyValue(String source) throws CSSException, IOException {
		this.scanner = new Scanner(source);
		return this.parsePropertyValueInternal();
	}

	/**
	 * Implements {@link ExtendedParser#parsePriority(String)}.
	 */
	public boolean parsePriority(String source) throws CSSException, IOException {
		this.scanner = new Scanner(source);
		return this.parsePriorityInternal();
	}

	/**
	 * Implements {@link ExtendedParser#parseMedia(String)}.
	 */
	public SACMediaList parseMedia(String mediaText) throws CSSException, IOException {
		CSSSACMediaList result = new CSSSACMediaList();
		if (!"all".equalsIgnoreCase(mediaText)) {
			StringTokenizer st = new StringTokenizer(mediaText, " ,");
			while (st.hasMoreTokens()) {
				result.append(st.nextToken());
			}
		}
		return result;
	}
}