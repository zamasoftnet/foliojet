package net.zamasoft.foliojet.css.impl.property.flex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.shorthand.FlexFlowShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.FlexShorthand;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.FlexDirectionValue;
import net.zamasoft.foliojet.css.value.FlexWrapValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Flex F1aの解析テストです(consult-codex-2026-08-02-flexbox.txt F1a)。
 * flex-direction/flex-wrap/flex-grow/flex-shrink/flex-basisと
 * flex/flex-flowショートハンドの受理/拒否、既定{@code 0 1 auto}、
 * ショートハンド省略時の既定(grow=1・shrink=1・basis=0)を固定する。
 * computed(em絶対化)はstyle文脈が要るためRaw中間形の受理までを対象と
 * する(GridTrackParserTestと同方針)。
 */
public class FlexParserTest extends TestCase {

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
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(FlexParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "FlexParserTest.UserAgent";
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

	private static Value parse(final PrimitivePropertyInfo info, final String value) throws PropertyException {
		return ((net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo) info)
				.parseValue(tokens(info.getName() + ": " + value), ua(), null);
	}

	private static void assertInvalid(final PrimitivePropertyInfo info, final String value) {
		try {
			parse(info, value);
			fail(info.getName() + ": " + value + " が受理された");
		} catch (final PropertyException e) {
			// 期待どおり
		}
	}

	/** ショートハンドを解析し、primitive名→値のMapへ展開します。 */
	private static Map<String, Value> parseShorthand(final ShorthandPropertyInfo info, final String value)
			throws Exception {
		final Property property = ((net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo) info)
				.parse(tokens(info.getName() + ": " + value), ua(), null, false);
		final java.lang.reflect.Field field = CompositeProperty.class.getDeclaredField("entries");
		field.setAccessible(true);
		final CompositeProperty.Entry[] entries = (CompositeProperty.Entry[]) field.get(property);
		final Map<String, Value> map = new HashMap<>();
		for (final CompositeProperty.Entry entry : entries) {
			map.put(entry.getPrimitivePropertyInfo().getName(), entry.getValue());
		}
		return map;
	}

	private static void assertInvalidShorthand(final ShorthandPropertyInfo info, final String value)
			throws Exception {
		try {
			parseShorthand(info, value);
			fail(info.getName() + ": " + value + " が受理された");
		} catch (final PropertyException e) {
			// 期待どおり
		}
	}

	/** 既定は0 1 auto(§7.2)。 */
	public void testDefaults() {
		assertEquals(0f, ((RealValue) FlexFactor.GROW.getDefault(null)).getReal(), 0);
		assertEquals(1f, ((RealValue) FlexFactor.SHRINK.getDefault(null)).getReal(), 0);
		assertTrue(((FlexBasisValue) FlexBasisProperty.INFO.getDefault(null)).isAuto());
		assertSame(FlexDirectionValue.ROW, FlexDirectionProperty.INFO.getDefault(null));
		assertSame(FlexWrapValue.NOWRAP, FlexWrapProperty.INFO.getDefault(null));
	}

	public void testFactor() throws Exception {
		assertEquals(2.5f, ((RealValue) parse(FlexFactor.GROW, "2.5")).getReal(), 0);
		assertEquals(0f, ((RealValue) parse(FlexFactor.SHRINK, "0")).getReal(), 0);
		assertInvalid(FlexFactor.GROW, "-1");
		assertInvalid(FlexFactor.GROW, "1px");
		assertInvalid(FlexFactor.GROW, "1 2");
	}

	public void testBasis() throws Exception {
		assertTrue(((FlexBasisValue) parse(FlexBasisProperty.INFO, "auto")).isAuto());
		assertTrue(((FlexBasisValue) parse(FlexBasisProperty.INFO, "content")).isContent());
		final FlexBasisValue px = (FlexBasisValue) parse(FlexBasisProperty.INFO, "100px");
		assertFalse(px.isAuto() || px.isContent());
		assertNotNull(px.getSize());
		final FlexBasisValue percent = (FlexBasisValue) parse(FlexBasisProperty.INFO, "50%");
		assertNotNull(percent.getSize());
		// 単位なし0は長さ0として受理(0と0%の等価はF1bの解決層で保証)
		assertNotNull(((FlexBasisValue) parse(FlexBasisProperty.INFO, "0")).getSize());
		assertInvalid(FlexBasisProperty.INFO, "-5px");
		assertInvalid(FlexBasisProperty.INFO, "5");
	}

	public void testDirectionAndWrap() throws Exception {
		assertSame(FlexDirectionValue.COLUMN_REVERSE, parse(FlexDirectionProperty.INFO, "column-reverse"));
		assertSame(FlexWrapValue.WRAP_REVERSE, parse(FlexWrapProperty.INFO, "wrap-reverse"));
		assertInvalid(FlexDirectionProperty.INFO, "diagonal");
		assertInvalid(FlexWrapProperty.INFO, "wrap wrap");
	}

	/** flexショートハンド(§7.1): 省略時はgrow=1・shrink=1・basis=0。 */
	public void testFlexShorthand() throws Exception {
		Map<String, Value> m = parseShorthand(FlexShorthand.INFO, "none");
		assertEquals(0f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(0f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertTrue(((FlexBasisValue) m.get("flex-basis")).isAuto());

		m = parseShorthand(FlexShorthand.INFO, "2");
		assertEquals(2f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(1f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertNotNull(((FlexBasisValue) m.get("flex-basis")).getSize());

		m = parseShorthand(FlexShorthand.INFO, "2 3");
		assertEquals(2f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(3f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertNotNull(((FlexBasisValue) m.get("flex-basis")).getSize());

		m = parseShorthand(FlexShorthand.INFO, "auto");
		assertEquals(1f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(1f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertTrue(((FlexBasisValue) m.get("flex-basis")).isAuto());

		m = parseShorthand(FlexShorthand.INFO, "200px");
		assertEquals(1f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertNotNull(((FlexBasisValue) m.get("flex-basis")).getSize());

		m = parseShorthand(FlexShorthand.INFO, "30% 2 3");
		assertEquals(2f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(3f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertNotNull(((FlexBasisValue) m.get("flex-basis")).getSize());

		// 2因子の後の単位なし0はbasis 0
		m = parseShorthand(FlexShorthand.INFO, "0 0 0");
		assertEquals(0f, ((RealValue) m.get("flex-grow")).getReal(), 0);
		assertEquals(0f, ((RealValue) m.get("flex-shrink")).getReal(), 0);
		assertNotNull(((FlexBasisValue) m.get("flex-basis")).getSize());

		assertInvalidShorthand(FlexShorthand.INFO, "2 3 4");
		assertInvalidShorthand(FlexShorthand.INFO, "none 2");
		assertInvalidShorthand(FlexShorthand.INFO, "-1");
		assertInvalidShorthand(FlexShorthand.INFO, "auto auto");
	}

	public void testFlexFlowShorthand() throws Exception {
		Map<String, Value> m = parseShorthand(FlexFlowShorthand.INFO, "column wrap");
		assertSame(FlexDirectionValue.COLUMN, m.get("flex-direction"));
		assertSame(FlexWrapValue.WRAP, m.get("flex-wrap"));

		m = parseShorthand(FlexFlowShorthand.INFO, "wrap-reverse row-reverse");
		assertSame(FlexDirectionValue.ROW_REVERSE, m.get("flex-direction"));
		assertSame(FlexWrapValue.WRAP_REVERSE, m.get("flex-wrap"));

		m = parseShorthand(FlexFlowShorthand.INFO, "wrap");
		assertSame(FlexDirectionValue.ROW, m.get("flex-direction"));
		assertSame(FlexWrapValue.WRAP, m.get("flex-wrap"));

		assertInvalidShorthand(FlexFlowShorthand.INFO, "row row");
		assertInvalidShorthand(FlexFlowShorthand.INFO, "diagonal");
	}
}
