package net.zamasoft.foliojet.css.impl.property.grid;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.box.AspectRatio;
import net.zamasoft.foliojet.css.impl.property.shorthand.GridAreaShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.GridLineShorthand;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AspectRatioValue;
import net.zamasoft.foliojet.css.value.GridAutoFlowValue;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.GridTemplateAreasValue;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 2026-08-29のGrid拡張とaspect-ratioの解析テストです(50サイト掃過で
 * 見つかった未対応値)。grid-area/grid-column/grid-rowの省略補完
 * (css-grid-1 §8.4)、線名付きの線・トラック、%・min-content・
 * auto-fill/auto-fit・subgridのトラック、grid-template-areasの矩形検証、
 * grid-auto-flow、aspect-ratioの受理/拒否を固定する。
 */
public class GridShorthandParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(GridShorthandParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "GridShorthandParserTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	/** ショートハンドを展開し、longhand→値の写像を返します(Primitivesの中身をリフレクションで読む)。 */
	@SuppressWarnings("unchecked")
	private static java.util.Map<String, Value> expand(final AbstractShorthandPropertyInfo info, final String declaration)
			throws Exception {
		final Class<?> primitivesClass = Class
				.forName("net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo$Primitives");
		final java.lang.reflect.Constructor<?> ctor = primitivesClass.getDeclaredConstructor();
		ctor.setAccessible(true);
		final Object primitives = ctor.newInstance();
		final java.lang.reflect.Method parse = AbstractShorthandPropertyInfo.class.getDeclaredMethod("parseValues",
				TokenStream.class, UserAgent.class, java.net.URI.class, primitivesClass);
		parse.setAccessible(true);
		try {
			parse.invoke(info, tokens(declaration), ua(), null, primitives);
		} catch (java.lang.reflect.InvocationTargetException e) {
			if (e.getCause() instanceof PropertyException pe) {
				throw pe;
			}
			throw e;
		}
		final java.lang.reflect.Field entries = primitivesClass.getDeclaredField("entries");
		entries.setAccessible(true);
		final java.util.Map<String, Value> map = new java.util.LinkedHashMap<>();
		for (final Object entry : (List<Object>) entries.get(primitives)) {
			final net.zamasoft.foliojet.css.property.CompositeProperty.Entry e = (net.zamasoft.foliojet.css.property.CompositeProperty.Entry) entry;
			map.put(e.getPrimitivePropertyInfo().getName(), e.getValue());
		}
		return map;
	}

	private static GridLineValue line(final java.util.Map<String, Value> map, final String name) {
		return (GridLineValue) map.get(name);
	}

	public void testGridAreaFourValues() throws Exception {
		final java.util.Map<String, Value> m = expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO,
				"grid-area: 1 / 2 / 3 / 4");
		assertEquals(1, line(m, "grid-row-start").getNumber());
		assertEquals(2, line(m, "grid-column-start").getNumber());
		assertEquals(3, line(m, "grid-row-end").getNumber());
		assertEquals(4, line(m, "grid-column-end").getNumber());
	}

	public void testGridAreaName() throws Exception {
		// 領域名: 4値とも同じ名前(レイアウト側がheader-start/-endへ解決)
		final java.util.Map<String, Value> m = expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO,
				"grid-area: header");
		for (final String longhand : new String[] { "grid-row-start", "grid-column-start", "grid-row-end",
				"grid-column-end" }) {
			assertTrue(longhand, line(m, longhand).isNameOnly());
			assertEquals("header", line(m, longhand).getName());
		}
	}

	public void testGridAreaOmittedValues() throws Exception {
		// 省略はauto(startが線名単独のときだけ同名)
		java.util.Map<String, Value> m = expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO,
				"grid-area: 2 / span 2");
		assertEquals(2, line(m, "grid-row-start").getNumber());
		assertTrue(line(m, "grid-column-start").isSpan());
		assertEquals(2, line(m, "grid-column-start").getNumber());
		assertTrue(line(m, "grid-row-end").isAuto());
		assertTrue(line(m, "grid-column-end").isAuto());
		m = expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO, "grid-area: a / b");
		assertEquals("a", line(m, "grid-row-end").getName());
		assertEquals("b", line(m, "grid-column-end").getName());
		m = expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO, "grid-area: a / 2");
		assertEquals("a", line(m, "grid-row-end").getName());
		assertTrue(line(m, "grid-column-end").isAuto());
	}

	public void testGridAreaRejected() throws Exception {
		for (final String bad : new String[] { "1 / 2 / 3 / 4 / 5", "1 2", "span" }) {
			try {
				expand((AbstractShorthandPropertyInfo) GridAreaShorthand.INFO, "grid-area: " + bad);
				fail("拒否されるべきgrid-area: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testGridColumnNameAndSpanZero() throws Exception {
		java.util.Map<String, Value> m = expand((AbstractShorthandPropertyInfo) GridLineShorthand.COLUMN,
				"grid-column: content");
		assertEquals("content", line(m, "grid-column-start").getName());
		assertEquals("content", line(m, "grid-column-end").getName());
		m = expand((AbstractShorthandPropertyInfo) GridLineShorthand.COLUMN, "grid-column: 1 / -1");
		assertEquals(1, line(m, "grid-column-start").getNumber());
		assertEquals(-1, line(m, "grid-column-end").getNumber());
		// span 0は仕様では無効だが実物で見かける——span 1として受理
		m = expand((AbstractShorthandPropertyInfo) GridLineShorthand.COLUMN, "grid-column: span 0");
		assertTrue(line(m, "grid-column-start").isSpan());
		assertEquals(1, line(m, "grid-column-start").getNumber());
		assertTrue(line(m, "grid-column-end").isAuto());
		// span 2 name / N name
		m = expand((AbstractShorthandPropertyInfo) GridLineShorthand.ROW, "grid-row: 2 main / span 2 main");
		assertEquals(2, line(m, "grid-row-start").getNumber());
		assertEquals("main", line(m, "grid-row-start").getName());
		assertTrue(line(m, "grid-row-end").isSpan());
		assertEquals("main", line(m, "grid-row-end").getName());
	}

	private static GridTrackListValue computedTracks(final PrimitivePropertyInfo info, final String value)
			throws PropertyException {
		final GridTemplateTracks tracks = (GridTemplateTracks) info;
		return (GridTrackListValue) tracks.getComputedValue(
				tracks.parseValue(tokens(info.getName() + ": " + value), ua(), null), null);
	}

	public void testNamedLinesInTrackList() throws Exception {
		final GridTrackListValue v = computedTracks(GridTemplateTracks.COLUMNS,
				"[full-start] 10pt [content-start] 20pt [content-end full-end]");
		assertEquals(2, v.getTracks().size());
		assertEquals(List.of("full-start"), v.getLineNames().get(0));
		assertEquals(List.of("content-start"), v.getLineNames().get(1));
		assertEquals(List.of("content-end", "full-end"), v.getLineNames().get(2));
		// repeat内の線名は繰り返され、隣接する線名は同じ線に集まる
		final GridTrackListValue r = computedTracks(GridTemplateTracks.COLUMNS, "repeat(2, [a] 10pt [b])");
		assertEquals(2, r.getTracks().size());
		assertEquals(List.of("a"), r.getLineNames().get(0));
		assertEquals(List.of("b", "a"), r.getLineNames().get(1));
		assertEquals(List.of("b"), r.getLineNames().get(2));
	}

	public void testPercentAndContentTracks() throws Exception {
		GridTrackListValue v = computedTracks(GridTemplateTracks.COLUMNS, "100%");
		assertEquals(1, v.getTracks().size());
		assertEquals(1.0, ((GridTrackListValue.Percentage) v.getTracks().get(0)).ratio(), 1e-9);
		v = computedTracks(GridTemplateTracks.COLUMNS, "repeat(4, 25%)");
		assertEquals(4, v.getTracks().size());
		assertEquals(0.25, ((GridTrackListValue.Percentage) v.getTracks().get(3)).ratio(), 1e-9);
		v = computedTracks(GridTemplateTracks.COLUMNS, "min-content auto");
		assertTrue(v.getTracks().get(0) instanceof GridTrackListValue.MinContent);
		assertTrue(v.getTracks().get(1) instanceof GridTrackListValue.Auto);
		v = computedTracks(GridTemplateTracks.COLUMNS, "min-content minmax(0, auto) min-content");
		assertEquals(3, v.getTracks().size());
		assertTrue(v.getTracks().get(1) instanceof GridTrackListValue.Auto);
		v = computedTracks(GridTemplateTracks.COLUMNS, "max-content 1fr");
		assertTrue(v.getTracks().get(0) instanceof GridTrackListValue.MaxContent);
		// subgridは単一autoの近似
		v = computedTracks(GridTemplateTracks.COLUMNS, "subgrid [a] [b]");
		assertEquals(1, v.getTracks().size());
		assertTrue(v.getTracks().get(0) instanceof GridTrackListValue.Auto);
	}

	public void testAutoRepeat() throws Exception {
		final GridTrackListValue v = computedTracks(GridTemplateTracks.COLUMNS,
				"repeat(auto-fill, minmax(100pt, 1fr))");
		assertEquals(1, v.getTracks().size());
		final GridTrackListValue.AutoRepeat repeat = (GridTrackListValue.AutoRepeat) v.getTracks().get(0);
		assertFalse(repeat.fit());
		assertEquals(100.0, repeat.unitMinLength(), 1e-9);
		assertEquals(1, repeat.unit().size());
		assertTrue(repeat.unit().get(0) instanceof GridTrackListValue.Fr);
		final GridTrackListValue fit = computedTracks(GridTemplateTracks.COLUMNS,
				"10pt repeat(auto-fit, [col] minmax(20%, 50pt)) 10pt");
		assertEquals(3, fit.getTracks().size());
		final GridTrackListValue.AutoRepeat r = (GridTrackListValue.AutoRepeat) fit.getTracks().get(1);
		assertTrue(r.fit());
		assertEquals(0.2, r.unitMinRatio(), 1e-9);
		assertEquals(List.of("col"), r.unitLineNames().get(0));
		// auto-repeatのunitに内容依存トラックは不可、auto-repeatは1つまで
		for (final String bad : new String[] { "repeat(auto-fill, 1fr)", "repeat(auto-fill, auto)",
				"repeat(auto-fill, 10pt) repeat(auto-fit, 10pt)" }) {
			try {
				computedTracks(GridTemplateTracks.COLUMNS, bad);
				fail("拒否されるべきtrack list: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testImplicitTracks() throws Exception {
		final GridTrackListValue v = computedTracks(GridTemplateTracks.AUTO_ROWS, "30pt auto");
		assertEquals(2, v.getTracks().size());
		for (final String bad : new String[] { "none", "[a] 10pt", "repeat(2, 10pt)" }) {
			try {
				computedTracks(GridTemplateTracks.AUTO_COLUMNS, bad);
				fail("grid-auto-columnsで拒否されるべき: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testTemplateAreas() throws Exception {
		final GridTemplateAreas info = (GridTemplateAreas) GridTemplateAreas.INFO;
		GridTemplateAreasValue v = (GridTemplateAreasValue) info
				.parseValue(tokens("grid-template-areas: \"head head\" \"nav main\" \"nav .\""), ua(), null);
		assertEquals(3, v.getRowCount());
		assertEquals(2, v.getColumnCount());
		assertEquals(3, v.getAreas().size());
		final GridTemplateAreasValue.Area nav = v.getAreas().get(1);
		assertEquals("nav", nav.name());
		assertEquals(1, nav.rowStart());
		assertEquals(3, nav.rowEnd());
		assertEquals(0, nav.columnStart());
		assertEquals(1, nav.columnEnd());
		// 連続する.は1個の空セル
		v = (GridTemplateAreasValue) info.parseValue(tokens("grid-template-areas: \"a ...\" \"a b\""), ua(), null);
		assertEquals(2, v.getColumnCount());
		assertTrue(((GridTemplateAreasValue) info.parseValue(tokens("grid-template-areas: none"), ua(), null))
				.isNone());
		for (final String bad : new String[] { "\"a b\" \"b a\"", "\"a\" \"b c\"", "\"\"", "a b" }) {
			try {
				info.parseValue(tokens("grid-template-areas: " + bad), ua(), null);
				fail("拒否されるべきgrid-template-areas: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testAutoFlow() throws Exception {
		final GridAutoFlow info = (GridAutoFlow) GridAutoFlow.INFO;
		assertSame(GridAutoFlowValue.COLUMN, info.parseValue(tokens("grid-auto-flow: column"), ua(), null));
		assertSame(GridAutoFlowValue.ROW_DENSE, info.parseValue(tokens("grid-auto-flow: dense"), ua(), null));
		assertSame(GridAutoFlowValue.COLUMN_DENSE,
				info.parseValue(tokens("grid-auto-flow: dense column"), ua(), null));
		for (final String bad : new String[] { "column column", "row column", "sparse" }) {
			try {
				info.parseValue(tokens("grid-auto-flow: " + bad), ua(), null);
				fail("拒否されるべきgrid-auto-flow: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testAspectRatio() throws Exception {
		final AspectRatio info = (AspectRatio) AspectRatio.INFO;
		AspectRatioValue v = (AspectRatioValue) info.parseValue(tokens("aspect-ratio: 16 / 9"), ua(), null);
		assertFalse(v.isAuto());
		assertEquals(16.0 / 9.0, v.getRatio(), 1e-9);
		v = (AspectRatioValue) info.parseValue(tokens("aspect-ratio: auto 1"), ua(), null);
		assertTrue(v.isAuto());
		assertEquals(1.0, v.getRatio(), 1e-9);
		v = (AspectRatioValue) info.parseValue(tokens("aspect-ratio: 0.5 auto"), ua(), null);
		assertTrue(v.isAuto());
		assertEquals(0.5, v.getRatio(), 1e-9);
		assertSame(AspectRatioValue.AUTO_VALUE, info.parseValue(tokens("aspect-ratio: auto"), ua(), null));
		// 退化した比率はauto扱い
		assertFalse(((AspectRatioValue) info.parseValue(tokens("aspect-ratio: 1 / 0"), ua(), null)).hasRatio());
		for (final String bad : new String[] { "-1", "1 / -2", "auto auto", "1 2", "16 / 9 / 2", "abc" }) {
			try {
				info.parseValue(tokens("aspect-ratio: " + bad), ua(), null);
				fail("拒否されるべきaspect-ratio: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}
}
