package net.zamasoft.foliojet.css.impl.property.container;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.shorthand.ContainerShorthand;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.ContainerTypeValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code @container}実装・段2の解析テストです
 * (docs/history/2026-08-15-container-queries-design.md §5/§6)。
 * {@code container-type}/{@code container-name}/{@code container}
 * ショートハンドの受理・拒否と既定値を固定する(FlexParserTestと同方針、
 * カスケードそのものはstyle文脈が要るためRaw中間形の受理までを対象とする)。
 */
public class ContainerParserTest extends TestCase {

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
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(ContainerParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "ContainerParserTest.UserAgent";
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

	private static String[] names(final Value value) {
		if (value == KeywordValue.NONE) {
			return new String[0];
		}
		final Value[] values = ((ValueListValue) value).getValues();
		final String[] result = new String[values.length];
		for (int i = 0; i < values.length; ++i) {
			result[i] = ((StringValue) values[i]).getString();
		}
		return result;
	}

	/** 既定はnormal/none(未対応時のフォールバック挙動と一致)。 */
	public void testDefaults() {
		assertSame(ContainerTypeValue.NORMAL_VALUE, ContainerType.INFO.getDefault(null));
		assertSame(KeywordValue.NONE, ContainerName.INFO.getDefault(null));
	}

	public void testContainerType() throws Exception {
		assertSame(ContainerTypeValue.NORMAL_VALUE, parse(ContainerType.INFO, "normal"));
		assertSame(ContainerTypeValue.INLINE_SIZE_VALUE, parse(ContainerType.INFO, "inline-size"));
		assertSame(ContainerTypeValue.SIZE_VALUE, parse(ContainerType.INFO, "size"));
		assertInvalid(ContainerType.INFO, "block-size");
		assertInvalid(ContainerType.INFO, "inline-size normal");
		assertInvalid(ContainerType.INFO, "42");
	}

	public void testContainerName() throws Exception {
		assertSame(KeywordValue.NONE, parse(ContainerName.INFO, "none"));
		assertEquals(List.of("sidebar"), List.of(names(parse(ContainerName.INFO, "sidebar"))));
		assertEquals(List.of("a", "b", "c"), List.of(names(parse(ContainerName.INFO, "a b c"))));
		// noneは他の名前と共存できない(css-contain-3の<custom-ident>から除外)
		assertInvalid(ContainerName.INFO, "a none");
		assertInvalid(ContainerName.INFO, "none a");
		assertInvalid(ContainerName.INFO, "42");
	}

	/** container: <name> [/ <type>]?——typeを省略するとnormal。 */
	public void testContainerShorthand() throws Exception {
		Map<String, Value> m = parseShorthand(ContainerShorthand.INFO, "sidebar");
		assertEquals(List.of("sidebar"), List.of(names(m.get("container-name"))));
		assertSame(ContainerTypeValue.NORMAL_VALUE, m.get("container-type"));

		m = parseShorthand(ContainerShorthand.INFO, "sidebar / inline-size");
		assertEquals(List.of("sidebar"), List.of(names(m.get("container-name"))));
		assertSame(ContainerTypeValue.INLINE_SIZE_VALUE, m.get("container-type"));

		m = parseShorthand(ContainerShorthand.INFO, "card gallery / size");
		assertEquals(List.of("card", "gallery"), List.of(names(m.get("container-name"))));
		assertSame(ContainerTypeValue.SIZE_VALUE, m.get("container-type"));

		m = parseShorthand(ContainerShorthand.INFO, "none");
		assertEquals(List.of(), List.of(names(m.get("container-name"))));
		assertSame(ContainerTypeValue.NORMAL_VALUE, m.get("container-type"));

		assertInvalidShorthand(ContainerShorthand.INFO, "sidebar / block-size");
		assertInvalidShorthand(ContainerShorthand.INFO, "none / inline-size extra");
		assertInvalidShorthand(ContainerShorthand.INFO, "sidebar / inline-size / inline-size");
	}
}
