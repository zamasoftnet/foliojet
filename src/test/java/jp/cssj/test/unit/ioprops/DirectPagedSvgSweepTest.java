package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 多数の文書をBatik版と独自版の両方で組んで、同じものが出るか掃きます。
 *
 * <p>
 * 1つの実書籍で通っても、そこに出てこない書き方——影、勾配、変換の入れ子、
 * 縦横の混在、表、浮動、画像の各種——では違うかもしれません。だから
 * `files/unittest`の文書を機械的に回します。
 * </p>
 *
 * <p>
 * 見るのは「同じ資源構成で、同じ文字を、同じ位置に置いているか」です。
 * <b>座標まで突き合わせます</b>。資源の並びやページ数だけでは、
 * 図形や画像が別の大きさで出ていても気づけません。
 * </p>
 */
public class DirectPagedSvgSweepTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 1回あたり数秒かかるので、通しで回せる数に絞ります。
	 * 広げたいときは{@code -Dfoliojet.pagedSvgSweepLimit=200}。
	 * 接頭辞が{@code foliojet.}でないとbuild.gradleが試験のJVMへ渡しません。
	 */
	private static final int LIMIT = Integer.getInteger("foliojet.pagedSvgSweepLimit", 60);

	public void testTheTwoWritersAgreeAcrossManyDocuments() throws Exception {
		final List<Path> documents = documents();
		assertFalse("no test document was found", documents.isEmpty());

		final List<String> failures = new ArrayList<>();
		int compared = 0;
		for (final Path document : documents) {
			final Map<String, byte[]> batik;
			final Map<String, byte[]> direct;
			try {
				batik = run(document, "batik");
				direct = run(document, "direct");
			} catch (final Exception e) {
				// 組めない文書は両方式に共通の問題なので、ここでは扱わない
				continue;
			}
			++compared;
			final String problem = compare(batik, direct);
			if (problem != null) {
				failures.add(document.getFileName() + ": " + problem);
			}
		}

		assertTrue("too few documents could be compared: " + compared, compared >= LIMIT / 2);
		assertEquals("the two writers must agree (" + compared + " documents compared): " + failures,
				List.of(), failures);
	}

	/** 両方式の出力を突き合わせ、食い違いを1行で返します。合っていればnull。 */
	private static String compare(final Map<String, byte[]> batik, final Map<String, byte[]> direct) {
		// 画像の資源URIは内容ハッシュなので、PNGの符号化が違えば名前も変わる。
		// 符号化は実装の都合で、守るべきはそこに写っている画素のほう。
		// だから画像だけはURIを外して、枚数と画素で見る
		if (!withoutImages(batik.keySet()).equals(withoutImages(direct.keySet()))) {
			return "the set of result URIs differs: " + withoutImages(batik.keySet()) + " vs "
					+ withoutImages(direct.keySet());
		}
		final String assets = compareImageAssets(batik, direct);
		if (assets != null) {
			return assets;
		}
		for (final String uri : batik.keySet()) {
			if (!uri.endsWith(".svg")) {
				continue;
			}
			final org.w3c.dom.Document a;
			final org.w3c.dom.Document b;
			try {
				a = parse(batik.get(uri));
				b = parse(direct.get(uri));
			} catch (final Exception e) {
				return uri + " is not well-formed XML: " + e;
			}
			final String text = compareTexts(uri, a, b);
			if (text != null) {
				return text;
			}
			final String image = compareImages(uri, a, b);
			if (image != null) {
				return image;
			}
			final String pattern = comparePatterns(uri, a, b);
			if (pattern != null) {
				return pattern;
			}
		}
		return null;
	}

	private static java.util.Set<String> withoutImages(final java.util.Set<String> uris) {
		final java.util.Set<String> out = new java.util.TreeSet<>();
		for (final String uri : uris) {
			if (!uri.startsWith("assets/images/")) {
				out.add(uri);
			}
		}
		return out;
	}

	/**
	 * 共有画像を枚数と縦横比で突き合わせます。
	 *
	 * <p>
	 * <b>画素数までは揃えません。</b> 敷き詰めの絵で、Batik版は升目の大きさ
	 * (pt)に合わせて縮めた画素を出し、独自版は元の画素をそのまま出して升目へ
	 * 収めます。見え方は同じで、独自版のほうが元の解像度を落としません。
	 * 見た目を決めるのは升目の大きさのほうなので、それは
	 * {@link #comparePatterns}で別に見ます。
	 * </p>
	 */
	private static String compareImageAssets(final Map<String, byte[]> batik, final Map<String, byte[]> direct) {
		final List<java.awt.image.BufferedImage> a = images(batik);
		final List<java.awt.image.BufferedImage> b = images(direct);
		if (a.size() != b.size()) {
			return "image asset count " + a.size() + " vs " + b.size();
		}
		for (int i = 0; i < a.size(); ++i) {
			final java.awt.image.BufferedImage x = a.get(i);
			final java.awt.image.BufferedImage y = b.get(i);
			if (x == null || y == null) {
				continue;
			}
			final double ra = (double) x.getWidth() / x.getHeight();
			final double rb = (double) y.getWidth() / y.getHeight();
			if (Math.abs(ra - rb) > 0.02) {
				return "image " + i + " aspect " + x.getWidth() + "x" + x.getHeight() + " vs " + y.getWidth() + "x"
						+ y.getHeight();
			}
		}
		return null;
	}

	/** 敷き詰めの升目が同じ大きさであること。見え方を決めるのはここ。 */
	private static String comparePatterns(final String uri, final org.w3c.dom.Document a,
			final org.w3c.dom.Document b) {
		final org.w3c.dom.NodeList pa = a.getElementsByTagName("pattern");
		final org.w3c.dom.NodeList pb = b.getElementsByTagName("pattern");
		if (pa.getLength() != pb.getLength()) {
			return uri + ": pattern count " + pa.getLength() + " vs " + pb.getLength();
		}
		for (int i = 0; i < pa.getLength(); ++i) {
			for (final String attr : new String[] { "width", "height" }) {
				final double va = number(((org.w3c.dom.Element) pa.item(i)).getAttribute(attr));
				final double vb = number(((org.w3c.dom.Element) pb.item(i)).getAttribute(attr));
				if (Double.isNaN(va) || Double.isNaN(vb) || Math.abs(va - vb) > 0.5) {
					return uri + ": pattern " + i + " " + attr + " is " + va + " vs " + vb;
				}
			}
		}
		return null;
	}

	private static List<java.awt.image.BufferedImage> images(final Map<String, byte[]> results) {
		final List<java.awt.image.BufferedImage> out = new ArrayList<>();
		for (final Map.Entry<String, byte[]> e : results.entrySet()) {
			if (!e.getKey().startsWith("assets/images/")) {
				continue;
			}
			try {
				out.add(javax.imageio.ImageIO.read(new ByteArrayInputStream(e.getValue())));
			} catch (final IOException ex) {
				out.add(null);
			}
		}
		out.sort(Comparator.comparingInt(i -> i == null ? -1 : i.getWidth() * 100000 + i.getHeight()));
		return out;
	}

	/** 文字列と、それを置く位置が同じであること。 */
	private static String compareTexts(final String uri, final org.w3c.dom.Document a,
			final org.w3c.dom.Document b) {
		final org.w3c.dom.NodeList ta = a.getElementsByTagName("text");
		final org.w3c.dom.NodeList tb = b.getElementsByTagName("text");
		if (ta.getLength() != tb.getLength()) {
			return uri + ": text count " + ta.getLength() + " vs " + tb.getLength();
		}
		for (int i = 0; i < ta.getLength(); ++i) {
			final org.w3c.dom.Element ea = (org.w3c.dom.Element) ta.item(i);
			final org.w3c.dom.Element eb = (org.w3c.dom.Element) tb.item(i);
			final String sa = ea.getAttribute("data-copper-text");
			final String sb = eb.getAttribute("data-copper-text");
			if (!sa.equals(sb)) {
				return uri + ": text " + i + " is \"" + sa + "\" vs \"" + sb + "\"";
			}
			for (final String attr : new String[] { "x", "y" }) {
				final String problem = compareNumberLists(uri + ": text " + i + " " + attr,
						ea.getAttribute(attr), eb.getAttribute(attr));
				if (problem != null) {
					return problem;
				}
			}
		}
		return null;
	}

	/**
	 * 画像が同じ資源を、同じ大きさで置いていること。
	 *
	 * <p>
	 * 大きさは自前で畳んで比べます。片方が{@code <g transform>}で、もう片方が
	 * 要素自身のtransformで表していても、実際に占める寸法は同じはずです。
	 * </p>
	 */
	private static String compareImages(final String uri, final org.w3c.dom.Document a,
			final org.w3c.dom.Document b) {
		final org.w3c.dom.NodeList ia = a.getElementsByTagName("image");
		final org.w3c.dom.NodeList ib = b.getElementsByTagName("image");
		if (ia.getLength() != ib.getLength()) {
			return uri + ": image count " + ia.getLength() + " vs " + ib.getLength();
		}
		for (int i = 0; i < ia.getLength(); ++i) {
			final double[] sa = drawnSize((org.w3c.dom.Element) ia.item(i));
			final double[] sb = drawnSize((org.w3c.dom.Element) ib.item(i));
			if (sa == null || sb == null) {
				continue;
			}
			// 1ptより細かい差は座標の丸め
			if (Math.abs(sa[0] - sb[0]) > 1.0 || Math.abs(sa[1] - sb[1]) > 1.0) {
				return uri + ": image " + i + " is drawn " + sa[0] + "x" + sa[1] + " vs " + sb[0] + "x" + sb[1];
			}
		}
		return null;
	}

	/** 祖先のtransformまで畳んで、実際に占める幅と高さを返します。 */
	private static double[] drawnSize(final org.w3c.dom.Element image) {
		final double w0 = number(image.getAttribute("width"));
		final double h0 = number(image.getAttribute("height"));
		if (Double.isNaN(w0) || Double.isNaN(h0)) {
			return null;
		}
		double w = w0;
		double h = h0;
		for (org.w3c.dom.Node n = image; n instanceof org.w3c.dom.Element e; n = n.getParentNode()) {
			final double[] s = scaleOf(e.getAttribute("transform"));
			w *= s[0];
			h *= s[1];
		}
		return new double[] { Math.abs(w), Math.abs(h) };
	}

	private static double number(final String value) {
		if (value == null || value.isEmpty()) {
			return Double.NaN;
		}
		try {
			return Double.parseDouble(value.replaceAll("[a-z%]+$", ""));
		} catch (final NumberFormatException e) {
			return Double.NaN;
		}
	}

	private static double[] scaleOf(final String transform) {
		if (transform == null || transform.isEmpty()) {
			return new double[] { 1, 1 };
		}
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(matrix|scale)\\s*\\(([^)]*)\\)").matcher(transform);
		double sx = 1;
		double sy = 1;
		while (m.find()) {
			final String[] parts = m.group(2).trim().split("[\\s,]+");
			try {
				if ("matrix".equals(m.group(1)) && parts.length >= 4) {
					sx *= Double.parseDouble(parts[0]);
					sy *= Double.parseDouble(parts[3]);
				} else if ("scale".equals(m.group(1)) && parts.length >= 1) {
					sx *= Double.parseDouble(parts[0]);
					sy *= Double.parseDouble(parts.length >= 2 ? parts[1] : parts[0]);
				}
			} catch (final NumberFormatException e) {
				return new double[] { 1, 1 };
			}
		}
		return new double[] { sx, sy };
	}

	private static String compareNumberLists(final String what, final String a, final String b) {
		final String[] pa = a.trim().isEmpty() ? new String[0] : a.trim().split("[\\s,]+");
		final String[] pb = b.trim().isEmpty() ? new String[0] : b.trim().split("[\\s,]+");
		if (pa.length != pb.length) {
			return what + ": " + pa.length + " values vs " + pb.length;
		}
		for (int i = 0; i < pa.length; ++i) {
			final double va = number(pa[i]);
			final double vb = number(pb[i]);
			if (Double.isNaN(va) || Double.isNaN(vb)) {
				if (!pa[i].equals(pb[i])) {
					return what + "[" + i + "]: " + pa[i] + " vs " + pb[i];
				}
			} else if (Math.abs(va - vb) > 0.01) {
				return what + "[" + i + "]: " + va + " vs " + vb;
			}
		}
		return null;
	}

	private static org.w3c.dom.Document parse(final byte[] bytes) throws Exception {
		final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
		// Batik版はSVG 1.0のDTDを指すDOCTYPEを付ける。取りに行くとw3.orgへ
		// 毎回問い合わせることになり、実際429で撥ねられる。読まない
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
	}

	/** 掃引する文書。毎回同じ並びになるよう名前で整列します。 */
	private static List<Path> documents() throws IOException {
		final Path root = Path.of("files", "unittest");
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(Files::isRegularFile)
					.filter(p -> {
						final String n = p.getFileName().toString();
						return n.endsWith(".html") || n.endsWith(".xhtml");
					})
					.sorted(Comparator.comparing(Path::toString))
					.limit(LIMIT)
					.toList();
		}
	}

	private Map<String, byte[]> run(final Path document, final String writer) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.writer", writer);
			final File file = document.toFile();
			CTISessionHelper.transcodeStream(session, new java.io.FileInputStream(file), file.toURI(),
					document.getFileName().toString().endsWith(".xhtml") ? "application/xhtml+xml" : "text/html",
					null);
		} finally {
			session.close();
		}
		return results.data;
	}

	private static final class CapturingResults implements Results {
		final Map<String, byte[]> data = new LinkedHashMap<>();
		private final Map<String, ByteArrayOutputStream> streams = new LinkedHashMap<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.streams.put(uri, out);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			this.streams.forEach((uri, out) -> this.data.put(uri, out.toByteArray()));
		}
	}
}
