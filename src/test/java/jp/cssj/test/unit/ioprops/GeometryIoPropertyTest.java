package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 版面の幾何に効く入出力プロパティの検査です(2026-08-02新設、
 * 入出力プロパティ網羅の第3陣)。
 *
 * <p>
 * 判定は出力PDFの<b>紙面の寸法({@code /MediaBox})とページ数</b>で行う。
 * 組版の細部はdisplay-list goldenが見ているので、ここでは
 * 「そのプロパティが紙面に効いたか」だけを見る。
 * </p>
 */
public class GeometryIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 2ページになる文書(ページ数に効くプロパティの検査用)。 */
	private static final File TWO_PAGES = new File("files/unittest/ioprops/two-pages.html");

	private static final Pattern MEDIA_BOX = Pattern
			.compile("/MediaBox\\s*\\[\\s*([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)");

	/** 基準: 200x300pt、2ページ。 */
	private static Map<String, String> base() {
		return props("output.page-width", "200pt", "output.page-height", "300pt");
	}

	public void testPageSize() throws Exception {
		final String pdf = this.convert(base());
		final double[] box = mediaBox(pdf);
		assertEquals("紙面の幅", 200.0, box[2] - box[0], 1.0);
		assertEquals("紙面の高さ", 300.0, box[3] - box[1], 1.0);
		assertEquals("ページ数", 2, pageCount(pdf));
	}

	/** {@code output.no-page-break}: 改ページしない(1ページになる)。 */
	public void testNoPageBreak() throws Exception {
		final Map<String, String> props = base();
		props.put("output.no-page-break", "true");
		final String pdf = this.convert(props);
		assertEquals("改ページしないので1ページ", 1, pageCount(pdf));
	}

	/** {@code output.auto-height}: 内容に合わせて紙面が伸びる。 */
	public void testAutoHeight() throws Exception {
		final Map<String, String> props = base();
		props.put("output.auto-height", "true");
		final String pdf = this.convert(props);
		assertEquals("1ページにまとまる", 1, pageCount(pdf));
		final double[] box = mediaBox(pdf);
		assertTrue("高さが内容に合わせて縮む(300ptのままではない): " + (box[3] - box[1]),
				Math.abs((box[3] - box[1]) - 300.0) > 1.0);
	}

	/** {@code output.htrim}/{@code output.vtrim}: 断ち代の分だけ紙面が広がる。 */
	public void testTrims() throws Exception {
		final Map<String, String> props = base();
		props.put("output.htrim", "10pt");
		props.put("output.vtrim", "20pt");
		props.put("output.marks", "crop");
		final String pdf = this.convert(props);
		final double[] box = mediaBox(pdf);
		assertEquals("幅は左右の断ち代ぶん広がる", 220.0, box[2] - box[0], 1.0);
		assertEquals("高さは上下の断ち代ぶん広がる", 340.0, box[3] - box[1], 1.0);
	}

	/** {@code output.trims}: 4辺の断ち代をまとめて指定できる。 */
	public void testTrimsShorthand() throws Exception {
		final Map<String, String> props = base();
		props.put("output.trims", "5pt");
		props.put("output.marks", "crop");
		final String pdf = this.convert(props);
		final double[] box = mediaBox(pdf);
		assertEquals("幅が5pt×2広がる", 210.0, box[2] - box[0], 1.0);
		assertEquals("高さが5pt×2広がる", 310.0, box[3] - box[1], 1.0);
	}

	/** {@code output.n-up}: 面付けでページがまとまる。 */
	public void testNUp() throws Exception {
		final Map<String, String> props = base();
		props.put("output.n-up", "2");
		final String pdf = this.convert(props);
		assertEquals("2ページが1枚にまとまる", 1, pageCount(pdf));
	}

	/** {@code output.paper-width}/{@code output.paper-height}: 用紙の寸法。 */
	public void testPaperSize() throws Exception {
		final Map<String, String> props = base();
		props.put("output.paper-width", "400pt");
		props.put("output.paper-height", "500pt");
		props.put("output.fit-to-paper", "true");
		final String pdf = this.convert(props);
		final double[] box = mediaBox(pdf);
		assertEquals("紙面が用紙の幅になる", 400.0, box[2] - box[0], 1.0);
		assertEquals("紙面が用紙の高さになる", 500.0, box[3] - box[1], 1.0);
	}

	private static int pageCount(final String pdf) {
		int count = 0;
		final Matcher m = Pattern.compile("/Type\\s*/Page[^s]").matcher(pdf);
		while (m.find()) {
			++count;
		}
		return count;
	}

	private static double[] mediaBox(final String pdf) {
		final Matcher m = MEDIA_BOX.matcher(pdf);
		assertTrue("MediaBoxが見つかること", m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
				Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private final List<String> licenseBlocked = new ArrayList<>();

	private String convert(final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		this.licenseBlocked.clear();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
					if (code == net.zamasoft.foliojet.message.MessageCodes.WARN_LICENSE_CONSTRAINT_IO) {
						this.licenseBlocked.add(args != null && args.length > 0 ? args[0] : "?");
					}
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, TWO_PAGES, "text/html", null);
			} finally {
				session.close();
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
