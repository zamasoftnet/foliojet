package jp.cssj.test.unit._0510_text_spacing;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.IntFunction;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.GlyphBounds;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.*;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;

final class InkGapTestSupport {
	static FontStyle style(final double size, final FontStyle.Direction direction) {
		return new FontStyleImpl(FontFamilyList.SERIF, size, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				direction, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
	}

	static FontMetricsImpl realMetrics(final String file, final FontStyle style) throws Exception {
		final var source = new OpenTypeCIDIdentityFontSource(
				new File("src/test/resources/conf/profiles/fonts/truetype/" + file), 0, style.getDirection());
		return new FontMetricsImpl(s -> s.createFont(), source, style);
	}

	/** FontMetricsImpl経由で輪郭を測る。送り・縦原点・GPOSを独立に制御する。 */
	static FontMetricsImpl metrics(final FontStyle style, final FontStyle.Direction sourceDirection,
			IntFunction<GlyphBounds> bounds, int origin, int placement, int kern) {
		final FontSource source = (FontSource) Proxy.newProxyInstance(FontSource.class.getClassLoader(),
				new Class<?>[] { FontSource.class }, (p, method, args) -> switch (method.getName()) {
				case "getDirection" -> sourceDirection;
				case "getWeight" -> FontStyle.Weight.W_400;
				case "isItalic" -> false;
				case "canDisplay" -> true;
				case "getAscent" -> (short) 880;
				case "getDescent" -> (short) 120;
				case "getXHeight", "getSpaceAdvance" -> (short) 500;
				case "getFontName", "toString" -> "InkGapControlled";
				default -> throw new AssertionError(method);
			});
		final ShapedFont font = (ShapedFont) Proxy.newProxyInstance(ShapedFont.class.getClassLoader(),
				new Class<?>[] { ShapedFont.class }, (p, method, args) -> switch (method.getName()) {
				case "getFontSource" -> source;
				case "getGlyphBounds" -> bounds.apply((int) args[0]);
				case "getAdvance", "getWidth" -> (short) 1000;
				case "getVerticalOrigin" -> (short) origin;
				case "getPlacementAdjustment" -> (short) placement;
				case "getAdvanceAdjustment" -> (short) 0;
				case "getKerning" -> (short) kern;
				case "toGID" -> (int) args[0];
				case "toString" -> "InkGapControlled";
				default -> throw new AssertionError(method);
			});
		return new FontMetricsImpl(s -> font, source, style);
	}

	static TextImpl text(final FontStyle style, final FontMetricsImpl metrics, final String chars) {
		final var text = new TextImpl(0, style, metrics);
		final char[] ch = chars.toCharArray();
		for (int i = 0; i < ch.length; ++i) {
			text.appendGlyph(ch, i, (byte) 1, metrics.getFont().toGID(ch[i]));
		}
		return text;
	}

	static Field field(Class<?> type, final String name) throws Exception {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				final Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (final NoSuchFieldException e) {
				// 観測対象の field は基底の box/builder に宣言されていることがあるので親へ遡る
			}
		}
		throw new NoSuchFieldException(name);
	}

	static Object get(final Object target, final String name) throws Exception {
		return field(target.getClass(), name).get(target);
	}

	static void set(final Object target, final String name, final Object value) throws Exception {
		field(target.getClass(), name).set(target, value);
	}

	static Object call(final Object target, final String name, Class<?>[] types, final Object... args) throws Exception {
		final Class<?> type = target instanceof Class<?> c ? c : target.getClass();
		final Method method = type.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method.invoke(target instanceof Class<?> ? null : target, args);
	}

	private InkGapTestSupport() { }
}
