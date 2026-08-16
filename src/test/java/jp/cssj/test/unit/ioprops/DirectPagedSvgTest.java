package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Batikを介さないPaged SVG書き出しの試験です。
 *
 * <p>
 * 要は<b>従来と同じものが出ること</b>で、そこを両方式の突き合わせで見ます。
 * 座標の丸めなど細部は書き手によって変わりうるので、バイト一致ではなく
 * 「同じ資源を参照し、同じ文字を保ち、同じページ数で、XMLとして妥当」を
 * 見ています。
 * </p>
 */
public class DirectPagedSvgTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PNG =
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

	private static String html() {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:120pt 90pt;margin:8pt}body{margin:0;font-size:11pt}"
				+ "h1{font-size:11pt;margin:0}"
				+ ".box{width:40pt;height:12pt;background:#3366cc;border:1pt solid #000}"
				+ ".next{page-break-before:always}"
				+ "</style></head><body>"
				+ "<h1 id=\"top\"><a href=\"#second\">第一頁 ABC</a></h1>"
				+ "<div class=\"box\"></div>"
				// 大きさを与える。1画素のまま描くと寸法の取り違えが誤差に埋もれる
				+ "<img src=\"data:image/png;base64," + PNG + "\" style=\"width:40pt;height:30pt\">"
				+ "<div id=\"second\" class=\"next\"><p>第二頁 XYZ</p></div>"
				+ "</body></html>";
	}

	/** 独自書き出しでもページ・資源・目次が従来と同じ形で出ること。 */
	public void testDirectWriterMatchesBatik() throws Exception {
		final CapturingResults batik = run(Map.of());
		final CapturingResults direct = run(Map.of("output.paged-svg.writer", "direct"));

		assertEquals("page count must match", pageCount(batik), pageCount(direct));
		assertTrue("at least two pages are expected", pageCount(direct) >= 2);

		// 出力するファイルの並びが同じであること
		assertEquals("the set of result URIs must match", batik.order, direct.order);

		for (int i = 1; i <= pageCount(direct); ++i) {
			final String name = String.format("pages/%04d.svg", i);
			final byte[] svg = direct.data.get(name).toByteArray();
			assertWellFormedXml(name, svg);
			final String text = new String(svg, StandardCharsets.UTF_8);
			assertTrue(name + " must declare the SVG namespace",
					text.contains("http://www.w3.org/2000/svg"));
			assertTrue(name + " must carry a viewBox", text.contains("viewBox="));
		}
	}

	/** 文字がテキストとして残り、共有WOFF2を参照すること。 */
	public void testTextIsPreserved() throws Exception {
		final CapturingResults direct = run(Map.of("output.paged-svg.writer", "direct"));
		final String first = direct.text("pages/0001.svg");
		assertTrue("text must stay as <text>", first.contains("<text"));
		assertTrue("the original characters must be recoverable",
				first.contains("data-copper-text="));
		assertTrue("the shared subset must be referenced", first.contains("../assets/fonts/"));
		assertTrue("the font-face rule must be emitted", first.contains("@font-face"));
		assertTrue("page data must keep the source text",
				direct.text("pages/0001.json").contains("第一頁"));
	}

	/** 図形がpathで出て、塗りと線が付くこと。 */
	public void testShapesBecomePaths() throws Exception {
		final CapturingResults direct = run(Map.of("output.paged-svg.writer", "direct"));
		final String first = direct.text("pages/0001.svg");
		assertTrue("shapes must be emitted as <path>", first.contains("<path"));
		assertTrue("a fill must be present", first.contains("fill=\"#"));
		assertTrue("the box border must be stroked", first.contains("stroke=\"#"));
	}

	/** 画像が共有資源として外に出て、data:が本文へ埋まらないこと。 */
	public void testImagesAreExternalised() throws Exception {
		final CapturingResults direct = run(Map.of("output.paged-svg.writer", "direct"));
		final String first = direct.text("pages/0001.svg");
		assertTrue("the image must reference a shared asset", first.contains("../assets/images/"));
		assertFalse("no data: URI may be inlined", first.contains("data:image/"));
		assertTrue("the asset itself must be emitted",
				direct.order.stream().anyMatch(u -> u.startsWith("assets/images/")));
	}

	/**
	 * 埋め込みにすると、画像がページSVGの中へ入り、別ファイルとして出ないこと。
	 * ディレクトリへ出せない送り方のための指定。
	 */
	public void testEmbeddedResources() throws Exception {
		for (final String writer : new String[] { "batik", "direct" }) {
			final CapturingResults r = run(Map.of("output.paged-svg.writer", writer,
					"output.paged-svg.resources", "embed"));
			final String first = r.text("pages/0001.svg");
			assertTrue(writer + ": the image must be inlined", first.contains("data:image/"));
			assertFalse(writer + ": no shared image file may be emitted",
					r.order.stream().anyMatch(u -> u.startsWith("assets/images/")));
			assertWellFormedXml("pages/0001.svg", r.data.get("pages/0001.svg").toByteArray());
		}
	}

	/** 既定は参照。同じ画像の実体は1つで済む。 */
	public void testReferencedResourcesAreDefault() throws Exception {
		final CapturingResults r = run(Map.of("output.paged-svg.writer", "direct"));
		final String first = r.text("pages/0001.svg");
		assertTrue("the default must reference a shared file", first.contains("../assets/images/"));
		assertFalse("nothing may be inlined by default", first.contains("data:image/"));
	}

	/**
	 * <b>画像が両方式で同じ大きさに描かれること。</b>
	 *
	 * <p>
	 * 画像は「自分の論理寸法の升目」へ描かれる約束で、単位矩形でも画素数でも
	 * ない。ここを取り違えても参照先も整形式も正しいままなので、他の検査は
	 * すべて素通りする。実際に描かれる寸法まで見て初めて捕まる。
	 * </p>
	 */
	public void testImageIsDrawnAtTheSameSizeAsBatik() throws Exception {
		final double[] batik = imageBox(run(Map.of()).data.get("pages/0001.svg").toByteArray());
		final double[] direct = imageBox(
				run(Map.of("output.paged-svg.writer", "direct")).data.get("pages/0001.svg").toByteArray());
		assertNotNull("batik must draw the image", batik);
		assertNotNull("the direct writer must draw the image", direct);
		// 1ptより細かい差は座標の丸めなので許す
		assertEquals("the drawn width must match", batik[0], direct[0], 1.0);
		assertEquals("the drawn height must match", batik[1], direct[1], 1.0);
		// CSSで40pt×30ptを与えてある。そこから外れたら寸法の取り違え
		assertEquals("the CSS width must be honoured", 40.0, direct[0], 1.0);
		assertEquals("the CSS height must be honoured", 30.0, direct[1], 1.0);
	}

	/** 最初の{@code <image>}が実際に占める幅と高さを、祖先のtransformまで畳んで返します。 */
	private static double[] imageBox(final byte[] svg) throws Exception {
		final org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(svg));
		final org.w3c.dom.NodeList images = doc.getElementsByTagName("image");
		if (images.getLength() == 0) {
			return null;
		}
		final org.w3c.dom.Element image = (org.w3c.dom.Element) images.item(0);
		double w = Double.parseDouble(image.getAttribute("width"));
		double h = Double.parseDouble(image.getAttribute("height"));
		for (org.w3c.dom.Node n = image; n instanceof org.w3c.dom.Element e; n = n.getParentNode()) {
			final double[] s = scaleOf(e.getAttribute("transform"));
			w *= s[0];
			h *= s[1];
		}
		return new double[] { Math.abs(w), Math.abs(h) };
	}

	/** {@code matrix(a b c d e f)}または{@code scale(...)}から拡大率だけ取り出します。 */
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
			if ("matrix".equals(m.group(1)) && parts.length >= 4) {
				sx *= Double.parseDouble(parts[0]);
				sy *= Double.parseDouble(parts[3]);
			} else if ("scale".equals(m.group(1)) && parts.length >= 1) {
				sx *= Double.parseDouble(parts[0]);
				sy *= Double.parseDouble(parts.length >= 2 ? parts[1] : parts[0]);
			}
		}
		return new double[] { sx, sy };
	}

	/** manifestのSHA-256が実体と一致すること。流しながら取っているので特に確かめる。 */
	public void testManifestHashesMatchTheBytes() throws Exception {
		final CapturingResults direct = run(Map.of("output.paged-svg.writer", "direct"));
		final String manifest = direct.text("manifest.json");
		for (int i = 1; i <= pageCount(direct); ++i) {
			final String name = String.format("pages/%04d.svg", i);
			final String sha = sha256(direct.data.get(name).toByteArray());
			assertTrue("the manifest must record the actual bytes of " + name + " (" + sha + ")",
					manifest.contains(sha));
		}
	}

	private static String sha256(final byte[] bytes) throws Exception {
		return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static int pageCount(final CapturingResults r) {
		return (int) r.order.stream().filter(u -> u.startsWith("pages/") && u.endsWith(".svg")).count();
	}

	private static void assertWellFormedXml(final String name, final byte[] bytes) throws Exception {
		try {
			javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new ByteArrayInputStream(bytes));
		} catch (final Exception e) {
			throw new AssertionError(name + " is not well-formed XML: " + e + "\n"
					+ new String(bytes, StandardCharsets.UTF_8), e);
		}
	}

	private CapturingResults run(final Map<String, String> extraProps) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("processing.pass-count", "2");
			for (final Map.Entry<String, String> e : extraProps.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html().getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/1080-FONT/direct-svg-test.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// 何もしない
		}

		String text(final String uri) {
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
