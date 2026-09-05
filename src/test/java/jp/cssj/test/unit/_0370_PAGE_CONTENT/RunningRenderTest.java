package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** GCPMの頁選択・表示頁での生成内容・任意部分木をDirectSessionで確認します。 */
public final class RunningRenderTest extends TestCase {
	static final class TestUA extends PDFUserAgent {
	}
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");
	static final Pattern Y = Pattern.compile("y=([-0-9.]+)");

	record Conversion(byte[] pdf, List<String> pages, List<String> messages, long nanos) {
	}

	public void testHeadingFirstLastAndDisplayPageCounter() throws Exception {
		final Conversion result = fixture("running-header.html");
		assertEquals(3, result.pages().size());
		final String[] expected = { "ALPHA/1ALPHA/1", "BETA/2GAMMA/2", "GAMMA/3GAMMA/3" };
		for (int i = 0; i < expected.length; ++i) {
			assertEquals("page " + (i + 1), expected[i], marginText(result.pages().get(i), true));
		}
		assertNoReplayWarnings(result);
	}

	public void testSameTemplateInTwoMarginBoxes() throws Exception {
		final Conversion result = fixture("running-two-refs.html");
		assertEquals(1, result.pages().size());
		assertEquals("SHAREDSHARED", marginText(result.pages().get(0), true));
		final List<Double> positions = new ArrayList<Double>();
		for (final String line : result.pages().get(0).split("\n")) {
			if (line.contains("artifact ") && line.contains("Text[\"SHARED\"")) {
				final var x = Pattern.compile("x=([-0-9.]+)").matcher(line);
				assertTrue(x.find());
				positions.add(Double.parseDouble(x.group(1)));
			}
		}
		assertEquals(2, positions.size());
		assertTrue(positions.toString(), positions.get(1) > positions.get(0));
		assertNoReplayWarnings(result);
	}

	public void testFourModeMatrixAtStartMiddleAndMultipleAssignments() throws Exception {
		final Conversion result = fixture("running-assign-clear.html");
		assertEquals(4, result.pages().size());
		final String[] top = { "AAB", "BBB", "CBD", "DDD" };
		final String[] bottom = { "", "B", "", "D" };
		for (int i = 0; i < top.length; ++i) {
			assertEquals("first/start/last page " + (i + 1), top[i], marginText(result.pages().get(i), true));
			assertEquals("first-except page " + (i + 1), bottom[i], marginText(result.pages().get(i), false));
		}
		assertNoReplayWarnings(result);
	}

