package jp.cssj.test.unit.ioprops;

import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;

import junit.framework.TestCase;

/**
 * Batikを介さないSVG書き出しの単体試験です。
 *
 * <p>
 * 対象はパッケージ内のクラスなので、反射で呼びます。<b>出力がXMLとして
 * 妥当であること</b>と、<b>パス文法が規格どおりであること</b>を見ます。
 * </p>
 */
public class DirectSVGWriterTest extends TestCase {
	private static final String PKG = "net.zamasoft.foliojet.ua.impl.pagedsvg.";

	private static String pathData(final java.awt.Shape shape, final AffineTransform at) throws Exception {
		final Class<?> c = Class.forName(PKG + "SVGPathWriter");
		final Method m = c.getDeclaredMethod("toPathData", java.awt.Shape.class, AffineTransform.class);
		m.setAccessible(true);
		return (String) m.invoke(null, shape, at);
	}

	private static String number(final double v) throws Exception {
		final Class<?> c = Class.forName(PKG + "SVGWriter");
		final Method m = c.getDeclaredMethod("number", double.class);
		m.setAccessible(true);
		return (String) m.invoke(null, v);
	}

	/** 矩形はM/L/Zで表され、座標がそのまま出ること。 */
	public void testRectanglePath() throws Exception {
		final String d = pathData(new Rectangle2D.Double(10, 20, 30, 40), null);
		assertTrue("must start with a moveto: " + d, d.startsWith("M"));
		assertTrue("must be closed: " + d, d.endsWith("Z"));
		// 4隅の座標が現れる
		for (final String v : new String[] { "10", "20", "40", "60" }) {
			assertTrue(v + " expected in " + d, d.contains(v));
		}
	}

	/** 変換は座標へ畳み込む。transform属性に頼らないので閲覧側の実装差が出ない。 */
	public void testTransformIsFoldedIntoCoordinates() throws Exception {
		final AffineTransform at = AffineTransform.getTranslateInstance(100, 200);
		final String d = pathData(new Rectangle2D.Double(0, 0, 10, 10), at);
		assertTrue("translated coordinates expected: " + d, d.contains("100") && d.contains("200"));
	}

	/** 曲線はC(3次)で出る。楕円はJava2Dが3次ベジエへ分解する。 */
	public void testCurveUsesCubicCommand() throws Exception {
		final String d = pathData(new Ellipse2D.Double(0, 0, 10, 10), null);
		assertTrue("a cubic command is expected: " + d, d.indexOf('C') >= 0);
	}

	/** 同じコマンドが続くなら文字を省ける。省いても文法上正しい。 */
	public void testRepeatedCommandLetterIsOmitted() throws Exception {
		final Path2D.Double p = new Path2D.Double();
		p.moveTo(0, 0);
		p.lineTo(1, 0);
		p.lineTo(2, 0);
		p.lineTo(3, 0);
		final String d = pathData(p, null);
		int letters = 0;
		for (int i = 0; i < d.length(); ++i) {
			if (d.charAt(i) == 'L') {
				++letters;
			}
		}
		// moveto に続く座標対は暗黙の lineto なので、この形ならLは1つも要らない
		assertEquals("the L command letter must not repeat: " + d, 0, letters);
		// コマンド文字の数だけ見ても足りない。読み戻して図形が変わっていないこと
		assertSameShape(p, d);
	}

	/**
	 * <b>書いたものを読み戻すと元の図形に戻ること。</b>
	 *
	 * <p>
	 * コマンド文字や区切りを省く最適化は、1文字落とすだけで座標が繋がって
	 * 黙って別の図形になる。しかもXMLとしては妥当なままなので、整形式の検査や
	 * 「Lがいくつあるか」では捕まらない。ここで実際に読み直して確かめる。
	 * </p>
	 */
	public void testPathDataRoundTrips() throws Exception {
		final Path2D.Double zigzag = new Path2D.Double();
		zigzag.moveTo(0, 0);
		zigzag.lineTo(300.472441, 0);
		zigzag.lineTo(300.472441, 490.393701);
		zigzag.lineTo(0, 490.393701);
		zigzag.closePath();

		final Path2D.Double mixed = new Path2D.Double();
		mixed.moveTo(-1.5, 2);
		mixed.curveTo(3, -4, 5, 6, -7, 8);
		mixed.curveTo(9, 10, -11, 12, 13, -14);
		mixed.quadTo(1, 2, 3, 4);
		mixed.quadTo(-5, 6, 7, -8);
		mixed.lineTo(0, 0);
		mixed.closePath();
		mixed.moveTo(20, 30);
		mixed.lineTo(40, 50);

		final java.awt.Shape[] shapes = { new Rectangle2D.Double(10, 20, 30, 40), new Ellipse2D.Double(0, 0, 10, 10),
				zigzag, mixed };
		final AffineTransform[] transforms = { null, AffineTransform.getTranslateInstance(100, 200),
				AffineTransform.getScaleInstance(1.3, -0.7) };
		for (final java.awt.Shape shape : shapes) {
			for (final AffineTransform at : transforms) {
				assertSameShape(at == null ? shape : at.createTransformedShape(shape), pathData(shape, at));
			}
		}
	}

