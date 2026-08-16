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

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 制限・異常系の入出力プロパティの検査です(2026-08-02新設、
 * 入出力プロパティ網羅の第5陣)。
 *
 * <p>
 * 出力の中身ではなく<b>振る舞い</b>(中断するか、警告が出るか、資源を
 * 取りに行くか)を見る。
 * </p>
 */
public class LimitIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File TWO_PAGES = new File("files/unittest/ioprops/two-pages.html");

	private static final File WITH_IMAGE = new File("files/unittest/ioprops/link-and-image.html");

	private final List<Short> messages = new ArrayList<>();

	private boolean failed;

	/** {@code output.page-limit}: 上限を超えると中断すること。 */
	public void testPageLimit() throws Exception {
		final String pdf = this.convert(TWO_PAGES, props("output.page-limit", "1"));
		assertTrue("上限超過が通知されるか、出力が打ち切られること",
				this.failed || pageCount(pdf) <= 1);
	}

	/** 上限に達しなければ中断しないこと(境界の確認)。 */
	public void testPageLimitNotReached() throws Exception {
		final String pdf = this.convert(TWO_PAGES, props("output.page-limit", "10"));
		assertFalse("中断しないこと", this.failed);
		assertEquals("2ページ出ること", 2, pageCount(pdf));
	}

	/** {@code output.size-limit}: 出力量の上限で中断すること。 */
	public void testSizeLimit() throws Exception {
		this.convert(TWO_PAGES, props("output.size-limit", "100"));
		assertTrue("出力量の上限で中断すること", this.failed);
	}

	/** {@code input.size-limit}: 主文書を読み切る前に上限で中断すること。 */
	public void testInputSizeLimit() throws Exception {
		this.convert(TWO_PAGES, props("input.size-limit", "32"));
		assertTrue("主文書の入力上限で中断すること", this.failed);
	}

	/** {@code input.size-limit}: 既知長と同じ上限なら変換できること。 */
	public void testInputSizeLimitExactBoundary() throws Exception {
		this.convert(TWO_PAGES, props("input.size-limit", Long.toString(TWO_PAGES.length())));
		assertFalse("主文書と同じ長さの上限では中断しないこと", this.failed);
	}

	/** {@code input.resource-count-limit}: 外部資源数0なら画像を取得しないこと。 */
	public void testResourceCountLimit() throws Exception {
		final String pdf = this.convert(WITH_IMAGE, props("input.resource-count-limit", "0"));
		assertFalse("文書本体は読めること", this.failed);
		assertFalse("外部画像を埋め込まないこと", pdf.contains("/Subtype /Image"));
	}

	/** {@code input.resource-size-limit}: 外部資源の累積入力0なら画像を取得しないこと。 */
	public void testResourceSizeLimit() throws Exception {
		final String pdf = this.convert(WITH_IMAGE, props("input.resource-size-limit", "0"));
		assertFalse("文書本体は読めること", this.failed);
		assertFalse("外部画像を埋め込まないこと", pdf.contains("/Subtype /Image"));
	}

	/** {@code processing.time-limit}: 複数パスを含む文書全体の締切で中断すること。 */
	public void testProcessingTimeLimit() throws Exception {
		this.convert(TWO_PAGES, props("processing.time-limit", "1", "processing.pass-count", "2"));
		assertTrue("処理時間の上限で中断すること", this.failed);
	}

	/**
	 * {@code input.include}: 資源が読めること(基準)。
	 *
	 * <p>
	 * <b>制限の側は現状では検査できない。</b> 2026-08-02の実測で、
	 * 埋め込み側が{@code setSourceResolver}でリゾルバを差し込むと
	 * {@code file:}資源はそちらが解決し、{@code input.include}/
	 * {@code input.exclude}のACLを通らないことが分かった。CLIも
	 * ウェブアプリも汎用リゾルバを差し込むため、実運用でも同じである
	 * (HTTPは2026-08-02の修正で設定側が優先になりACLを通る)。
	 * アクセス制御の意味が変わる話なのでオーナー判断待ち——PLAN §3。
	 * </p>
	 */
	public void testIncludeAllowsResources() throws Exception {
		final String allowed = this.convert(WITH_IMAGE, props("input.include", "**"));
		assertTrue("全許可なら画像が埋め込まれること", allowed.contains("/Subtype /Image"));
	}

	private static int pageCount(final String pdf) {
		int count = 0;
		int at = 0;
		while (true) {
			final int found = pdf.indexOf("/Type /Page", at);
			if (found < 0) {
				break;
			}
			// "/Type /Pages" は数えない
			if (!pdf.startsWith("/Type /Pages", found)) {
				++count;
			}
			at = found + 1;
		}
		return count;
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		this.messages.clear();
		this.failed = false;
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> this.messages.add(code));
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				// **ACLは先に登録したパターンが勝つ**ので、テストが
				// include/exclude を指定する場合は既定を入れない
				if (!properties.containsKey("input.include") && !properties.containsKey("input.exclude")) {
					session.property("input.include", "**");
				}
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} catch (final Exception e) {
				// 中断は例外で通知される(どの型かは問わない——ここで見るのは
				// 「上限で止まったか」だけである)
				this.failed = true;
			} finally {
				try {
					session.close();
				} catch (final Exception e) {
					this.failed = true;
				}
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
