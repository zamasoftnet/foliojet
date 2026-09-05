package jp.cssj.test.unit.ioprops;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.PaintOrderValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code paint-order}と{@code vertical-align:central}の宣言解析です。 */
public class PaintOrderAndCentralValueTest extends TestCase {
	private final List<String> warnings = new ArrayList<>();

	public void testPaintOrderGrammar() {
		assertSame(PaintOrderValue.NORMAL, this.single("paint-order", "normal"));
		assertTrue(((PaintOrderValue) this.single("paint-order", "stroke fill")).isStrokeBeforeFill());
		assertTrue("未指定のfillは末尾へ補う",
				((PaintOrderValue) this.single("paint-order", "stroke")).isStrokeBeforeFill());
		assertFalse(((PaintOrderValue) this.single("paint-order", "fill stroke markers")).isStrokeBeforeFill());
		this.invalid("paint-order", "normal stroke");
		this.invalid("paint-order", "stroke stroke");
	}

	public void testVerticalAlignCentralMapsToMiddle() {
		assertSame(VerticalAlignValue.MIDDLE_VALUE, this.single("vertical-align", "central"));
	}

	private Value single(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ":" + value),
				this.ua(), null, false);
		assertNotNull(name + ":" + value + "が無効: " + this.warnings, property);
		assertTrue("警告が出ています: " + this.warnings, this.warnings.isEmpty());
		final CompositeProperty.Entry[] entries = ((CompositeProperty) property).getEntries();
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private void invalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ":" + value),
				this.ua(), null, false);
		assertTrue(name + ":" + value + "が受理された", property == null || !this.warnings.isEmpty());
	}

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { UserAgent.class },
				(proxy, method, args) -> switch (method.getName()) {
				case "getPixelsPerInch" -> 96.0;
				case "getFontSize" -> 12.0;
				case "getDocumentContext" -> new DocumentContext();
				case "getProperty" -> null;
				case "message" -> {
					this.warnings.add(String.valueOf(args[0]));
					yield null;
				}
				case "toString" -> "PaintOrderAndCentralValueTest.UserAgent";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new UnsupportedOperationException(method.toString());
				});
	}

	private static List<CssToken> tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(declarations);
		final List<CSSDeclaration> all = declarations.getAllDeclarations();
		assertEquals(1, all.size());
		return Tokens.fromExpression(all.get(0).getExpression());
	}
}
