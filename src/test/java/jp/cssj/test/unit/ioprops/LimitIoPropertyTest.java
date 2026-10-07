package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.RetainedTextLimit;
import net.zamasoft.foliojet.layout.RetainedTextLimitException;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for limit and error-path I/O properties (introduced on 2026-08-02,
 * the fifth batch of comprehensive I/O property coverage).
 *
 * <p>
 * Check <b>behavior</b> (whether conversion aborts, warnings appear, or resources are fetched),
 * rather than output contents.
 * </p>
 */
public class LimitIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File TWO_PAGES = new File("files/unittest/ioprops/two-pages.html");

	private static final File WITH_IMAGE = new File("files/unittest/ioprops/link-and-image.html");

	private final List<Short> messages = new ArrayList<>();

	private boolean failed;

	/** {@code output.page-limit}: abort when the limit is exceeded. */
	public void testPageLimit() throws Exception {
		final String pdf = this.convert(TWO_PAGES, props("output.page-limit", "1"));
		assertTrue("上限超過が通知されるか、出力が打ち切られること",
				this.failed || pageCount(pdf) <= 1);
	}

	/** Do not abort if the limit is not reached (boundary check). */
	public void testPageLimitNotReached() throws Exception {
		final String pdf = this.convert(TWO_PAGES, props("output.page-limit", "10"));
		assertFalse("中断しないこと", this.failed);
		assertEquals("2ページ出ること", 2, pageCount(pdf));
	}

	/** {@code output.size-limit}: abort at the output-size limit. */
	public void testSizeLimit() throws Exception {
		this.convert(TWO_PAGES, props("output.size-limit", "100"));
		assertTrue("出力量の上限で中断すること", this.failed);
	}

	/** {@code input.size-limit}: abort at the limit before reading the entire main document. */
	public void testInputSizeLimit() throws Exception {
		this.convert(TWO_PAGES, props("input.size-limit", "32"));
		assertTrue("主文書の入力上限で中断すること", this.failed);
	}

	/** {@code input.size-limit}: conversion succeeds when the limit equals the known length. */
	public void testInputSizeLimitExactBoundary() throws Exception {
		this.convert(TWO_PAGES, props("input.size-limit", Long.toString(TWO_PAGES.length())));
		assertFalse("主文書と同じ長さの上限では中断しないこと", this.failed);
	}

	/** {@code input.resource-count-limit}: do not fetch images if the external-resource count limit is 0. */
	public void testResourceCountLimit() throws Exception {
		final String pdf = this.convert(WITH_IMAGE, props("input.resource-count-limit", "0"));
		assertFalse("文書本体は読めること", this.failed);
		assertFalse("外部画像を埋め込まないこと", pdf.contains("/Subtype /Image"));
	}

	/** {@code input.resource-size-limit}: do not fetch images if the cumulative external-resource input limit is 0. */
	public void testResourceSizeLimit() throws Exception {
		final String pdf = this.convert(WITH_IMAGE, props("input.resource-size-limit", "0"));
		assertFalse("文書本体は読めること", this.failed);
		assertFalse("外部画像を埋め込まないこと", pdf.contains("/Subtype /Image"));
	}

	/** {@code processing.time-limit}: abort at the deadline for the entire document, including multiple passes. */
	public void testProcessingTimeLimit() throws Exception {
		this.convert(TWO_PAGES, props("processing.time-limit", "1", "processing.pass-count", "2"));
		assertTrue("処理時間の上限で中断すること", this.failed);
	}

	/**
	 * {@code input.include}: resources can be read (baseline).
	 *
	 * <p>
	 * <b>The restriction side cannot currently be tested.</b> Measurements on 2026-08-02 showed that
	 * when the embedding application injects a resolver through {@code setSourceResolver},
	 * it resolves {@code file:} resources without passing through the {@code input.include}/
	 * {@code input.exclude} ACL. Both CLI and web applications inject a generic resolver, so
	 * production behaves the same way (the 2026-08-02 fix gave HTTP settings priority, so HTTP passes the ACL).
	 * This changes access-control semantics, so it awaits the owner's decision—PLAN §3.
	 * </p>
	 */
	public void testIncludeAllowsResources() throws Exception {
		final String allowed = this.convert(WITH_IMAGE, props("input.include", "**"));
		assertTrue("全許可なら画像が埋め込まれること", allowed.contains("/Subtype /Image"));
	}


	private static final File RETAINED_TABLE = new File("files/unittest/ioprops/retained-table.html");
	private static final File RETAINED_FLOAT = new File("files/unittest/ioprops/retained-float-table.html");

	public void testRetainedTextLimitFailure() throws Exception {
		final RetainedResult result = this.convertRetained(RETAINED_TABLE, 1024,
				"processing.fail-on-fatal-error", "false");
		assertRetainedFailure(result, 1024);
	}

	public void testRetainedTextLimitBoundary() throws Exception {
		final RetainedResult unlimited = this.convertRetained(RETAINED_TABLE, 0);
		assertNull(unlimited.failure());
		final long highWater = unlimited.highWater();
		assertTrue("fixtureが小さい上限を超えること", highWater > 1024);
		final RetainedResult exact = this.convertRetained(RETAINED_TABLE, highWater);
		assertNull("high-waterと同じ上限では完走すること", exact.failure());
		assertEquals(highWater, exact.highWater());
		assertRetainedFailure(this.convertRetained(RETAINED_TABLE, highWater - 2), highWater - 2);
	}

	public void testRetainedTextLimitUnlimited() throws Exception {
		final RetainedResult result = this.convertRetained(RETAINED_TABLE, 0);
		assertNull(result.failure());
		assertTrue(result.highWater() > 1024);
		assertFalse(result.messages().contains(MessageCodes.ERROR_RETAINED_TEXT_LIMIT));
	}

	public void testRetainedTextLimitOrdinaryProse() throws Exception {
		final RetainedResult small = this.convertRetained(
				new File("files/unittest/ioprops/retained-prose.html"), 1024);
		final RetainedResult large = this.convertRetained(
				new File("files/unittest/ioprops/retained-prose-4x.html"), 1024);
		assertNull(small.failure());
		assertNull(large.failure());
		// Both are 0 for body text with neither tables nor independent hosts. Do not calculate the 0/0 ratio.
		assertEquals(0L, small.highWater());
		assertEquals(0L, large.highWater());
	}

	public void testRetainedTextLimitNestedHost() throws Exception {
		final RetainedResult flat = this.convertRetained(RETAINED_TABLE, 0);
		final RetainedResult nested = this.convertRetained(RETAINED_FLOAT, 0);
		assertNull(flat.failure());
		assertNull(nested.failure());
		assertTrue(flat.highWater() > 1024);
		assertEquals("内側の表と外側のfloatへ二重に足さないこと", flat.highWater(), nested.highWater());
		final long between = flat.highWater() + flat.highWater() / 2;
		assertNull(this.convertRetained(RETAINED_TABLE, between).failure());
		assertNull(this.convertRetained(RETAINED_FLOAT, between).failure());
	}

	public void testRetainedTextLimitPassBReleased() throws Exception {
		final var previous = RetainedTextLimit.beforeTableMainBind;
		final var samples = new java.util.concurrent.atomic.AtomicLong();
		final var current = new java.util.concurrent.atomic.AtomicLong(-1);
		final var measured = new java.util.concurrent.atomic.AtomicLong();
		try {
			RetainedTextLimit.beforeTableMainBind = limit -> {
				samples.incrementAndGet();
				current.accumulateAndGet(limit.getCurrentBytes(), Math::max);
				measured.accumulateAndGet(limit.getHighWater(), Math::max);
			};
			assertNull(this.convertRetained(RETAINED_TABLE, 0).failure());
			assertTrue("MAIN前の観測点を通ること", samples.get() > 0);
			assertTrue("Pass B中も数えていること", measured.get() > 1024);
			assertEquals("破棄したPass Bの量はMAINへ残らないこと", 0L, current.get());
		} finally {
			RetainedTextLimit.beforeTableMainBind = previous;
		}
	}

	public void testRetainedTextLimitMarginBoxesIsolated() throws Exception {
		this.checkRetainedMarginBoxes(false);
	}

	public void testRetainedTextLimitRunningIsolated() throws Exception {
		this.checkRetainedMarginBoxes(true);
	}

	private void checkRetainedMarginBoxes(final boolean running) throws Exception {
		final String rows = ("<tr><td>" + "abcdefghij".repeat(4) + "</td></tr>").repeat(30);
		final String css = "@page { size:300pt 200pt; margin:40pt } body { margin:0; font:10pt serif }"
				+ "table { table-layout:auto; width:200pt; border-spacing:0 } td { padding:0; height:20pt }";
		final String header = "HEADER ".repeat(24).trim();
		final String marginCss = "@page { @top-center { content:"
				+ (running ? "element(heading)" : "'" + header + "'") + " } }"
				+ "#heading { position:running(heading) } #heading span { display:inline-block; width:180pt }";
		final String template = running ? "<div id='heading'><span>" + header + "</span></div>" : "";
		final RetainedResult plain = this.convertRetained(
				"<html><style>" + css + "</style><body><table>" + rows + "</table></body></html>", 0);
		assertNull(plain.failure());
		assertTrue("表のaddBound中に改頁するfixtureであること", plain.pages() > 1);
		assertTrue(plain.highWater() > 1024);
		final RetainedResult withHeader = this.convertRetained("<html><style>" + css + marginCss
				+ "</style><body>" + template + "<table>" + rows + "</table></body></html>", plain.highWater());
		assertNull("柱の計測・描画が本文の上限を超えさせないこと", withHeader.failure());
		assertEquals(plain.pages(), withHeader.pages());
		assertEquals("柱の文字量を本文の表へ加算しないこと", plain.highWater(), withHeader.highWater());
		try (final var pdf = Loader.loadPDF(withHeader.pdf())) {
			assertTrue("柱の再生経路を実際に通ること", new PDFTextStripper().getText(pdf).contains("HEADER"));
		}
	}

	public void testRetainedTextLimitBalancedColumns() throws Exception {
		this.checkRetainedBalancedColumns("", false);
	}

	public void testRetainedTextLimitBalancedInlineBlock() throws Exception {
		this.checkRetainedBalancedColumns("display:inline-block;", true);
	}

	private void checkRetainedBalancedColumns(final String display, final boolean boxReplay) throws Exception {
		final String text = "abcdefghij".repeat(60);
		final long payload = text.length() * 2L;
		// An absolutely positioned child triggers containsAbsolute in canReplayChildren, branching to box replay.
		// The child is empty, so the 600-character body payload remains unchanged.
		final String child = boxReplay ? "<span style='position:absolute; width:1pt; height:1pt'></span>" : "";
		final String document = "<html><style>@page { size:400pt 600pt; margin:20pt }"
				+ "body { margin:0; font:10pt/12pt serif } div { " + display
				+ "width:240pt; column-count:2; column-fill:balance; column-gap:12pt; word-break:break-all }"
				+ "</style><body><div>" + text + child + "</div></body></html>";
		for (final long limit : new long[] { 0, payload + payload / 2 }) {
			final long suspensionsBefore = RetainedTextLimit.SUSPEND_ENTRIES.get();
			final long sourceReplaysBefore = SourceReplayer.BALANCE_REPLAYS.get();
			final RetainedResult result = this.convertRetained(document, limit);
			assertNull("無制限と1回分<上限<2回分で完走すること", result.failure());
			final long suspensions = RetainedTextLimit.SUSPEND_ENTRIES.get() - suspensionsBefore;
			final long sourceReplays = SourceReplayer.BALANCE_REPLAYS.get() - sourceReplaysBefore;
			assertTrue("balance再生でsuspendに入ること", suspensions > 0);
			if (boxReplay) {
				assertEquals("ソース再生が不適格となりボックス再生へ進むこと", 0L, sourceReplays);
			} else {
				assertEquals("suspendしたbalanceがソース再生を通ること", suspensions, sourceReplays);
			}
			assertEquals("balance再生は文字数×2を二重計数しないこと", payload, result.highWater());
		}
	}

	public void testRetainedTextLimitContinuousReloadsLimit() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		final List<Short> messages = new ArrayList<>();
		try (final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			session.setUserAgent(ua);
			session.setMessageHandler((code, args, message) -> messages.add(code));
			session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
			session.setContinuous(true);
			session.property("processing.retained-text-limit", "0");
			CTISessionHelper.transcodeFile(session, RETAINED_TABLE, "text/html", null);
			final RetainedTextLimit accounting = ua.getRetainedTextLimit();
			assertTrue(accounting.getHighWater() > 1024);
			final long previousHighWater = RetainedTextLimit.HIGH_WATER.get();
			messages.clear();
			session.property("processing.retained-text-limit", "1024");
			TranscoderException failure = null;
			try {
				CTISessionHelper.transcodeFile(session, RETAINED_TABLE, "text/html", null);
			} catch (final TranscoderException e) {
				failure = e;
			}
			assertSame(accounting, ua.getRetainedTextLimit());
			assertEquals(1024L, accounting.getLimit());
			assertRetainedFailure(new RetainedResult(failure, accounting.getHighWater(), List.copyOf(messages), 0,
					new byte[0]), 1024);
			assertEquals("超過の通知は1回だけ", 1,
					java.util.Collections.frequency(messages, MessageCodes.ERROR_RETAINED_TEXT_LIMIT));
			assertTrue("staticの最大値は変換開始で戻さないこと",
					RetainedTextLimit.HIGH_WATER.get() >= previousHighWater);
		}
	}

	public void testRetainedTextLimitContinuousResetsHighWater() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		try (final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
			session.setContinuous(true);
			session.property("processing.retained-text-limit", "0");
			CTISessionHelper.transcodeFile(session, RETAINED_TABLE, "text/html", null);
			final RetainedTextLimit accounting = ua.getRetainedTextLimit();
			assertTrue(accounting.getHighWater() > 1024);
			session.property("processing.retained-text-limit", "1024");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/ioprops/retained-prose.html"),
					"text/html", null);
			assertSame(accounting, ua.getRetainedTextLimit());
			assertEquals(1024L, accounting.getLimit());
			assertEquals(0L, accounting.getHighWater());
			session.join();
		}
	}

	public void testRetainedTextLimitSharedAcrossPasses() throws Exception {
		final var middleHighWater = new java.util.concurrent.atomic.AtomicLong();
		final PDFUserAgent ua = new PDFUserAgent() {
			@Override
			public void prepare(final PrepareMode mode) {
				super.prepare(mode);
				if (mode == PrepareMode.LAST_PASS) middleHighWater.set(this.getRetainedTextLimit().getHighWater());
			}
		};
		try (final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
			session.property("processing.retained-text-limit", "0");
			session.property("processing.pass-count", "2");
			CTISessionHelper.transcodeFile(session, RETAINED_TABLE, "text/html", null);
			assertTrue("中間パスのhigh-waterが最終パス開始時にも残ること", middleHighWater.get() > 1024);
			assertEquals(middleHighWater.get(), ua.getRetainedTextLimit().getHighWater());
		}
	}

	public void testRetainedTextLimitNestedSuspensionAndMeasurement() {
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		ua.setProperty("processing.retained-text-limit", "1024");
		try (final RetainedTextLimit limit = ua.getRetainedTextLimit(); var outer = limit.enter("table")) {
			limit.add(600);
			try (var suspended = limit.suspend()) {
				limit.add(800);
				try (var nested = limit.suspend()) {
					limit.add(800);
				}
				limit.add(800);
				assertEquals(600L, limit.getCurrentBytes());
				try (var measurement = limit.measurement("td")) {
					limit.add(800);
					assertEquals("加算保留中でもPass B自身は数えること", 800L, limit.getCurrentBytes());
					try {
						limit.add(226);
						fail("計測用複製にも上限を適用すること");
					} catch (final RetainedTextLimitException expected) {
						assertEquals(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, expected.getCode());
					}
				}
				limit.add(800);
				assertEquals("計測後も親の累計と加算保留を復元すること", 600L, limit.getCurrentBytes());
			}
			limit.add(200);
			assertEquals(800L, limit.getCurrentBytes());
		}
	}

	public void testRetainedTextLimitSuspensionResumesAfterException() {
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		ua.setProperty("processing.retained-text-limit", "1024");
		try (final RetainedTextLimit limit = ua.getRetainedTextLimit(); var outer = limit.enter("div")) {
			limit.add(600);
			final RuntimeException failure = new IllegalStateException("replay failed");
			try {
				try (var suspended = limit.suspend()) {
					limit.add(800);
					throw failure;
				}
			} catch (final RuntimeException expected) {
				assertSame("suspendのスコープ外まで例外が伝播すること", failure, expected);
			}
			assertEquals(600L, limit.getCurrentBytes());
			assertEquals(600L, limit.getHighWater());
			limit.add(200);
			assertEquals("例外でsuspendを抜けても加算が再開すること", 800L, limit.getCurrentBytes());
			assertEquals(800L, limit.getHighWater());
			try {
				limit.add(226);
				fail("復帰後も上限を検査すること");
			} catch (final RetainedTextLimitException expected) {
				assertEquals(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, expected.getCode());
			}
		}
	}

	public void testRetainedTextLimitScopeOwnership() {
		try (final RetainedTextLimit limit = new PDFUserAgent() {
		}.getRetainedTextLimit()) {
			final var outer = limit.enter("table");
			limit.add(600);
			try (var measurement = limit.measurement("td")) {
				limit.add(800);
				outer.close();
				outer.close();
				assertEquals("親Scopeのcloseで計測スタックを畳まないこと", 800L, limit.getCurrentBytes());
			}
			assertEquals("閉じた親の累計を復活させないこと", 0L, limit.getCurrentBytes());
			try (var parent = limit.enter("div")) {
				limit.add(200);
				final var first = limit.measurement("table");
				limit.add(400);
				final var second = limit.measurement("td");
				limit.add(800);
				first.close();
				assertEquals(800L, limit.getCurrentBytes());
				second.close();
				first.close();
				assertEquals("閉じた計測を飛ばして親へ戻ること", 200L, limit.getCurrentBytes());
			}
		}
	}

	private static void assertRetainedFailure(final RetainedResult result, final long limit) {
		final TranscoderException failure = result.failure();
		assertNotNull("上限超過で変換を失敗させること", failure);
		assertEquals(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, failure.getCode());
		assertEquals(TranscoderException.STATE_BROKEN, failure.getState());
		final String[] args = failure.getArgs();
		assertNotNull(args);
		assertEquals(3, args.length);
		assertEquals("table", args[0]);
		assertEquals(Long.toString(limit), args[1]);
		assertEquals(result.highWater(), Long.parseLong(args[2]));
		assertTrue(Long.parseLong(args[2]) > limit);
		assertNotNull("causeをformatter/workerで落とさないこと", RetainedTextLimitException.findIn(failure));
		assertTrue(result.messages().contains(MessageCodes.ERROR_RETAINED_TEXT_LIMIT));
	}

	private record RetainedResult(TranscoderException failure, long highWater, List<Short> messages, int pages, byte[] pdf) {
	}

	/** Observe the final CTI code/args/state without swallowing unexpected exceptions. */
	private RetainedResult convertRetained(final File document, final long limit, final String... extra)
			throws Exception {
		return this.convertRetained(Files.readString(document.toPath()), limit, extra);
	}

	/** Allow fixtures as strings too, avoiding extra files for boundary tests. */
	private RetainedResult convertRetained(final String document, final long limit, final String... extra)
			throws Exception {
		final long previousHighWater = RetainedTextLimit.HIGH_WATER.getAndSet(0);
		final List<Short> messages = new ArrayList<>();
		TranscoderException failure = null;
		try (final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			session.setMessageHandler((code, args, message) -> messages.add(code));
			session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("processing.retained-text-limit", Long.toString(limit));
			for (final var entry : props(extra).entrySet()) {
				session.property(entry.getKey(), entry.getValue());
			}
			try {
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)), RETAINED_TABLE.toURI(),
						"text/html", "UTF-8");
			} catch (final TranscoderException e) {
				failure = e;
			}
			return new RetainedResult(failure, RetainedTextLimit.HIGH_WATER.get(), List.copyOf(messages),
					pageCount(stream.toString(StandardCharsets.ISO_8859_1)), stream.toByteArray());
		} finally {
			RetainedTextLimit.HIGH_WATER.accumulateAndGet(previousHighWater, Math::max);
		}
	}

	private static int pageCount(final String pdf) {
		int count = 0;
		int at = 0;
		while (true) {
			final int found = pdf.indexOf("/Type /Page", at);
			if (found < 0) {
				break;
			}
			// Do not count "/Type /Pages".
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
				// **The first registered ACL pattern wins**, so if a test specifies
				// include/exclude, do not add the default.
				if (!properties.containsKey("input.include") && !properties.containsKey("input.exclude")) {
					session.property("input.include", "**");
				}
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} catch (final Exception e) {
				// Aborts are reported through exceptions (the type does not matter: this only checks
				// whether conversion stopped at the limit).
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
