package net.zamasoft.foliojet.css.impl.property.box;

import java.awt.geom.Rectangle2D;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.text.TextOverflow;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;

/**
 * {@code clip-path: path()}・{@code text-overflow}・{@code mix-blend-mode}・
 * {@code isolation}の解析テストです(2026-08-29)。
 */
public class MiscPropertyParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static Object parse(final PrimitivePropertyInfo info, final String css) {
		try {
			return ((net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo) info)
					.parseValue(tokens(info.getName() + ": " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testClipPathPath() {
		final Object v = parse(ClipPath.INFO, "path(\"M0 0 H100 V100 Z\")");
		assertTrue(String.valueOf(v), v instanceof ClipPath.ClipPathValue);
		final ClipPath.ClipPathValue cv = (ClipPath.ClipPathValue) v;
		assertTrue(cv.shape() instanceof net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec.Path);
		assertEquals(ClipPathShape.ReferenceBox.BORDER_BOX, cv.box());
		final ClipPathShape shape = ClipPath.toShape(cv);
		assertTrue(shape instanceof ClipPathShape.Path);
		// uaなしのpx→ptは0.75。参照ボックス(10,20)原点で100px=75pt
		final Rectangle2D b = shape.resolve(10, 20, 200, 200).getBounds2D();
		assertEquals(10.0, b.getMinX(), 1e-9);
		assertEquals(85.0, b.getMaxX(), 1e-9);
		assertEquals(20.0, b.getMinY(), 1e-9);
		assertEquals(95.0, b.getMaxY(), 1e-9);

		// fill-rule付き・参照ボックス併記
		final Object v2 = parse(ClipPath.INFO, "path(evenodd, \"M0 0 h10 v10 z\") content-box");
		assertTrue(String.valueOf(v2), v2 instanceof ClipPath.ClipPathValue);
		assertEquals(ClipPathShape.ReferenceBox.CONTENT_BOX, ((ClipPath.ClipPathValue) v2).box());
		assertTrue(((net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec.Path) ((ClipPath.ClipPathValue) v2).shape()).evenOdd());

		// 不正: 文字列でない・パス文法エラー・未知のfill-rule
		assertTrue(parse(ClipPath.INFO, "path(M0 0)") instanceof PropertyException);
		assertTrue(parse(ClipPath.INFO, "path(\"L0 0\")") instanceof PropertyException);
		assertTrue(parse(ClipPath.INFO, "path(inside, \"M0 0\")") instanceof PropertyException);
	}

	public void testTextOverflow() {
		assertSame(KeywordValue.CLIP, parse(TextOverflow.INFO, "clip"));
		assertSame(TextOverflow.ELLIPSIS, parse(TextOverflow.INFO, "ellipsis"));
		assertTrue(parse(TextOverflow.INFO, "fade") instanceof PropertyException);
		assertTrue(parse(TextOverflow.INFO, "clip ellipsis") instanceof PropertyException);
	}

	public void testMixBlendMode() {
		for (final BlendMode mode : BlendMode.values()) {
			final Object v = parse(MixBlendMode.INFO, mode.cssName);
			assertTrue(mode.cssName + ": " + v, v instanceof MixBlendMode.BlendModeValue);
			assertEquals(mode, ((MixBlendMode.BlendModeValue) v).mode());
		}
		assertEquals(BlendMode.COLOR_DODGE, ((MixBlendMode.BlendModeValue) parse(MixBlendMode.INFO, "Color-Dodge")).mode());
		assertTrue(parse(MixBlendMode.INFO, "plus-lighter") instanceof PropertyException);
		assertTrue(parse(MixBlendMode.INFO, "multiply screen") instanceof PropertyException);
	}

	public void testIsolation() {
		assertSame(KeywordValue.AUTO, parse(Isolation.INFO, "auto"));
		final Value isolate = (Value) parse(Isolation.INFO, "isolate");
		assertSame(Isolation.ISOLATE, isolate);
		assertTrue(parse(Isolation.INFO, "none") instanceof PropertyException);
	}
}