	public void testFourModeClearMatrixAndEmptyTemplate() throws Exception {
		// clearのCSS配線はR3。ここでは捕捉した実テンプレートを頁状態へ代入して再生を確かめる。
		final var ua = new RunningSideEffectTest.AuditUA(false, agent -> {
			final var state = agent.getPassContext().getRunningState();
			final var a = state.resolve("a", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value();
			final var b = state.resolve("b", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value();
			final var empty = state.resolve("empty", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value();
			assertNotNull(a);
			assertNotNull(b);
			assertNotNull(empty);
			// 操作列、first、先頭start、途中start、last、first-except。Cはclear。
			final String[][] cases = {
					{ "", "A", "A", "A", "A", "A" },
					{ "B", "B", "B", "A", "B", "" },
					{ "C", "", "", "A", "", "" },
					{ "BC", "B", "B", "A", "", "" },
					{ "CB", "", "", "A", "B", "" },
					{ "BCB", "B", "B", "A", "B", "" },
					{ "CBC", "", "", "A", "", "" }
			};
			for (final boolean begins : new boolean[] { true, false }) {
				for (final String[] row : cases) {
					state.reset();
					state.assign("pick", a, 0, true);
					state.endPage();
					for (int i = 0; i < row[0].length(); ++i) {
						if (row[0].charAt(i) == 'C') {
							state.clear("pick", i + 1, begins && i == 0);
						} else {
							state.assign("pick", b, i + 1, begins && i == 0);
						}
					}
					final String[] expected = { row[1], row[begins ? 2 : 3], row[4], row[5] };
					int index = 0;
					for (final var mode : net.zamasoft.foliojet.ua.PageAssignmentState.Mode.values()) {
						final String actual = renderResolved(agent, "pick", mode);
						System.err.println("[running R2] clear matrix: " + row[0] + ", begins=" + begins + ", " + mode + "=" + actual);
						assertEquals(expected[index++], actual);
					}
					state.endPage();
					for (final var mode : net.zamasoft.foliojet.ua.PageAssignmentState.Mode.values()) {
						assertEquals("次頁の持越し", row[4], renderResolved(agent, "pick", mode));
					}
				}
			}
			state.reset();
			final var renderer = new net.zamasoft.foliojet.css.style.running.RunningRenderer(agent,
					new net.zamasoft.foliojet.css.style.running.PageValueSnapshot(agent, agent.getPassContext().getPageSide(), null));
			final var container = RunningSideEffectTest.container(agent);
			final var first = net.zamasoft.foliojet.ua.PageAssignmentState.Mode.FIRST;
			assertNull(renderer.prepare(new net.zamasoft.foliojet.css.value.ElementFunctionValue("pick", first), container));
			state.assign("pick", empty, 1, true);
			final var content = renderer.prepare(new net.zamasoft.foliojet.css.value.ElementFunctionValue("pick", first), container);
			assertNotNull("空内容はtombstoneではない", content);
			final var drawer = new net.zamasoft.foliojet.layout.draw.Drawer(0);
			net.zamasoft.foliojet.css.style.running.RunningRenderer.draw(
					content.layout(RunningSideEffectTest.params(agent, container), 100, 30), drawer, 0, 0);
			final StringBuilder dump = new StringBuilder();
			drawer.dump(dump, "");
			System.err.println("[running R2] empty template: " + dump);
			assertTrue(dump.toString(), dump.toString().contains("RectFrame"));
		});
		final var result = convert(document("#a{position:running(a)}#b{position:running(b)}"
				+ "#empty{position:running(empty);border:1pt solid red;height:5pt}",
				"<div id='a'>A</div><div id='b'>B</div><div id='empty'></div><p>BODY</p>"), ua, false, false, Map.of());
		assertEquals(1, ua.checkedPages);
		assertNoReplayWarnings(result);
	}

	private static String renderResolved(final PDFUserAgent ua, final String name,
			final net.zamasoft.foliojet.ua.PageAssignmentState.Mode mode) {
		final var container = RunningSideEffectTest.container(ua);
		final var renderer = new net.zamasoft.foliojet.css.style.running.RunningRenderer(ua,
				new net.zamasoft.foliojet.css.style.running.PageValueSnapshot(ua, ua.getPassContext().getPageSide(), null));
		final var content = renderer.prepare(new net.zamasoft.foliojet.css.value.ElementFunctionValue(name, mode), container);
		if (content == null) {
			return "";
		}
		final var drawer = new net.zamasoft.foliojet.layout.draw.Drawer(0);
		net.zamasoft.foliojet.css.style.running.RunningRenderer.draw(
				content.layout(RunningSideEffectTest.params(ua, container), 100, 30), drawer, 0, 0);
		final StringBuilder dump = new StringBuilder();
		drawer.dump(dump, "");
		return artifactText(dump.toString());
	}

	public void testTableImageNestedRunningAndOverflow() throws Exception {
		final String image = "data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%228%22 height=%228%22%3E"
				+ "%3Crect width=%228%22 height=%228%22 fill=%22red%22/%3E%3C/svg%3E";
		final Conversion result = convert(document("@page{@top-center{content:element(h)}}"
				+ "#h{position:running(h);width:90pt}#inner{position:running(inner)}"
				+ "table{border-collapse:collapse}td{border:1pt solid blue}img{width:8pt;height:8pt}",
				"<div id='h'>OUTER<span id='inner'>HIDDEN</span><table><caption>CAPTION</caption>"
				+ "<tr><td>CELL</td><td><img src='" + image + "'></td></tr></table>"
				+ "<div style='height:50pt'>OVERFLOW</div></div><p>BODY</p>"), new TestUA(), false, true, Map.of());
		assertEquals(1, result.pages().size());
		final String page = result.pages().get(0);
		final String text = artifactText(page);
		assertTrue(text, text.contains("CELL"));
		assertTrue(text, text.contains("CAPTION"));
		assertTrue(text, text.contains("OVERFLOW"));
		assertFalse(text, text.contains("HIDDEN"));
		assertTrue(page, page.lines().anyMatch(line -> line.contains("artifact ") && line.contains("AbsoluteRectFrame[w=8.00 h=8.00]")));
		assertTrue(page, page.lines().anyMatch(line -> line.contains("artifact ") && line.contains("Border")));
		assertNoReplayWarnings(result);
	}

	public void testPageStringsAttributesCountersAndLeader() throws Exception {
		final Conversion result = convert(document("@page{@top-center{content:element(h)}}"
				+ "#h{position:running(h);width:130pt}#h::before{content:attr(data-label) ':' string(title,last) ':' counters(page,'.')}"
				+ "#h::after{content:leader('.') 'END'}#one{string-set:title 'ONE'}#two{string-set:title 'TWO';break-before:page}",
				"<div id='h' data-label='LABEL'></div><p id='one'>FIRST</p><p id='two'>SECOND</p>"),
				new TestUA(), false, true, Map.of());
		assertEquals(2, result.pages().size());
		assertTrue(artifactText(result.pages().get(0)), artifactText(result.pages().get(0)).contains("LABEL:ONE:1"));
		assertTrue(artifactText(result.pages().get(1)), artifactText(result.pages().get(1)).contains("LABEL:TWO:2"));
		assertTrue(artifactText(result.pages().get(1)).contains("END"));
		assertNoReplayWarnings(result);
	}

	public void testTargetCounterReadsReferencedPage() throws Exception {
		final var ua = new RunningSideEffectTest.AuditUA(false, agent -> {
			final var template = agent.getPassContext().getRunningState()
					.resolve("h", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.FIRST).value();
			assertNotNull(template);
			final var before = template.events().stream().filter(event -> event instanceof
					net.zamasoft.foliojet.css.style.running.RunningTemplate.Start start && "before".equals(start.pseudo()))
					.map(event -> ((net.zamasoft.foliojet.css.style.running.RunningTemplate.Start) event).style())
					.findFirst().orElseThrow();
			assertEquals("#target", before.attributes().get("href"));
			final URI uri = URI.create(before.baseURI()).resolve(before.attributes().get("href"));
			assertEquals(URI.create("file:///running-r2.html#target"), uri);
			final var values = agent.getUAContext().getPageRef().counterView(agent.isLastPass()).counters(uri, "page", false);
			System.err.println("[running R2] target-counter: attr=" + before.attributes().get("href")
					+ ", base=" + before.baseURI() + ", uri=" + uri + ", lastPass=" + agent.isLastPass() + ", values=" + values);
			if (agent.isLastPass()) {
				assertEquals(List.of(2), values);
			}
		});
		final Conversion result = convert(document("@page{@top-center{content:element(h)}}"
				+ "#h{position:running(h)}#h::before{content:'TARGET=' target-counter(attr(href),page) '/PAGE=' counter(page)}"
				+ "#target{break-before:page}",
				"<div id='h' href='#target'></div><p>FIRST</p><p id='target'>TARGETBODY</p>"),
				ua, false, true, Map.of("processing.pass-count", "2", "processing.page-references", "true"));
		assertEquals(2, result.pages().size());
		assertEquals("TARGET=2/PAGE=1", marginText(result.pages().get(0), true));
		assertEquals("TARGET=2/PAGE=2", marginText(result.pages().get(1), true));
		assertNoReplayWarnings(result);
	}

	public void testDataImagesReplayAtOriginalSize() throws Exception {
		// 空白を含むSVGのdata: URIと通常のbase64 PNGを、同じfile:基底から読む。
		final String svg = "data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2216%22 height=%228%22%3E"
				+ "%3Crect width=%2216%22 height=%228%22 fill=%22red%22/%3E%3C/svg%3E";
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16, 8,
				java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png));
		for (final String image : List.of(svg, "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png.toByteArray()))) {
			final String body = "<div id='h'><img src='" + image + "'></div><p>BODY</p>";
			final String css = "img{width:auto;height:auto}";
			final var original = convert(document(css, body), new TestUA(), false, true, Map.of());
			final var replay = convert(document(css + "#h{position:running(h)}@page{@top-center{content:element(h)}}", body),
					new TestUA(), false, true, Map.of());
			final String frame = "AbsoluteRectFrame[w=12.00 h=6.00]";
			assertTrue(original.pages().toString(), original.pages().stream().anyMatch(page -> page.contains(frame)));
			assertTrue(replay.pages().toString(), replay.pages().stream().flatMap(String::lines)
					.anyMatch(line -> line.contains("artifact ") && line.contains(frame)));
			assertNoReplayWarnings(original);
			assertNoReplayWarnings(replay);
		}
	}

	public void testInlineSvgReplayAtOriginalIntrinsicSize() throws Exception {
		final String body = "<div id='h'><svg xmlns='http://www.w3.org/2000/svg' width='16' height='8'>"
				+ "<rect width='16' height='8' fill='red'/></svg></div><p>BODY</p>";
		final String css = "svg{width:auto!important;height:auto!important}";
		final var original = convert(document(css, body), new TestUA(), false, true, Map.of());
		final var replay = convert(document(css + "#h{position:running(h)}@page{@top-center{content:element(h)}}", body),
				new TestUA(), false, true, Map.of());
		final String frame = "AbsoluteRectFrame[w=12.00 h=6.00]";
		assertTrue(original.pages().toString(), original.pages().stream().anyMatch(page -> page.contains(frame)));
		assertTrue(replay.pages().toString(), replay.pages().stream().flatMap(String::lines)
				.anyMatch(line -> line.contains("artifact ") && line.contains(frame)));
		assertNoReplayWarnings(original);
		assertNoReplayWarnings(replay);
	}

	public void testVerticalRunningPreferredWidthInHorizontalMargin() throws Exception {
		final var ua = new RunningSideEffectTest.AuditUA(false, agent -> {
			try {
				final Object box = RunningSideEffectTest.marginBox(agent, "content:element(h);writing-mode:horizontal-tb");
				final var preferred = box.getClass().getDeclaredMethod("preferredWidth", net.zamasoft.foliojet.ua.UserAgent.class);
				preferred.setAccessible(true);
				final double width = (Double) preferred.invoke(box, agent);
				System.err.println("[running R2] vertical running preferred width=" + width);
				assertEquals(10.0, width, .01);
			} catch (final ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		final var result = convert(document("@page{@top-center{content:element(h);writing-mode:horizontal-tb}}"
				+ "#h{position:running(h);writing-mode:vertical-rl;font-size:10pt;line-height:10pt}",
				"<div id='h'>一二三四五六七八</div><p>BODY</p>"), ua, false, true, Map.of());
		assertEquals(1, ua.checkedPages);
		assertNoReplayWarnings(result);
	}

	public void testVerticalTemplateAndMarginBox() throws Exception {
		final Conversion result = convert(document("@page{@right-middle{content:element(h);writing-mode:vertical-rl}}"
				+ "#h{position:running(h);writing-mode:vertical-rl;font-size:8pt}",
				"<div id='h'>VERTICAL</div><p>BODY</p>"), new TestUA(), false, true, Map.of());
		assertEquals(1, result.pages().size());
		assertTrue(result.pages().toString(), artifactText(result.pages().get(0)).contains("VERTICAL"));
		assertNoReplayWarnings(result);
	}

	public void testAnonymousColumnsPlacedTablesBreakAndFixedOrigin() throws Exception {
		final var result = convert(document("@page{@top-left{content:element(h);vertical-align:top}}"
				+ "#h{position:running(h);width:200pt}.columns{column-count:2}.inline{display:inline-table}"
				+ ".floating{float:left}.fixed{position:fixed;left:0;top:0}td{border:1pt solid blue}",
				"<div id='h'><div class='columns'><p>COLUMN</p><p>SECOND</p></div><span>AFTER</span>"
				+ "<br><span>BREAK</span><table class='inline'><tr><td>INLINE</td></tr></table>"
				+ "<table class='floating'><tr><td>FLOAT</td></tr></table><span class='fixed'>FIXED</span></div><p>BODY</p>"),
				new TestUA(), false, true, Map.of());
		assertEquals(1, result.pages().size());
		final String dump = result.pages().get(0);
		for (final String word : List.of("COLUMN", "SECOND", "AFTER", "BREAK", "INLINE", "FLOAT", "FIXED")) {
			assertTrue(dump, artifactText(dump).contains(word));
		}
		final List<Double> lines = new ArrayList<Double>();
		for (final String line : dump.split("\n")) {
			if (line.contains("Text[\"AFTER\"") || line.contains("Text[\"BREAK\"")) {
				final var y = Y.matcher(line);
				assertTrue(y.find());
				lines.add(Double.parseDouble(y.group(1)));
			}
		}
		assertEquals(2, lines.size());
		assertTrue("brの後は別行", lines.get(1) > lines.get(0));
		assertTrue("fixedも上余白に配置する", marginText(dump, true).contains("FIXED"));
		assertNoReplayWarnings(result);
	}

	static String document(final String css, final String body) {
		return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><style>"
				+ "@page{size:320pt 220pt;margin:30pt}html,body{margin:0;font:10pt serif}p{margin:0}"
				+ css + "</style></head><body>" + body + "</body></html>";
	}

	private static Conversion fixture(final String name) throws Exception {
		return convert(Files.readString(Path.of("files/unittest/0370-page-content", name), StandardCharsets.UTF_8),
				new TestUA(), false, true, Map.of());
	}

	static String artifactText(final String page) {
		final StringBuilder text = new StringBuilder();
		page.lines().filter(line -> line.contains("artifact ")).forEach(line -> {
			final var matcher = TEXT.matcher(line);
			while (matcher.find()) {
				text.append(matcher.group(1));
			}
		});
		return text.toString();
	}

	private static String marginText(final String page, final boolean top) {
		final StringBuilder text = new StringBuilder();
		page.lines().filter(line -> line.contains("artifact ")).forEach(line -> {
			final var y = Y.matcher(line);
			if (y.find() && (top ? Double.parseDouble(y.group(1)) < 0 : Double.parseDouble(y.group(1)) >= 160)) {
				final var matcher = TEXT.matcher(line);
				while (matcher.find()) {
					text.append(matcher.group(1));
				}
			}
		});
		return text.toString();
	}

	static void assertNoReplayWarnings(final Conversion result) {
		assertFalse(result.messages().toString(), result.messages().stream().anyMatch(message ->
				message.contains("running:") || message.contains("template rejected")
				|| message.startsWith(net.zamasoft.foliojet.message.MessageCodes.WARN_MISSING_IMAGE + ":")));
	}

	/** 全文fixtureとカスタムUAを受けるDirectSession経路です。大規模試験ではdumpしません。 */
	static Conversion convert(final String html, final PDFUserAgent ua, final boolean tagged,
			final boolean dump, final Map<String, String> properties) throws Exception {
		final Path dir = dump ? Files.createTempDirectory("foliojet-running-r2-") : null;
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final List<String> messages = new ArrayList<String>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(output)));
			session.setMessageHandler((code, args, text) -> messages.add(code + ": " + java.util.Arrays.toString(args) + " " + text));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.pdf.tagged", Boolean.toString(tagged));
			for (final var property : properties.entrySet()) {
				session.property(property.getKey(), property.getValue());
			}
			final long begin = System.nanoTime();
			try (final AutoCloseable scope = DisplayListDumper.scopedDir(dir == null ? null : dir.toString())) {
				CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///running-r2.html"), "text/html", "UTF-8");
			}
			final long nanos = System.nanoTime() - begin;
			final List<String> pages = new ArrayList<String>();
			if (dir != null) {
				try (final var paths = Files.list(dir)) {
					for (final Path path : paths.filter(p -> p.getFileName().toString().matches("page-.*\\.txt")).sorted().toList()) {
						final String page = Files.readString(path, StandardCharsets.UTF_8);
						pages.add(page);
						System.err.println("[running R2] " + path.getFileName() + "\n" + page);
					}
				}
			}
			System.err.println("[running R2] bytes=" + output.size() + ", ms=" + nanos / 1_000_000 + ", messages=" + messages);
			return new Conversion(output.toByteArray(), List.copyOf(pages), List.copyOf(messages), nanos);
		} finally {
			session.close();
			if (dir != null) {
				try (final var paths = Files.list(dir)) {
					for (final Path path : paths.toList()) {
						Files.deleteIfExists(path);
					}
				}
				Files.deleteIfExists(dir);
			}
		}
	}
}
