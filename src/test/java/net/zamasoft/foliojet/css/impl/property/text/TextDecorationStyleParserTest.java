package net.zamasoft.foliojet.css.impl.property.text;

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
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 装飾線の個別指定({@code text-decoration-style/-thickness}、
 * {@code text-underline-offset/-position})・{@code line-clamp}・
 * {@code text-shadow}のぼかし半径の解析テストです(2026-08-29)。
 *
 * <p>
 * {@code ModernCssValuesTest}と同じ手順(宣言→{@code ElementPropertySet}
 * →構成要素)で、描画へ配線した値が解析で意図どおりの型に落ちることを
 * 固定する。ぼかし半径は同日夜まで解析して捨てていた。
 * </p>
 */
public class TextDecorationStyleParserTest extends TestCase {
	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(TextDecorationStyleParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
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
						return "TextDecorationStyleParserTest.UserAgent";
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

	private Entry[] parse(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		assertTrue(property instanceof CompositeProperty);
		return ((CompositeProperty) property).getEntries();
	}

	private Value single(final String name, final String value) {
		final Entry[] entries = this.parse(name, value);
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private static Value entry(final Entry[] entries, final PrimitivePropertyInfo info) {
		for (final Entry e : entries) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail("構成要素が無い: " + info.getName());
		return null;
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertNull(name + ": " + value + " が受理された", property);
		assertFalse("警告が出ていない: " + name + ": " + value, this.warnings.isEmpty());
	}

	public void testDecorationStyle() {
		assertSame(TextDecorationAux.SOLID, this.single("text-decoration-style", "solid"));
		assertEquals("wavy", this.single("text-decoration-style", "wavy").toString());
		assertEquals("double", this.single("text-decoration-style", "double").toString());
		assertEquals("dotted", this.single("-webkit-text-decoration-style", "dotted").toString());
		this.assertInvalid("text-decoration-style", "groove");
		this.assertInvalid("text-decoration-style", "dotted dashed");
	}

	public void testDecorationThickness() {
		assertSame(KeywordValue.AUTO, this.single("text-decoration-thickness", "auto"));
		// from-font はフォントの下線太さを取れないため auto と同じ扱い(NORMAL番兵)
		assertSame(KeywordValue.NORMAL, this.single("text-decoration-thickness", "from-font"));
		assertEquals(2.0, ((AbsoluteLengthValue) this.single("text-decoration-thickness", "2pt")).getLength(), 1e-6);
		assertEquals(0.1, ((PercentageValue) this.single("text-decoration-thickness", "10%")).getRatio(), 1e-6);
		this.assertInvalid("text-decoration-thickness", "-1pt");
		this.assertInvalid("text-decoration-thickness", "thick");
	}

	public void testUnderlineOffset() {
		assertSame(KeywordValue.AUTO, this.single("text-underline-offset", "auto"));
		assertEquals(3.0, ((AbsoluteLengthValue) this.single("text-underline-offset", "3pt")).getLength(), 1e-6);
		// 負の値は線を文字へ寄せる(許される)
		assertEquals(-1.0, ((AbsoluteLengthValue) this.single("text-underline-offset", "-1pt")).getLength(), 1e-6);
		assertEquals(0.2, ((PercentageValue) this.single("text-underline-offset", "20%")).getRatio(), 1e-6);
		this.assertInvalid("text-underline-offset", "from-font");
	}

	public void testUnderlinePosition() {
		assertSame(KeywordValue.AUTO, this.single("text-underline-position", "auto"));
		// from-font 単独は auto 相当
		assertSame(KeywordValue.AUTO, this.single("text-underline-position", "from-font"));
		assertEquals("under", this.single("text-underline-position", "under").toString());
		assertEquals("under right", this.single("text-underline-position", "right under").toString());
		assertEquals("left", this.single("text-underline-position", "from-font left").toString());
		assertEquals("right", this.single("-webkit-text-underline-position", "right").toString());
		this.assertInvalid("text-underline-position", "under from-font");
		this.assertInvalid("text-underline-position", "left right");
		this.assertInvalid("text-underline-position", "auto under");
		this.assertInvalid("text-underline-position", "below");
	}

	public void testShorthandCarriesStyleAndThickness() {
		final Entry[] entries = this.parse("text-decoration", "underline wavy red 3pt");
		assertEquals("wavy", entry(entries, TextDecorationAux.STYLE).toString());
		assertEquals(3.0, ((AbsoluteLengthValue) entry(entries, TextDecorationAux.THICKNESS)).getLength(), 1e-6);
		// 省略した構成要素は初期値へ戻る
		final Entry[] plain = this.parse("text-decoration", "underline");
		assertSame(TextDecorationAux.SOLID, entry(plain, TextDecorationAux.STYLE));
		assertSame(KeywordValue.AUTO, entry(plain, TextDecorationAux.THICKNESS));
	}

	public void testLineClamp() {
		assertEquals(3, ((IntegerValue) this.single("line-clamp", "3")).getInteger());
		assertEquals(1, ((IntegerValue) this.single("-webkit-line-clamp", "1")).getInteger());
		assertSame(KeywordValue.NONE, this.single("line-clamp", "none"));
		this.assertInvalid("line-clamp", "0");
		this.assertInvalid("line-clamp", "2.5");
		this.assertInvalid("line-clamp", "3 lines");
	}

	public void testTextShadowBlur() {
		TextShadowValue shadow = (TextShadowValue) this.single("text-shadow", "1pt 2pt 4pt black");
		assertEquals(1, shadow.getShadows().length);
		assertEquals(4.0, ((AbsoluteLengthValue) shadow.getShadows()[0].blur).getLength(), 1e-6);
		// ぼかし省略は null(=0)
		shadow = (TextShadowValue) this.single("text-shadow", "1pt 2pt black");
		assertNull(shadow.getShadows()[0].blur);
		// 色→長さの順、複数の影
		shadow = (TextShadowValue) this.single("text-shadow", "red 0 0 6pt, 1pt 1pt 0 blue");
		assertEquals(2, shadow.getShadows().length);
		assertEquals(6.0, ((AbsoluteLengthValue) shadow.getShadows()[0].blur).getLength(), 1e-6);
		assertEquals(0.0, ((AbsoluteLengthValue) shadow.getShadows()[1].blur).getLength(), 1e-6);
		// 負のぼかしは無効
		this.assertInvalid("text-shadow", "1pt 1pt -2pt black");
	}
}
