package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.CSSStyleSheet;
import net.zamasoft.foliojet.css.CSSStyleSheetBuilder;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.MarginBoxName;
import net.zamasoft.foliojet.css.PageRule;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJPageContentClear;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.RunningPositionValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;
import net.zamasoft.foliojet.ua.UserAgent;

/** running R1a の値型・宣言文脈・legacy 分解・頁選択を検証します。 */
public class RunningValueParseTest extends TestCase {
	private static final URI URI_BASE = URI.create("file:///running-r1a-parse.css");

	private final List<Short> warnings = new ArrayList<Short>();

	private final UserAgent ua = (UserAgent) Proxy.newProxyInstance(UserAgent.class.getClassLoader(),
			new Class<?>[] { UserAgent.class }, (proxy, method, args) -> {
				if ("message".equals(method.getName())) {
					this.warnings.add((Short) args[0]);
					System.err.println("[running R1a] parser message=" + Arrays.deepToString(args));
					return null;
				}
				throw new AssertionError("解析試験で予期しない UA 呼び出し: " + method);
			});

	public void testRunningPositionRetainsNameAndIsCapturedOutsideFlow() throws Exception {
		final CSSStyle style = this.style("position:running(hdr)");
		assertEquals(new RunningPositionValue("hdr"), style.get(CSSPosition.INFO));
		assertEquals(PositionValue.STATIC, CSSPosition.get(style));
		assertEquals(new RunningPositionValue("Header"),
				this.style("position:running(Header)").get(CSSPosition.INFO));
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());

