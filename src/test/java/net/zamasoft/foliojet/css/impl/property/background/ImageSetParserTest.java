package net.zamasoft.foliojet.css.impl.property.background;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleImage;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code image-set()}(css-images-4 §4.1、2026-08-29)の候補選択テストです。
 * 出力解像度({@code getPixelsPerInch}。1x=96dpi)を超えない最大の解像度、
 * 無ければ超える中で最小の候補を選ぶこと、{@code -webkit-image-set()}・
 * 文字列URL・{@code type()}・未対応候補の読み飛ばし、そして
 * background/background-image/mask-image/content/list-style-image/
 * list-styleの各入口で同じ選択が働くことを固定する。
 */
public class ImageSetParserTest extends TestCase {

	private static final URI BASE = URI.create("file:///base/css/");

	private final List<String> warnings = new ArrayList<String>();

	private double dpi = 96;

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(ImageSetParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return this.dpi;
					case "getFontSize":
						return 12.0;
					case "getDocumentContext":
						return new DocumentContext();
					case "getProperty":
						return null;
					case "message":
						this.warnings.add(String.valueOf(args[0]) + ":" + java.util.Arrays.toString(args));
						return null;
					case "toString":
						return "ImageSetParserTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	private static List<CssToken> tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		return Tokens.fromExpression(all.get(0).getExpression());
	}

	private Property parseRaw(final String name, final String value) {
		this.warnings.clear();
		return ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value), this.ua(), BASE,
				false);
	}

	private Value entry(final String name, final String value, final PrimitivePropertyInfo info) {
		final Property property = this.parseRaw(name, value);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		for (final Entry e : ((CompositeProperty) property).getEntries()) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail("構成要素が無い: " + info.getName());
		return null;
	}

	private String chosen(final String name, final String value, final PrimitivePropertyInfo info) {
		final Value v = this.entry(name, value, info);
		assertTrue(name + ": " + value + " -> " + v, v instanceof URIValue);
		final String uri = ((URIValue) v).getURI().toString();
		return uri.substring(uri.lastIndexOf('/') + 1);
	}

	private String chosenBackground(final String value) {
		return this.chosen("background-image", value, BackgroundImage.INFO);
	}

	public void testClosestNotExceedingOutputResolution() {
		assertEquals("a.png", this.chosenBackground("image-set(url(a.png) 1x, url(b.png) 2x)"));
		assertEquals("a.png", this.chosenBackground("image-set(url(b.png) 2x, url(a.png) 1x)"));
		// 解像度省略は1x
		assertEquals("a.png", this.chosenBackground("image-set(url(a.png), url(b.png) 2x)"));
		this.dpi = 192;
		assertEquals("b.png", this.chosenBackground("image-set(url(a.png) 1x, url(b.png) 2x)"));
		this.dpi = 144;
		// 1.5xちょうど、超えない最大
		assertEquals("c.png",
				this.chosenBackground("image-set(url(a.png) 1x, url(c.png) 1.5x, url(b.png) 2x)"));
		this.dpi = 120;
		assertEquals("a.png", this.chosenBackground("image-set(url(a.png) 1x, url(c.png) 1.5x)"));
		// 出力解像度を超えない候補が無ければ、超える中で最小
		this.dpi = 96;
		assertEquals("b.png", this.chosenBackground("image-set(url(b.png) 2x, url(c.png) 3x)"));
		// dpi/dppx/dpcm
		assertEquals("a.png", this.chosenBackground("image-set(url(a.png) 96dpi, url(b.png) 2dppx)"));
		assertEquals("b.png", this.chosenBackground("image-set(url(b.png) 30dpcm, url(a.png) 100dpi)"));
	}

	public void testWebkitPrefixStringsAndType() {
		assertEquals("a.png", this.chosenBackground("-webkit-image-set(url(\"a.png\") 1x, url(\"b.png\") 2x)"));
		assertEquals("a.png", this.chosenBackground("image-set(\"a.png\" 1x, \"b.png\" 2x)"));
		// 未対応MIMEのtype()付き候補は飛ばす。type()の順序はどちらでも
		assertEquals("a.png", this.chosenBackground(
				"image-set(\"x.avif\" type(\"image/avif\"), \"a.png\" type(\"image/png\"), url(b.png) 2x type(\"image/png\"))"));
		assertEquals("a.png", this.chosenBackground("image-set(url(a.png) type(\"image/png\") 1x)"));
		// image()やグラデーションの候補は飛ばし、残りから選ぶ
		assertEquals("a.png",
				this.chosenBackground("image-set(linear-gradient(red, blue) 1x, image(\"z.png\") 1x, url(a.png) 1x)"));
		// 採れる候補が無ければ宣言無効
		assertNull(this.parseRaw("background-image", "image-set(linear-gradient(red, blue) 1x)"));
		assertNull(this.parseRaw("background-image", "image-set()"));
	}

	public void testOtherEntryPoints() {
		assertEquals("a.png",
				this.chosen("background", "image-set(url(a.png) 1x, url(b.png) 2x) no-repeat #fff", BackgroundImage.INFO));
		assertEquals("a.png", this.chosen("mask-image", "image-set(url(a.png) 1x, url(b.png) 2x)", MaskImage.INFO));
		assertEquals("a.png",
				this.chosen("list-style-image", "-webkit-image-set(url(a.png) 1x, url(b.png) 2x)", ListStyleImage.INFO));
		assertEquals("a.png",
				this.chosen("list-style", "image-set(url(a.png) 1x, url(b.png) 2x) inside", ListStyleImage.INFO));
		final Value content = this.entry("content", "image-set(url(a.png) 1x, url(b.png) 2x) \"!\"", Content.INFO);
		assertTrue(content instanceof ValueListValue);
		final Value first = ((ValueListValue) content).getValues()[0];
		assertTrue(first instanceof URIValue);
		assertTrue(((URIValue) first).getURI().toString().endsWith("/a.png"));
	}
}
