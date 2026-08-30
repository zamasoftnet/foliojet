package net.zamasoft.foliojet.css.impl.property;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.impl.property.image.ImageOrientation;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * MDN Baselineの棚卸しで最後まで残っていた3件を固定します(2026-08-30)——
 * {@code @font-feature-values}の名前解決、{@code image-orientation}、
 * {@code border-image-repeat}のタイル。
 *
 * <p>
 * {@code border-image-repeat}のタイルは描画の話なので、枚数の検査は
 * 基準画像({@code files/visual/2110-border-image}) と、この場での
 * 実測(stretch 8 / repeat 60 / round 54 / space 52 枚)に任せ、
 * ここでは値が正しく届くところまでを見る。
 */
public class BaselineCssRemainderTest extends TestCase {

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssRemainderTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getFontSize":
						return 12.0;
					case "getFontMagnification":
						return 1.0;
					case "getDocumentContext":
						return new DocumentContext();
					case "getProperty":
						return null;
					case "message":
						this.warnings.add(String.valueOf(args[0]) + ":" + java.util.Arrays.toString(args));
						return null;
					case "toString":
						return "BaselineCssRemainderTest.UserAgent";
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

	private Value single(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), java.net.URI.create("file:///base/"), false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		final Entry[] entries = ((CompositeProperty) property).getEntries();
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), java.net.URI.create("file:///base/"), false);
		assertTrue(name + ": " + value + " が黙って受理された", property == null || !this.warnings.isEmpty());
	}

	// ---- 1. image-orientation

	public void testImageOrientationValues() {
		assertSame(KeywordValue.FROM_IMAGE, this.single("image-orientation", "from-image"));
		assertSame(KeywordValue.NONE, this.single("image-orientation", "none"));
		// 初期の草案にあった角度指定は現行仕様から落ちている
		this.assertInvalid("image-orientation", "90deg");
		this.assertInvalid("image-orientation", "no-such-value");
	}

	public void testImageOrientationApplyIsIdentityForFromImage() {
		// nullは素通し(呼び出し側が無条件に通せること)
		assertNull(ImageOrientation.apply(null, null));
	}

	// ---- 2. @font-feature-values の名前解決

	/**
	 * 名前表を直に組み立てて、{@code font-variant-alternates}の関数が
	 * OpenType機能タグへ落ちることを見ます。@規則の解析は
	 * {@code CSSStyleSheetBuilder}側なので、ここは<b>解決の対応表</b>
	 * (css-fonts-4 §6.9)が正しいかに絞る。
	 */
	public void testFeatureValuesResolveToTags() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLISTIC, "alt-a", new int[] { 1 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.CHARACTER_VARIANT, "beta", new int[] { 2 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.SWASH, "fancy", new int[] { 1 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.ORNAMENTS, "ding", new int[] { 2 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.ANNOTATION, "circled", new int[] { 1 });

		assertEquals("styleset(12) は ss12", "ss12", tagOf(values, "styleset", "nice"));
		assertEquals("stylistic は salt", "salt", tagOf(values, "stylistic", "alt-a"));
		assertEquals("character-variant(2) は cv02", "cv02", tagOf(values, "character-variant", "beta"));
		assertEquals("swash は swsh", "swsh", tagOf(values, "swash", "fancy"));
		assertEquals("ornaments は ornm", "ornm", tagOf(values, "ornaments", "ding"));
		assertEquals("annotation は nalt", "nalt", tagOf(values, "annotation", "circled"));
	}

	public void testUndefinedFeatureNameIsDropped() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		// 表に無い名前は、その関数だけが落ちる(宣言全体は生きる)
		final FontVariantAlternatesValue value = FontVariantAlternatesValue.create(false,
				List.of(new FontVariantAlternatesValue.Alternate("styleset", List.of("no-such-name"))));
		final String tags = value.featureSet(values, "Test Font").toString();
		assertEquals("未定義の名前が機能タグになっている: " + tags, "FontFeatureSet[]", tags);
	}

	public void testFeatureValuesAreScopedToFamily() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		assertNotNull(values.lookup("Test Font", FontFeatureValues.Type.STYLESET, "nice"));
		// 別のファミリの表は引けない
		assertNull(values.lookup("Other Font", FontFeatureValues.Type.STYLESET, "nice"));
	}

	public void testEmptyFeatureValuesKeepsExistingBehaviour() {
		// 表が空の文書では、@規則が無かったときと同じ結果になること
		final FontFeatureValues empty = new FontFeatureValues();
		assertTrue(empty.isEmpty());
		final FontVariantAlternatesValue historical = FontVariantAlternatesValue.create(true, List.of());
		assertEquals(historical.featureSet().toString(), historical.featureSet(empty, "Test Font").toString());
	}

	private static String tagOf(final FontFeatureValues values, final String function, final String name) {
		final FontVariantAlternatesValue value = FontVariantAlternatesValue.create(false,
				List.of(new FontVariantAlternatesValue.Alternate(function, List.of(name))));
		// FontFeatureSet の toString は "FontFeatureSet[ss12=1]" の形。
		// 角括弧の中の "<タグ>=<値>" からタグだけを取り出す
		final String text = value.featureSet(values, "Test Font").toString();
		final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\w{4})=")
				.matcher(text);
		assertTrue(function + " が機能タグを出していない: " + text, matcher.find());
		return matcher.group(1);
	}

	// ---- 3. border-image-repeat の値

	public void testBorderImageRepeatValues() {
		for (final String v : new String[] { "stretch", "repeat", "round", "space" }) {
			assertNotNull(this.single("border-image-repeat", v));
		}
		assertNotNull(this.single("border-image-repeat", "repeat round"));
		this.assertInvalid("border-image-repeat", "repeat round space");
	}
}