		final String body = "<div id='hdr'>HEADER<span>CHILD</span></div><div>BODY</div>";
		final StringSetModeMatrixTest.Conversion result = StringSetModeMatrixTest.convert(
				"#hdr{position:running(hdr)}", body);
		final StringSetModeMatrixTest.Conversion control = StringSetModeMatrixTest.convert("", "<div>BODY</div>");
		assertEquals(control.pages(), result.pages());
		assertFalse(result.messages().toString(), result.messages().stream()
				.anyMatch(message -> message.detail().contains("position") || message.detail().contains("running")));
	}

	public void testElementValueInMarginBox() throws Exception {
		final StyleContext context = this.sheet("@page{@top-center{content:element(hdr,last)}}");
		final Map<MarginBoxName, Declaration> boxes = context.pageMarginBoxes(CSSElement.PAGE_SINGLE_FIRST);
		assertNotNull(boxes.get(MarginBoxName.TOP_CENTER));
		final CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.BEFORE);
		boxes.get(MarginBoxName.TOP_CENTER).applyProperties(style);
		final Value[] content = Content.get(style);
		assertEquals(1, content.length);
		assertEquals(new ElementFunctionValue("hdr", Mode.LAST), content[0]);
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());

		final StringSetModeMatrixTest.Conversion result = StringSetModeMatrixTest.convert(
				"@page{@top-center{content:element(hdr,last)}}", "<div>BODY</div>");
		assertEquals(List.of("BODY"), result.pages());
		assertFalse(result.messages().toString(), result.messages().stream()
				.anyMatch(message -> message.detail().contains("element")));
	}

	public void testElementOutsideMarginBoxWarnsAndDoesNotThrow() throws Exception {
		final StringSetModeMatrixTest.Conversion result = StringSetModeMatrixTest.convert(
				"#x::before{content:element(hdr,last)}#x{content:element(hdr)}", "<div id='x'>BODY</div>");
		assertTrue(result.pdfBytes() > 0);
		assertEquals(List.of("BODY"), result.pages());
		assertTrue(result.messages().toString(), result.messages().stream()
				.anyMatch(message -> message.code() == MessageCodes.WARN_BAD_CSS_SYNTAX
						&& message.detail().contains("element()")));
	}

	public void testFunctionModesAndInvalidArguments() {
		final String[] modes = { "first", "start", "last", "first-except" };
		final Mode[] expected = { Mode.FIRST, Mode.START, Mode.LAST, Mode.FIRST_EXCEPT };
		for (int i = 0; i < modes.length; ++i) {
			assertEquals(new ElementFunctionValue("Header", expected[i]),
					Content.get(this.style("content:element(Header," + modes[i] + ")"))[0]);
			final StringFunctionValue string = (StringFunctionValue)
					Content.get(this.style("content:string(Header," + modes[i] + ")"))[0];
			assertEquals(expected[i], string.getMode());
			assertEquals("Header", string.getName());
		}
		assertEquals(new ElementFunctionValue("hdr", Mode.FIRST),
				Content.get(this.style("content:element(hdr)"))[0]);
		for (final String invalid : new String[] {
				"position:running('hdr')", "position:running(hdr,last)", "position:running(hdr) static",
				"position:running(none)", "position:running(default)", "position:running(revert)",
				"content:element('hdr')", "content:element(hdr,last,first)", "content:element(hdr last)",
				"content:element(default)", "content:element(hdr,bogus)",
				"content:'prefix' element(hdr)", "content:element(hdr) 'suffix'", "content:element(hdr)/'alt'" }) {
			assertNull(invalid, this.parse(invalid));
		}
	}

	/** 疑似要素の content: element() は警告して疑似要素ごと作らない(箱も副作用も残さない)。 */
	public void testElementInPseudoElementMakesNoBox() throws Exception {
		final String body = "<div id='x'>BODY</div>";
		final StringSetModeMatrixTest.Conversion result = StringSetModeMatrixTest.convert(
				"#x::before{content:element(hdr);display:block;height:100pt;background:red}", body);
		final StringSetModeMatrixTest.Conversion control = StringSetModeMatrixTest.convert("", body);
		assertEquals(control.pages(), result.pages());
		assertTrue(result.messages().toString(), result.messages().stream()
				.anyMatch(message -> message.code() == MessageCodes.WARN_BAD_CSS_SYNTAX
						&& message.detail().contains("element()")));
	}

	/** 裸の none は無効化、引用した 'none' は名前(3.2 と同じ)。 */
	public void testLegacyBareNoneDisables() {
		assertNull(CSSJPageContent.getName(this.style("-cssj-page-content:none")));
		assertNull(CSSJPageContent.getName(this.style("-cssj-page-content:NONE")));
		assertEquals("none", CSSJPageContent.getName(this.style("-cssj-page-content:'none'")));
		assertNull(CSSJPageContent.getName(this.style("-cssj-regeneratable:none")));
		final CSSStyle style = this.style("-cssj-page-content:old left");
		this.parse("-cssj-page-content:none").applyProperty(style);
		assertNull(CSSJPageContent.getName(style));
		assertEquals(0, CSSJPageContent.getPages(style));
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());
		// none に頁条件は付けられない(不正値の警告が出る)
		assertNull(this.parse("-cssj-page-content:none left"));
	}

	public void testLegacyPageContentDecomposition() {
		final CompositeProperty property = (CompositeProperty) this.parse(
				"-cssj-page-content:'header-left' left single");
		assertNotNull(property);
		assertEquals(2, property.getEntries().length);
		assertSame(CSSJPageContent.INFO_NAME, property.getEntries()[0].getPrimitivePropertyInfo());
		assertSame(CSSJPageContent.INFO_PAGES, property.getEntries()[1].getPrimitivePropertyInfo());
		assertEquals("-cssj-page-content-name", CSSJPageContent.INFO_NAME.getName());
		assertEquals("-cssj-page-content-pages", CSSJPageContent.INFO_PAGES.getName());
		final CSSStyle quoted = CSSStyle.getCSSStyle(this.ua, null, CSSElement.ANON);
		property.applyProperty(quoted);
		assertEquals("header-left", CSSJPageContent.getName(quoted));
		assertEquals(PageRule.PSEUDO_LEFT | PageRule.PSEUDO_SINGLE, CSSJPageContent.getPages(quoted));
		final CSSStyle bare = this.style("-cssj-page-content:nombre-left left");
		assertEquals("nombre-left", CSSJPageContent.getName(bare));
		assertEquals(PageRule.PSEUDO_LEFT, CSSJPageContent.getPages(bare));
		final CSSStyle all = this.style("-cssj-page-content:Header");
		assertEquals("Header", CSSJPageContent.getName(all));
		assertEquals(0, CSSJPageContent.getPages(all));
		System.err.println("[running R1a] legacy entries=" + Arrays.toString(property.getEntries()));
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());
	}

	public void testClearNamesAndRegeneratableAlias() {
		assertEquals(List.of("a", "b"),
				Arrays.asList(CSSJPageContentClear.get(this.style("-cssj-page-content-clear:a b"))));
		assertEquals(List.of("a", "b"),
				Arrays.asList(CSSJPageContentClear.get(this.style("-cssj-page-content-clear:'a' \"b\""))));
		final CSSStyle style = this.style("-cssj-page-content:old left single");
		this.parse("-cssj-regeneratable:x").applyProperty(style);
		assertEquals("x", CSSJPageContent.getName(style));
		assertEquals(0, CSSJPageContent.getPages(style));
		this.parse("-cssj-regeneratable:initial").applyProperty(style);
		assertNull(CSSJPageContent.getName(style));
		assertEquals(0, CSSJPageContent.getPages(style));
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());
		assertNull(this.parse("-cssj-regeneratable:x left"));
		assertNull(this.parse("-cssj-page-content:x bogus"));
	}

	public void testSinglePageParsingMatchingAndSpecificity() throws Exception {
		final StyleContext context = this.sheet(
				"@page:first{@top-center{content:'FIRST'}}"
				+ "@page:left{@top-center{content:'LEFT'}}"
				+ "@page:right{@top-center{content:'RIGHT'}}"
				+ "@page:single{@top-center{content:'SINGLE'}}"
				+ "@page{@top-center{content:'BASE'}}"
				+ "@page:single:first{@bottom-center{content:'SINGLEFIRST'}}");
		assertEquals("FIRST", this.pageText(context, CSSElement.PAGE_SINGLE_FIRST, MarginBoxName.TOP_CENTER));
		assertEquals("SINGLE", this.pageText(context, CSSElement.PAGE_SINGLE, MarginBoxName.TOP_CENTER));
		assertEquals("FIRST", this.pageText(context, CSSElement.PAGE_FIRST_RIGHT, MarginBoxName.TOP_CENTER));
		assertEquals("FIRST", this.pageText(context, CSSElement.PAGE_FIRST_LEFT, MarginBoxName.TOP_CENTER));
		for (final CSSElement page : new CSSElement[] { CSSElement.PAGE_LEFT_EVEN, CSSElement.PAGE_LEFT_ODD }) {
			assertEquals("LEFT", this.pageText(context, page, MarginBoxName.TOP_CENTER));
		}
		for (final CSSElement page : new CSSElement[] { CSSElement.PAGE_RIGHT_EVEN, CSSElement.PAGE_RIGHT_ODD }) {
			assertEquals("RIGHT", this.pageText(context, page, MarginBoxName.TOP_CENTER));
		}
		assertEquals("SINGLEFIRST",
				this.pageText(context, CSSElement.PAGE_SINGLE_FIRST, MarginBoxName.BOTTOM_CENTER));
		assertNull(this.pageText(context, CSSElement.PAGE_SINGLE, MarginBoxName.BOTTOM_CENTER));
		assertNull(this.pageText(context, CSSElement.PAGE_FIRST_RIGHT, MarginBoxName.BOTTOM_CENTER));
		assertTrue(this.warnings.toString(), this.warnings.isEmpty());
	}

	private String pageText(final StyleContext context, final CSSElement page, final MarginBoxName name) {
		final Declaration declaration = context.pageMarginBoxes(page).get(name);
		if (declaration == null) {
			return null;
		}
		final CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.BEFORE);
		declaration.applyProperties(style);
		final String value = ((StringValue) Content.get(style)[0]).getString();
		System.err.println("[running R1a] page=" + page + ", box=" + name + ", text=" + value);
		return value;
	}

	private StyleContext sheet(final String css) throws Exception {
		final CSSStyleSheet sheet = new CSSStyleSheet();
		final CSSStyleSheetBuilder builder = new CSSStyleSheetBuilder(this.ua);
		builder.setCSSStyleSheet(sheet);
		final InputSource source = new InputSource(new StringReader(css));
		source.setURI(URI_BASE.toString());
		builder.parse(source);
		return new StyleContext(sheet, null, null);
	}

	private CSSStyle style(final String declaration) {
		final Property property = this.parse(declaration);
		assertNotNull(declaration, property);
		final CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.ANON);
		property.applyProperty(style);
		return style;
	}

	private Property parse(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(declarations);
		assertEquals(declaration, 1, declarations.getAllDeclarations().size());
		final CSSDeclaration css = declarations.getAllDeclarations().get(0);
		final Property property = ElementPropertySet.getInstance().parseDeclaration(css.getProperty(),
				Tokens.fromExpression(css.getExpression()), this.ua, URI_BASE, css.isImportant());
		System.err.println("[running R1a] " + declaration + " -> " + property);
		return property;
	}
}
