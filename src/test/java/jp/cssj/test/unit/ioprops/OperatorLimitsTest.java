package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.pdfbox.Loader;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.driver.OperatorLimits;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 運用者の上限({@link OperatorLimits}、{@value OperatorLimits#FILE_KEY})を固定します
 * (2026-10-03、共有サービスの資源の上限 増分2。
 * {@code copperpdf4/docs/design/shared-service-limits-design.md} §3-2)。
 *
 * <p>
 * 上限ファイルに書いた値は、クライアントの指定・プロファイル・文書中の処理命令のどれで
 * 設定された値に対しても<b>小さい方</b>になる——厳しくはできるが緩められない。
 * </p>
 */
public class OperatorLimitsTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String THREE_PAGES = "<html><head><style>@page{size:200pt 200pt;margin:10pt}"
			+ "p{page-break-after:always}</style></head><body><p>A</p><p>B</p><p>C</p></body></html>";

	private final List<Short> codes = new ArrayList<>();
	private boolean failed;

	// ---- 比較の規則(プロパティごと)

	public void testNegativeIsUnlimited() throws Exception {
		final OperatorLimits limits = limits("output.page-limit", "200");
		assertEquals("200", limits.clamp("output.page-limit", null));
		assertEquals("200", limits.clamp("output.page-limit", "-1"));
		assertEquals("200", limits.clamp("output.page-limit", "1000"));
		assertEquals("5", limits.clamp("output.page-limit", "5"));
		assertEquals("0", limits.clamp("output.page-limit", "0"));
		assertEquals("読めない値は上限そのもの", "200", limits.clamp("output.page-limit", "abc"));
	}

	public void testNonPositiveIsUnlimited() throws Exception {
		final OperatorLimits limits = limits("processing.time-limit", "300000");
		assertEquals("300000", limits.clamp("processing.time-limit", null));
		assertEquals("0 は無制限なので上限になる", "300000", limits.clamp("processing.time-limit", "0"));
		assertEquals("300000", limits.clamp("processing.time-limit", "-5"));
		assertEquals("30000", limits.clamp("processing.time-limit", "30000"));
	}

	/** retained-text-limit の既定は無制限ではなく 8MiB。未設定ならその既定と上限の小さい方。 */
	public void testEngineDefaultIsCompared() throws Exception {
		assertEquals(String.valueOf(8L << 20),
				limits("processing.retained-text-limit", "100000000").clamp("processing.retained-text-limit", null));
		assertEquals("1000", limits("processing.retained-text-limit", "1000").clamp("processing.retained-text-limit",
				null));
	}

	public void testConcurrencyAutoIsBounded() throws Exception {
		final OperatorLimits limits = limits("processing.concurrency", "1");
		assertEquals("1", limits.clamp("processing.concurrency", "0"));
		assertEquals("1", limits.clamp("processing.concurrency", "8"));
		assertEquals("1", limits.clamp("processing.concurrency", null));
	}

	public void testUnlimitedNamesPassThrough() throws Exception {
		assertEquals("x", limits("output.page-limit", "1").clamp("output.type", "x"));
		assertNull(limits("output.page-limit", "1").clamp("output.type", null));
	}

	public void testBadFileIsRefused() throws Exception {
		try {
			limits("output.type", "application/pdf");
			fail("表に無い名前は書けないこと");
		} catch (final IOException e) {
			// ok
		}
		try {
			limits("output.page-limit", "many");
			fail("数値でない値は書けないこと");
		} catch (final IOException e) {
			// ok
		}
	}

	// ---- 変換で効くこと

	/** クライアントが -1(無制限)を送っても、上限ファイルの値で止まる。警告 2825 が出る。 */
	public void testClientCannotLoosen() throws Exception {
		this.withLimits("output.page-limit=1\n", () -> {
			final byte[] pdf = this.convert(THREE_PAGES, props("output.page-limit", "-1"));
			assertTrue("2825 が出ること", this.codes.contains(MessageCodes.WARN_OPERATOR_LIMIT));
			assertTrue("頁数の上限で止まること", this.failed || pageCount(pdf) <= 1);
		});
	}

	/** 上限より厳しい指定は効く(警告なし)。 */
	public void testClientCanTighten() throws Exception {
		this.withLimits("output.page-limit=100\n", () -> {
			final byte[] pdf = this.convert(THREE_PAGES, props("output.page-limit", "10"));
			assertFalse(this.failed);
			assertFalse(this.codes.contains(MessageCodes.WARN_OPERATOR_LIMIT));
			assertEquals(3, pageCount(pdf));
		});
	}

	/** 文書中の処理命令でも緩められない(読み取り口で上限を掛けるため)。 */
	public void testProcessingInstructionCannotLoosen() throws Exception {
		this.withLimits("output.page-limit=1\n", () -> {
			final byte[] pdf = this.convert(
					"<?jp.cssj.property name=\"output.page-limit\" value=\"-1\"?>" + THREE_PAGES,
					props("input.property-pi", "true"));
			assertTrue("頁数の上限で止まること", this.failed || pageCount(pdf) <= 1);
		});
	}

	/** 外部資源の上限(MySourceResolver はセッションの表を直接読む)も緩められない。 */
	public void testResourceLimitCannotBeLoosened() throws Exception {
		final File dir = new File("local/unittest/operator-limits/resource");
		dir.mkdirs();
		Files.write(new File(dir, "a.png").toPath(), java.util.Base64.getDecoder()
				.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
		this.withLimits("input.resource-count-limit=0\n", () -> {
			final byte[] pdf = this.convert(dir, "<html><body><p>x</p><img src='a.png'/></body></html>",
					props("input.resource-count-limit", "-1"));
			assertFalse("文書は変換できること", this.failed);
			assertFalse("画像を取得しないこと",
					new String(pdf, StandardCharsets.ISO_8859_1).contains("/Subtype /Image"));
		});
	}

	/** 上限ファイルが無ければ何も変わらない。 */
	public void testNoLimitsFile() throws Exception {
		final byte[] pdf = this.convert(THREE_PAGES, props("output.page-limit", "-1"));
		assertFalse(this.failed);
		assertEquals(3, pageCount(pdf));
	}

	private interface Body {
		void run() throws Exception;
	}

	private void withLimits(final String content, final Body body) throws Exception {
		final File file = File.createTempFile("operator-limits", ".properties");
		final String old = System.getProperty(OperatorLimits.FILE_KEY);
		try {
			Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
			System.setProperty(OperatorLimits.FILE_KEY, file.getPath());
			body.run();
		} finally {
			if (old == null) {
				System.clearProperty(OperatorLimits.FILE_KEY);
			} else {
				System.setProperty(OperatorLimits.FILE_KEY, old);
			}
			file.delete();
		}
	}

	private static OperatorLimits limits(final String name, final String value) throws IOException {
		final Properties p = new Properties();
		p.setProperty(name, value);
		return OperatorLimits.parse(p, "test");
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static int pageCount(final byte[] pdf) throws IOException {
		if (pdf.length == 0) {
			return 0;
		}
		try (var doc = Loader.loadPDF(pdf)) {
			return doc.getNumberOfPages();
		}
	}

	private byte[] convert(final String html, final Map<String, String> properties) throws Exception {
		final File dir = new File("local/unittest/operator-limits/" + this.getName());
		dir.mkdirs();
		return this.convert(dir, html, properties);
	}

	private byte[] convert(final File dir, final String html, final Map<String, String> properties) throws Exception {
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		final File out = new File(dir, "out.pdf");
		this.codes.clear();
		this.failed = false;
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> this.codes.add(code));
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} catch (final Exception e) {
				this.failed = true;
			} finally {
				try {
					session.close();
				} catch (final Exception e) {
					this.failed = true;
				}
			}
		}
		return Files.readAllBytes(out.toPath());
	}
}