	/** {@code d}を読み戻し、元の図形と1区間ずつ突き合わせます。 */
	private static void assertSameShape(final java.awt.Shape expected, final String d) {
		final double[] want = new double[6];
		final double[] got = new double[6];
		final java.awt.geom.PathIterator e = expected.getPathIterator(null);
		final java.awt.geom.PathIterator a = parse(d).getPathIterator(null);
		int n = 0;
		for (; !e.isDone() && !a.isDone(); e.next(), a.next(), ++n) {
			final int et = e.currentSegment(want);
			final int at = a.currentSegment(got);
			assertEquals("segment " + n + " type differs in " + d, et, at);
			final int count = switch (et) {
			case java.awt.geom.PathIterator.SEG_CLOSE -> 0;
			case java.awt.geom.PathIterator.SEG_QUADTO -> 4;
			case java.awt.geom.PathIterator.SEG_CUBICTO -> 6;
			default -> 2;
			};
			for (int i = 0; i < count; ++i) {
				assertEquals("segment " + n + " coordinate " + i + " differs in " + d, want[i], got[i], 1e-6);
			}
		}
		assertTrue("the path data has too few segments: " + d, e.isDone());
		assertTrue("the path data has too many segments: " + d, a.isDone());
		assertTrue("no segment was produced: " + d, n > 0);
	}

	/**
	 * 出しているのはM/L/Q/C/Zの絶対座標だけなので、それだけを読みます。
	 * 規格どおり、コマンド文字の省略と、負号が区切りを兼ねる書き方を受け付けます。
	 */
	private static Path2D.Double parse(final String d) {
		final Path2D.Double path = new Path2D.Double();
		final int len = d.length();
		final double[] c = new double[6];
		int i = 0;
		char command = 0;
		while (i < len) {
			final char ch = d.charAt(i);
			if (ch == ' ' || ch == ',') {
				++i;
				continue;
			}
			if (ch == 'M' || ch == 'L' || ch == 'Q' || ch == 'C' || ch == 'Z') {
				command = ch;
				++i;
				if (ch == 'Z') {
					path.closePath();
					command = 0;
				}
				continue;
			}
			// 数字から始まるならコマンドの繰り返し。ただしMの繰り返しはL
			assertTrue("a number cannot appear before any command: " + d, command != 0);
			final int count = switch (command) {
			case 'Q' -> 4;
			case 'C' -> 6;
			default -> 2;
			};
			for (int k = 0; k < count; ++k) {
				while (i < len && (d.charAt(i) == ' ' || d.charAt(i) == ',')) {
					++i;
				}
				final int start = i;
				if (i < len && (d.charAt(i) == '-' || d.charAt(i) == '+')) {
					++i;
				}
				while (i < len && ((d.charAt(i) >= '0' && d.charAt(i) <= '9') || d.charAt(i) == '.')) {
					++i;
				}
				assertTrue("a number was expected at " + start + " in " + d, i > start);
				c[k] = Double.parseDouble(d.substring(start, i));
			}
			switch (command) {
			case 'M' -> {
				path.moveTo(c[0], c[1]);
				// 規格上、Mに続く座標対はLとして扱う
				command = 'L';
			}
			case 'L' -> path.lineTo(c[0], c[1]);
			case 'Q' -> path.quadTo(c[0], c[1], c[2], c[3]);
			case 'C' -> path.curveTo(c[0], c[1], c[2], c[3], c[4], c[5]);
			default -> fail("unexpected command " + command);
			}
		}
		return path;
	}

	/**
	 * 数値に指数表記を使わないこと。SVGの文法では許されるが、読めない実装がある。
	 */
	public void testNumbersNeverUseExponentNotation() throws Exception {
		for (final double v : new double[] { 0.0000001, 1e-9, 12345678901234.0, -0.000005 }) {
			final String s = number(v);
			assertFalse("exponent notation must not appear: " + v + " -> " + s,
					s.contains("e") || s.contains("E"));
		}
	}

	/** 整数はそのまま、末尾の0は落とすこと。無駄な桁は文書を膨らませる。 */
	public void testNumbersAreCompact() throws Exception {
		assertEquals("10", number(10.0));
		assertEquals("-3", number(-3.0));
		assertEquals("0", number(0.0));
		assertEquals("1.5", number(1.5));
	}

	/** 属性値のエスケープ。&lt;と&amp;と引用符が素通りしないこと。 */
	public void testAttributeEscaping() throws Exception {
		final Class<?> c = Class.forName(PKG + "SVGWriter");
		final Method m = c.getDeclaredMethod("escapeAttribute", java.io.Writer.class, String.class);
		m.setAccessible(true);
		final StringWriter out = new StringWriter();
		m.invoke(null, out, "a<b>c&d\"e");
		assertEquals("a&lt;b&gt;c&amp;d&quot;e", out.toString());
	}

	/** 生成したSVGがXMLとして妥当であること。 */
	public void testGeneratedDocumentIsWellFormed() throws Exception {
		final String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"100\" height=\"50\""
				+ " viewBox=\"0 0 100 50\"><path d=\"" + pathData(new Rectangle2D.Double(1, 2, 3, 4), null)
				+ "\" fill=\"#112233\"/></svg>";
		javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}
}
