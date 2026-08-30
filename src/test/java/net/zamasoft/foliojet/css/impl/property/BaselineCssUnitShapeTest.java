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
import net.zamasoft.foliojet.css.impl.property.border.BorderImageOutset;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageRepeat;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSlice;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSource;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageWidth;
import net.zamasoft.foliojet.css.impl.property.box.ClipPath;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 2026-08-30に実装した単位・基本形状・{@code border-image}の解析を固定します。
 *
 * <p>
 * 単位({@code cap}/{@code ic}/{@code ric}/{@code rlh})はここでは
 * <b>解析結果の単位</b>までしか見ない——実際の長さは実フォントのcap-heightと
 * 根要素のline-heightが要るので、表示リストの基準データ
 * ({@code files/unittest/3020-VALUE/font-relative-units.html})で固定している。
 *
 * <p>
 * {@code rect()}/{@code xywh()}は<b>{@code inset()}へ畳んだ結果</b>まで見る。
 * この実装の要は「右辺・下辺は左上原点からの距離なので{@code 100% - 値}へ
 * 反転する」ところで、そこが逆になっていても解析だけなら通ってしまう。
 */
public class BaselineCssUnitShapeTest extends TestCase {

	/** {@code url()}の解決に使う基底URI。 */
	private static final java.net.URI BASE_URI = java.net.URI.create("file:///base/");

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssUnitShapeTest.class.getClassLoader(),
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
						return "BaselineCssUnitShapeTest.UserAgent";
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
				this.ua(), BASE_URI, false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		assertTrue(property instanceof CompositeProperty);
		return ((CompositeProperty) property).getEntries();
	}

	private Value single(final String name, final String value) {
		final Entry[] entries = this.parse(name, value);
		assertEquals(name + ": " + value + " の構成要素数", 1, entries.length);
		return entries[0].getValue();
	}

	private Value longhand(final String name, final String value, final PrimitivePropertyInfo info) {
		for (final Entry e : this.parse(name, value)) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail(name + ": " + value + " が " + info.getName() + " を設定していない");
		return null;
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), BASE_URI, false);
		assertTrue(name + ": " + value + " が黙って受理された", property == null || !this.warnings.isEmpty());
	}

	// ---- 1. 単位

	public void testQUnit() {
		// 1Q = 1/40 cm。10Q = 2.5mm
		assertEquals(2.5 / 25.4 * 72, ((AbsoluteLengthValue) this.single("width", "10Q")).getLength(), 1e-9);
	}

	public void testCapAndRlhUnits() {
		final RelativeLengthValue cap = (RelativeLengthValue) this.single("width", "10cap");
		assertEquals(Unit.CAP, cap.getUnit());
		assertEquals(10.0, cap.getValue(), 1e-9);
		final RelativeLengthValue rlh = (RelativeLengthValue) this.single("width", "2rlh");
		assertEquals(Unit.RLH, rlh.getUnit());
		assertEquals(2.0, rlh.getValue(), 1e-9);
	}

	public void testIcFoldsToEm() {
		// SPEC css-values-4: ic は表意文字U+6C34の送り幅で、取れないときの
		// 代替値が 1em。全角の表意文字の送りは事実上1emなので、この実装は
		// 常に代替値を使う(意図した近似)。ric も同様に rem へ畳む
		final RelativeLengthValue ic = (RelativeLengthValue) this.single("width", "10ic");
		assertEquals(Unit.EM, ic.getUnit());
		assertEquals(10.0, ic.getValue(), 1e-9);
		final RelativeLengthValue ric = (RelativeLengthValue) this.single("width", "10ric");
		assertEquals(Unit.REM, ric.getUnit());
		assertEquals(10.0, ric.getValue(), 1e-9);
		// 同じ書き方の em / rem と完全に同じ値になること
		final RelativeLengthValue em = (RelativeLengthValue) this.single("width", "10em");
		assertEquals(em.getUnit(), ic.getUnit());
		assertEquals(em.getValue(), ic.getValue(), 1e-9);
	}

	public void testCalcKeepsCapComponent() {
		// calc()はフォント相対成分を単位ごとに分けて計算値の段階まで持ち回る。
		// 新しい単位が成分配列に載っていないと、ここで黙って0になる
		final Value value = this.single("width", "calc(5cap + 10pt)");
		assertTrue("CalcFontRelativeValue でない: " + value, value instanceof CalcFontRelativeValue);
		final int capIndex = CalcFontRelativeValue.indexOf(Unit.CAP);
		assertTrue("CAPが成分配列に無い", capIndex >= 0);
		// 成分は private なので、成分ごとに書き出す toString() で確かめる
		// (UNITS の並びと同じ順に "<係数><単位名>" が並ぶ)
		assertTrue("cap成分が保たれていない: " + value, value.toString().contains("5.0cap"));
		assertTrue("絶対成分が保たれていない: " + value, value.toString().contains("10.0pt"));
	}

	public void testCalcKeepsRlhComponent() {
		final Value value = this.single("width", "calc(1rlh + 1cap)");
		assertTrue(value instanceof CalcFontRelativeValue);
		assertTrue(value.toString().contains("1.0rlh"));
		assertTrue(value.toString().contains("1.0cap"));
	}

	// ---- 2. rect() / xywh()

	private ShapeSpec shape(final String value) {
		final Value v = this.single("clip-path", value);
		assertTrue("ClipPathValue でない: " + v, v instanceof ClipPath.ClipPathValue);
		return ((ClipPath.ClipPathValue) v).shape();
	}

	public void testRectParses() {
		final ShapeSpec spec = this.shape("rect(10pt 90pt 90pt 10pt)");
		assertTrue("Rect でない: " + spec, spec instanceof ShapeSpec.Rect);
		final ShapeSpec.Rect rect = (ShapeSpec.Rect) spec;
		assertEquals(10.0, ((AbsoluteLengthValue) rect.top()).getLength(), 1e-9);
		assertEquals(90.0, ((AbsoluteLengthValue) rect.right()).getLength(), 1e-9);
		// auto は null で保つ
		final ShapeSpec.Rect auto = (ShapeSpec.Rect) this.shape("rect(auto 90pt auto 10pt)");
		assertNull(auto.top());
		assertNull(auto.bottom());
		assertNotNull(auto.right());
		// round も受ける
		assertNotNull(((ShapeSpec.Rect) this.shape("rect(10pt 90pt 90pt 10pt round 4pt)")).radii());
	}

	public void testXywhParses() {
		final ShapeSpec spec = this.shape("xywh(20pt 30pt 40pt 50pt)");
		assertTrue("Xywh でない: " + spec, spec instanceof ShapeSpec.Xywh);
		final ShapeSpec.Xywh xywh = (ShapeSpec.Xywh) spec;
		assertEquals(20.0, ((AbsoluteLengthValue) xywh.x()).getLength(), 1e-9);
		assertEquals(50.0, ((AbsoluteLengthValue) xywh.height()).getLength(), 1e-9);
		assertNotNull(((ShapeSpec.Xywh) this.shape("xywh(0 0 10pt 10pt round 2pt)")).radii());
	}

	public void testXywhRejects() {
		// xywh() に auto は無い
		this.assertInvalid("clip-path", "xywh(auto 0 10pt 10pt)");
		// 幅・高さは負にできない
		this.assertInvalid("clip-path", "xywh(0 0 -10pt 10pt)");
		this.assertInvalid("clip-path", "xywh(0 0 10pt -10pt)");
		// 値の数が違う
		this.assertInvalid("clip-path", "xywh(0 0 10pt)");
		this.assertInvalid("clip-path", "rect(10pt 90pt 90pt)");
	}

	public void testRectFoldsToSameInsetAsInset() {
		// この実装の要。rect()の右・下は「左上原点からの距離」なので
		// inset()の差し込み量へ 100% - 値 で反転する。参照ボックスの寸法は
		// レイアウト時にしか分からないため Length の割合成分として持ち回る。
		// 反転が抜けていても解析だけなら通るので、解決後の矩形で比べる
		final java.awt.geom.Rectangle2D fromRect = resolved("rect(10pt 90pt 90pt 10pt)");
		final java.awt.geom.Rectangle2D fromInset = resolved("inset(10pt)");
		assertEquals("x", fromInset.getX(), fromRect.getX(), 1e-9);
		assertEquals("y", fromInset.getY(), fromRect.getY(), 1e-9);
		assertEquals("幅", fromInset.getWidth(), fromRect.getWidth(), 1e-9);
		assertEquals("高さ", fromInset.getHeight(), fromRect.getHeight(), 1e-9);
		// 100pt角の箱なので (10,10)-(90,90) の 80x80 になるはず
		assertEquals(10.0, fromRect.getX(), 1e-9);
		assertEquals(10.0, fromRect.getY(), 1e-9);
		assertEquals(80.0, fromRect.getWidth(), 1e-9);
		assertEquals(80.0, fromRect.getHeight(), 1e-9);
	}

	public void testRectAutoMeansBoxEdge() {
		// auto はその辺が参照ボックスの辺に一致する = 差し込み0
		final java.awt.geom.Rectangle2D r = resolved("rect(auto 90pt 90pt auto)");
		assertEquals(0.0, r.getX(), 1e-9);
		assertEquals(0.0, r.getY(), 1e-9);
		assertEquals(90.0, r.getWidth(), 1e-9);
		assertEquals(90.0, r.getHeight(), 1e-9);
	}

	public void testXywhFoldsToInset() {
		// xywh(20 30 40 50) は左上(20,30)から 40x50
		final java.awt.geom.Rectangle2D r = resolved("xywh(20pt 30pt 40pt 50pt)");
		assertEquals(20.0, r.getX(), 1e-9);
		assertEquals(30.0, r.getY(), 1e-9);
		assertEquals(40.0, r.getWidth(), 1e-9);
		assertEquals(50.0, r.getHeight(), 1e-9);
	}

	public void testRectPercentages() {
		// 割合も同じ反転を通ること
		final java.awt.geom.Rectangle2D r = resolved("rect(10% 90% 90% 10%)");
		assertEquals(10.0, r.getX(), 1e-9);
		assertEquals(80.0, r.getWidth(), 1e-9);
	}

	/** 100pt角の箱へ当てて、切り抜き形状の外接矩形を返します。 */
	private java.awt.geom.Rectangle2D resolved(final String value) {
		final ClipPathShape shape = ClipPath.toShape(this.single("clip-path", value));
		assertTrue("Inset へ畳まれていない: " + shape, shape instanceof ClipPathShape.Inset);
		return shape.resolve(0, 0, 100, 100).getBounds2D();
	}

	public void testBasicShapesStillParsesInset() {
		// rect()/xywh() を足したことで既存の basic-shape が壊れていないこと
		assertTrue(this.shape("inset(10pt)") instanceof ShapeSpec.Inset);
		assertTrue(this.shape("circle(50%)") instanceof ShapeSpec.Circle);
		assertTrue(this.shape("ellipse(40% 50%)") instanceof ShapeSpec.Ellipse);
		assertTrue(this.shape("polygon(0 0, 100% 0, 50% 100%)") instanceof ShapeSpec.Polygon);
		assertNotNull(BasicShapes.class);
	}

	// ---- 3. border-image の解析

	public void testBorderImageSource() {
		assertNotNull(this.single("border-image-source", "none"));
		assertNotNull(this.single("border-image-source", "url(frame.png)"));
		assertNotNull(this.single("border-image-source", "linear-gradient(red, blue)"));
		this.assertInvalid("border-image-source", "10pt");
	}

	public void testBorderImageSlice() {
		assertNotNull(this.single("border-image-slice", "30"));
		assertNotNull(this.single("border-image-slice", "30%"));
		assertNotNull(this.single("border-image-slice", "10 20 30 40"));
		assertNotNull(this.single("border-image-slice", "30 fill"));
		// fill は任意の位置に置ける
		assertNotNull(this.single("border-image-slice", "fill 30"));
		// 負の値と5値以上は無い
		this.assertInvalid("border-image-slice", "-1");
		this.assertInvalid("border-image-slice", "1 2 3 4 5");
	}

	public void testBorderImageWidth() {
		assertNotNull(this.single("border-image-width", "1"));
		assertNotNull(this.single("border-image-width", "20pt"));
		assertNotNull(this.single("border-image-width", "25%"));
		assertNotNull(this.single("border-image-width", "auto"));
		assertNotNull(this.single("border-image-width", "1 2 3 4"));
		this.assertInvalid("border-image-width", "-1");
		this.assertInvalid("border-image-width", "1 2 3 4 5");
	}

	public void testBorderImageOutset() {
		assertNotNull(this.single("border-image-outset", "0"));
		assertNotNull(this.single("border-image-outset", "5pt"));
		assertNotNull(this.single("border-image-outset", "1 2"));
		this.assertInvalid("border-image-outset", "-1");
		// outset に割合は無い
		this.assertInvalid("border-image-outset", "10%");
	}

	public void testBorderImageRepeat() {
		assertNotNull(this.single("border-image-repeat", "stretch"));
		assertNotNull(this.single("border-image-repeat", "repeat"));
		assertNotNull(this.single("border-image-repeat", "round"));
		assertNotNull(this.single("border-image-repeat", "space"));
		assertNotNull(this.single("border-image-repeat", "repeat round"));
		this.assertInvalid("border-image-repeat", "no-such-repeat");
		this.assertInvalid("border-image-repeat", "repeat round space");
	}

	public void testBorderImageShorthand() {
		// 5つのロングハンドすべてを設定すること(短縮形は初期化も担う)
		for (final PrimitivePropertyInfo info : new PrimitivePropertyInfo[] { BorderImageSource.INFO,
				BorderImageSlice.INFO, BorderImageWidth.INFO, BorderImageOutset.INFO, BorderImageRepeat.INFO }) {
			assertNotNull(this.longhand("border-image", "url(frame.png) 30 stretch", info));
		}
	}

	public void testBorderImageShorthandSlashForms() {
		// / の後ろが幅、// の後ろが outset
		assertNotNull(this.longhand("border-image", "url(a.png) 30 / 20pt", BorderImageWidth.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png) 30 / 20pt / 5pt", BorderImageOutset.INFO));
		// 幅を省いて outset だけ書く `30 / / 5pt` は、こちらの短縮形ではなく
		// **CSSパーサー(ph-css)が宣言そのものを解析できない**ため試せない。
		// 実文書での出現は事実上ないので追わない(2026-08-30に確認)
		// source を省いても書ける
		assertNotNull(this.longhand("border-image", "30 stretch", BorderImageSlice.INFO));
		// 全体キーワード
		assertNotNull(this.longhand("border-image", "none", BorderImageSource.INFO));
	}

	public void testBorderImageShorthandResetsOmitted() {
		// 書かなかった成分が初期値へ戻ること。ここが抜けていると直前の規則の
		// border-image-outset 等が残って効き続ける
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageOutset.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageRepeat.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageWidth.INFO));
	}

}
