package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.style.running.RunningRegistry;
import net.zamasoft.foliojet.css.style.running.RunningTemplate;
import net.zamasoft.foliojet.css.style.running.StyleSnapshot;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 実変換で捕捉内容・副作用遮断・配置アンカーを検証します。 */
public final class RunningCaptureTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	record Observed(int page, long order, String name, boolean beginsPage, RunningTemplate template) {
	}

	record Conversion(byte[] pdf, List<String> pages, PassContext context, List<Observed> assignments,
			List<String> messages, long nanos) {
	}

	public void testDetachedSnapshotAndUnevaluatedContent() throws Exception {
		final Conversion result = convert("#h{position:running(h);counter-increment:page 40}"
				+ "#h::before{content:counter(page)}#h::after{content:attr(data-note)}"
				+ "#body::before{content:counter(page)}",
				"<div id='h' data-note='NOTE'>A<span>B</span></div><p id='body'>BODY</p>", false, true, true);
		final RunningTemplate template = result.context().getRunningState().snapshot("h").entry().value();
		assertNotNull(template);
		assertEquals(0, liveReferences(template));
		final List<StyleSnapshot.FrozenValue> expressions = new ArrayList<StyleSnapshot.FrozenValue>();
		for (final RunningTemplate.Event event : template.events()) {
			if (event instanceof RunningTemplate.Start start) {
				collectExpressions(start.style().properties().get("content"), expressions);
			}
		}
		assertTrue("counter(page)が式のまま残る: " + expressions, expressions.stream().anyMatch(
				value -> value.type().equals(CounterValue.class.getName()) && "page".equals(value.fields().get("name"))));
		assertTrue(template.events().stream().anyMatch(event -> event instanceof RunningTemplate.Start start
				&& "before".equals(start.pseudo())));
		// 捕捉中の counter-increment は原位置でも実行しない(本文の counter(page) は 1 のまま)。
		// 表示リストの "desc=2.41" を拾わないよう Text の中身で見る
		assertFalse(result.pages().toString(), result.pages().toString().contains("Text[\"41\""));
		assertTrue(result.pages().toString(), result.pages().toString().contains("Text[\"1\""));
		System.err.println("[running R1b] captured events=" + template.events().size() + ", expressions=" + expressions);
	}

	public void testNestedRunningIsAssignedIndependently() throws Exception {
		final Conversion result = convert("#outer{position:running(outer)}#inner{position:running(inner)}",
				"<div id='outer'>OUT<span id='inner'>IN</span>END</div><p>BODY</p>", false, true, true);
		final RunningTemplate outer = result.context().getRunningState().snapshot("outer").entry().value();
		final RunningTemplate inner = result.context().getRunningState().snapshot("inner").entry().value();
		assertNotNull(inner);
		assertEquals(2L, result.context().getRunningRegistry().assignedCount());
		assertTrue(outer.events().stream().anyMatch(event -> event instanceof RunningTemplate.Token token
				&& "inner".equals(token.name())));
		assertFalse(outer.events().stream().anyMatch(event -> event instanceof RunningTemplate.Text text
				&& text.text().contains("IN")));
		assertEquals(0, liveReferences(outer));
	}

	public void testPageStartAndMiddleAcrossTwoPages() throws Exception {
		final Conversion result = convert(".head{position:running(head)}.mid{position:running(mid)}"
				+ ".page2{break-before:page}",
				"<section><span class='head'>H1</span><p>A<span class='mid'>M1</span>B</p></section>"
				+ "<section class='page2'><span class='head'>H2</span><p>C<span class='mid'>M2</span>D</p></section>",
				false, true, true);
		assertEquals(2, result.pages().size());
		assertEquals(4, result.assignments().size());
		for (final Observed assignment : result.assignments()) {
			assertEquals(assignment.toString(), "head".equals(assignment.name()), assignment.beginsPage());
		}
		assertEquals(List.of(1, 1, 2, 2), result.assignments().stream().map(Observed::page).toList());
		System.err.println("[running R1b] assignment anchors=" + result.assignments());
	}

	public void testOversizedTemplateIsRejected() throws Exception {
		final Conversion result = convert("#h{position:running(h)}",
				"<div id='h'>" + "x".repeat(52_000) + "</div><p>BODY</p>", false, false, false);
		assertEquals(0, result.context().getRunningRegistry().retainedCandidateCount("h"));
		assertEquals(1L, result.context().getRunningRegistry().rejectedCount());
		assertTrue(result.messages().toString(), result.messages().stream().anyMatch(
				message -> message.contains("template rejected")));
	}

	public void testEventAndImageLimits() throws Exception {
		for (final String content : List.of("<span></span>".repeat(5_001),
				"<span class='image'></span>".repeat(51))) {
			final Conversion result = convert("#h{position:running(h)}.image{background-image:url(unloaded.png)}",
					"<div id='h'>" + content + "</div><p>BODY</p>", false, false, false);
			assertEquals(0L, result.context().getRunningRegistry().assignedCount());
			assertEquals(1L, result.context().getRunningRegistry().rejectedCount());
			assertTrue(result.messages().toString(), result.messages().stream().anyMatch(
					message -> message.contains("template rejected")));
		}
	}

	public void testLegacyAndAbsoluteAnchors() throws Exception {
		final Conversion result = convert(".legacy{-cssj-page-content:legacy single}.absolute{position:absolute}"
				+ ".running{position:running(absolute)}",
				"<div class='absolute'><span class='running'>A</span><span>X</span></div>"
				+ "<div class='legacy'>L</div><p>BODY</p>", false, true, true);
		assertEquals(2L, result.context().getRunningRegistry().assignedCount());
		assertTrue(result.assignments().stream().anyMatch(
				assignment -> "absolute".equals(assignment.name()) && !assignment.beginsPage()));
		assertTrue(result.context().getRunningState().snapshot("legacy").entry().value().legacy());
		assertFalse("既定の両面印刷ではsingleのLを描かない", result.pages().toString().contains("Text[\"L\""));
	}

	public void testRunningPseudoInsideRunningIsIndependent() throws Exception {
		final Conversion result = convert(".outer{position:running(outer)}"
				+ ".outer::before{position:running(inner);content:'INNER'}",
				"<div class='outer'>OUTER</div><p>BODY</p>", false, true, true);
		final RunningTemplate outer = result.context().getRunningState().snapshot("outer").entry().value();
		final RunningTemplate inner = result.context().getRunningState().snapshot("inner").entry().value();
		assertEquals(2L, result.context().getRunningRegistry().assignedCount());
		assertTrue(outer.events().stream().anyMatch(event -> event instanceof RunningTemplate.Token token
				&& "inner".equals(token.name())));
		assertFalse(outer.events().stream().anyMatch(event -> event instanceof RunningTemplate.Start start
				&& "before".equals(start.pseudo())));
		assertTrue(inner.events().stream().anyMatch(event -> event instanceof RunningTemplate.Start start
				&& "before".equals(start.pseudo()) && start.style().properties().get("content").toString().contains("INNER")));
	}

	public void testPseudoRootKeepsOriginAttributesAndInheritedLanguage() throws Exception {
		final Conversion result = convert("p::before{position:running(h);content:attr(title)}",
				"<section lang='en'><p title='TITLE'>BODY</p></section>", false, false, true);
		final RunningTemplate template = result.context().getRunningState().snapshot("h").entry().value();
		final StyleSnapshot snapshot = ((RunningTemplate.Start) template.events().get(0)).style();
		assertEquals("TITLE", snapshot.attributes().get("title"));
		assertEquals("en", snapshot.language());
		assertTrue(snapshot.properties().get("content").toString().contains("AttrValue"));
		assertEquals(0, liveReferences(template));
	}

	public void testInlineSvgIsDetachedForReplay() throws Exception {
		final String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='10' height='12'>"
				+ "<rect width='7' height='9' fill='red'/></svg>";
		for (final String body : List.of("<div class='h'>" + svg + "</div><p>BODY</p>",
				svg.replace("<svg ", "<svg class='h' ") + "<p>BODY</p>")) {
			final Conversion result = convert(".h{position:running(h)}", body, false, false, true);
			final RunningTemplate template = result.context().getRunningState().snapshot("h").entry().value();
			final var images = template.events().stream().filter(event -> event instanceof RunningTemplate.Start)
					.map(event -> ((RunningTemplate.Start) event).style().svgSource()).filter(java.util.Objects::nonNull).toList();
			assertEquals(1, images.size());
			assertTrue(images.get(0).document(), images.get(0).document().contains("rect"));
			assertEquals("file:///running-r1b.html", images.get(0).baseURI());
			assertEquals(1, template.imageReferences());
			assertEquals(0, liveReferences(template));
			System.err.println("[running R1b #7] SVG=" + images);
		}
	}

	public void testEveryCopiedPropertyHasABudgetAndBodyResumes() throws Exception {
		for (final String property : List.of("string-set:other '" + "x".repeat(52_000) + "'",
				"background-image:url(data:image/svg+xml," + "x".repeat(52_000) + ")")) {
			final Conversion result = convert(".huge{position:running(huge);" + property + "}"
					+ ".ok{position:running(ok)}", "<div class='huge'>HUGE</div><span class='ok'>OK</span><p>BODY</p>",
					false, true, true);
			assertNull(result.context().getRunningState().snapshot("huge").entry());
			assertNotNull(result.context().getRunningState().snapshot("ok").entry());
			assertEquals(1L, result.context().getRunningRegistry().rejectedCount());
			assertTrue(result.messages().toString(), result.messages().stream().anyMatch(message -> message.contains("text bytes")));
			assertTrue(result.pages().toString(), result.pages().toString().contains("Text[\"BODY\""));
		}
	}

	public void testSnapshotCopiesStringSetAndLimitsItsPayload() throws Exception {
		final UserAgent ua = new net.zamasoft.foliojet.ua.impl.recorder.RecorderUserAgent();
		final CSSStyle style = CSSStyle.getCSSStyle(ua, null, CSSElement.ANON);
		final net.zamasoft.foliojet.css.value.Value[] parts = {
				new net.zamasoft.foliojet.css.value.StringValue("payload".repeat(100)) };
		style.set(net.zamasoft.foliojet.css.impl.property.content.StringSet.INFO,
				new net.zamasoft.foliojet.css.value.ValueListValue(new net.zamasoft.foliojet.css.value.Value[] {
						new net.zamasoft.foliojet.css.value.StringSetEntryValue("h", parts) }));
		final StyleSnapshot snapshot = StyleSnapshot.capture(style);
		assertTrue(snapshot.textBytes() >= 1_400);
		parts[0] = new net.zamasoft.foliojet.css.value.StringValue("x".repeat(60_000));
		assertTrue(snapshot.properties().get("string-set").toString().contains("payload"));
		try {
			StyleSnapshot.capture(style);
			fail("120,000 bytes of string-set must exceed the copy budget");
		} catch (final IllegalArgumentException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("text bytes"));
		}
		assertEquals(0, liveReferences(snapshot));
		System.err.println("[running R1b #9] copied bytes=" + snapshot.textBytes());
	}

	public void testPseudoSnapshotDetachesOriginAttributes() throws Exception {
		final UserAgent ua = new net.zamasoft.foliojet.ua.impl.recorder.RecorderUserAgent();
		final var attributes = new org.xml.sax.helpers.AttributesImpl();
		attributes.addAttribute("", "title", "title", "CDATA", "TITLE");
		final CSSElement origin = new CSSElement("http://www.w3.org/1999/xhtml", "p", null, null, null,
				java.util.Locale.ENGLISH, null, attributes, null, 0, 0);
		final CSSStyle parent = CSSStyle.getCSSStyle(ua, null, origin);
		final StyleSnapshot snapshot = StyleSnapshot.capture(CSSStyle.getCSSStyle(ua, parent, CSSElement.BEFORE));
		attributes.setValue(0, "CHANGED");
		assertEquals("TITLE", snapshot.attributes().get("title"));
		assertEquals("en", snapshot.language());
		assertEquals(0, liveReferences(snapshot));
	}

	public void testSvgSerializationIsBoundedAndDetached() throws Exception {
		final var document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
		document.appendChild(svg);
		final var rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
		rect.setAttribute("width", "7");
		svg.appendChild(rect);
		final var object = new net.zamasoft.foliojet.objects.svg.SVGInlineObject();
		final var snapshot = object.getClass().getDeclaredMethod("snapshotRunningSource", org.w3c.dom.Document.class, String.class);
		snapshot.setAccessible(true);
		snapshot.invoke(object, document, "file:///svg.html");
		final var source = object.getRunningSource();
		assertNotNull(source.document());
		assertTrue(source.document(), source.document().contains("width=\"7\""));
		rect.setAttribute("width", "12");
		assertFalse(source.document().contains("width=\"12\""));
		svg.setAttribute("data-large", "x".repeat(52_000));
		snapshot.invoke(object, document, "file:///svg.html");
		assertNull(object.getRunningSource().document());
		assertEquals(0, liveReferences(source));
	}

	public void testFirstLetterCharactersGoToCapture() throws Exception {
		final Conversion result = convert("p::first-letter{position:running(h)}", "<p>AB</p>", false, true, true);
		final RunningTemplate template = result.context().getRunningState().snapshot("h").entry().value();
		assertTrue(template.events().stream().anyMatch(event -> event instanceof RunningTemplate.Text text
				&& "A".equals(text.text())));
		assertFalse(result.pages().toString(), result.pages().toString().contains("Text[\"AB\""));
		assertFalse(result.pages().toString(), result.pages().toString().contains("Text[\"A\""));
	}

	public void testRunningAfterPreservedNewlinesUsesCurrentPage() throws Exception {
		for (final String whiteSpace : List.of("pre", "pre-wrap", "pre-line", "normal")) {
			final String breaks = "normal".equals(whiteSpace) ? "<br>".repeat(40) : "\n".repeat(40);
			final Conversion result = convert("p{white-space:" + whiteSpace + ";line-height:12pt}.h{position:running(h)}",
					"<p>A" + breaks + "<span class='h'>HEAD</span>B</p>", false, true, true);
			assertTrue(result.pages().toString(), result.pages().size() > 1);
			assertEquals(1, result.assignments().size());
			final Observed assignment = result.assignments().get(0);
			assertTrue(assignment.toString(), assignment.page() > 1);
			assertTrue(result.pages().toString(), result.pages().get(assignment.page() - 1).contains("Text[\"B\""));
			System.err.println("[running R1b #4] " + whiteSpace + ": " + assignment);
		}
	}

	/** offsetの穴は別頁のfloat/脚注、子箱はabsoluteの配置を模す。描画は行わない。 */
	public void testCharacterGapsAndAbsoluteOwnership() {
		final RunningRegistry registry = new RunningRegistry();
		final long order = registry.nextOrder();
		registry.strings(order, List.of(new net.zamasoft.foliojet.ua.PendingStringSet("h", List.of("VALUE"), order)));
		registry.bindCharacters(order, 200, true);
		final var body = new net.zamasoft.foliojet.layout.box.impl.LineBox(new net.zamasoft.foliojet.layout.box.params.BlockParams());
		body.addText(textAt(100));
		body.addText(textAt(300));
		assertTrue(registry.commitPage(body).isEmpty());
		assertEquals(1, registry.pendingCount());
		final AbsoluteLine absolute = new AbsoluteLine();
		absolute.addText(textAt(200));
		body.addAbsolute(absolute);
		final List<RunningRegistry.Placement> placements = registry.commitPage(body);
		assertEquals(1, placements.size());
		assertSame(absolute, placements.get(0).box());
		assertFalse(placements.get(0).beginsPage());
		assertEquals(0, registry.pendingCount());
		System.err.println("[running R1b #2] placements=" + placements.size() + ", outsideFlow beginsPage=false");
	}

	private static net.zamasoft.pdfg2d.gc.text.Text textAt(final int offset) {
		return (net.zamasoft.pdfg2d.gc.text.Text) java.lang.reflect.Proxy.newProxyInstance(
				RunningCaptureTest.class.getClassLoader(), new Class<?>[] { net.zamasoft.pdfg2d.gc.text.Text.class },
				(proxy, method, args) -> switch (method.getName()) {
				case "getCharOffset" -> offset;
				case "getCharCount", "getGlyphCount" -> 1;
				default -> throw new UnsupportedOperationException(method.getName());
				});
	}

	private static final class AbsoluteLine extends net.zamasoft.foliojet.layout.box.impl.LineBox
			implements net.zamasoft.foliojet.layout.box.IAbsoluteBox {
		AbsoluteLine() {
			super(new net.zamasoft.foliojet.layout.box.params.BlockParams());
		}

		@Override
		public net.zamasoft.foliojet.layout.box.params.AbsolutePos getAbsolutePos() {
			return new net.zamasoft.foliojet.layout.box.params.AbsolutePos();
		}
	}

	private static void collectExpressions(final Object value, final List<StyleSnapshot.FrozenValue> result) {
		if (value instanceof StyleSnapshot.FrozenValue frozen) {
			result.add(frozen);
			collectExpressions(frozen.fields(), result);
		} else if (value instanceof Map<?, ?> map) {
			map.values().forEach(item -> collectExpressions(item, result));
		} else if (value instanceof Iterable<?> items) {
			items.forEach(item -> collectExpressions(item, result));
		}
	}

	/** 到達可能な実体と、各非staticフィールドの宣言型の双方を再帰的に調べます。 */
	private static int liveReferences(final Object root) throws Exception {
		final Deque<Object> work = new ArrayDeque<Object>();
		final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		work.push(root);
		int count = 0;
		while (!work.isEmpty()) {
			final Object value = work.pop();
			if (!seen.add(value)) {
				continue;
			}
			if (value instanceof CSSStyle || value instanceof CSSElement || value instanceof UserAgent) {
				++count;
				continue;
			}
			if (value instanceof Map<?, ?> map) {
				for (final Object item : map.values()) {
					if (item != null) {
						work.push(item);
					}
				}
			} else if (value instanceof Iterable<?> items) {
				for (final Object item : items) {
					if (item != null) {
						work.push(item);
					}
				}
			} else if (value.getClass().getName().startsWith("net.zamasoft.")) {
				for (Class<?> type = value.getClass(); type != Object.class; type = type.getSuperclass()) {
					for (final Field field : type.getDeclaredFields()) {
						if (Modifier.isStatic(field.getModifiers())) {
							continue;
						}
						assertTrue("不変フィールド: " + field, Modifier.isFinal(field.getModifiers()));
						if (CSSStyle.class.isAssignableFrom(field.getType())
								|| CSSElement.class.isAssignableFrom(field.getType())
								|| UserAgent.class.isAssignableFrom(field.getType())) {
							++count;
						}
						field.setAccessible(true);
						final Object child = field.get(value);
						if (child != null) {
							work.push(child);
						}
					}
				}
			}
		}
		return count;
	}

	/** 3試験クラスで共有するDirectSession変換です。保持量試験では観測payloadを保存しません。 */
	static Conversion convert(final String css, final String body, final boolean tagged,
			final boolean dump, final boolean observe) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:240pt 180pt;margin:20pt}html,body{margin:0;font-size:10pt}p{margin:0}"
				+ css + "</style></head><body>" + body + "</body></html>";
		final File dir = dump ? Files.createTempDirectory("foliojet-running-r1b-").toFile() : null;
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final List<String> messages = new ArrayList<String>();
		final CaptureUA ua = new CaptureUA(observe);
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(output)));
			session.setMessageHandler((code, args, text) -> messages.add(code + ": " + Arrays.toString(args) + " " + text));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.pdf.tagged", Boolean.toString(tagged));
			final long begin = System.nanoTime();
			try (AutoCloseable scope = DisplayListDumper.scopedDir(dir == null ? null : dir.getPath())) {
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///running-r1b.html"), "text/html", "UTF-8");
			}
			final long elapsed = System.nanoTime() - begin;
			final List<String> pages = new ArrayList<String>();
			if (dir != null) {
				final File[] files = dir.listFiles((parent, name) -> name.startsWith("page-") && name.endsWith(".txt"));
				assertNotNull(files);
				Arrays.sort(files, Comparator.comparing(File::getName));
				for (final File file : files) {
					final String page = Files.readString(file.toPath(), StandardCharsets.UTF_8);
					pages.add(page);
					System.err.println("[running R1b] " + file.getName() + "\n" + page);
				}
			}
			System.err.println("[running R1b] PDF bytes=" + output.size() + ", messages=" + messages);
			return new Conversion(output.toByteArray(), List.copyOf(pages), ua.getPassContext(),
					List.copyOf(ua.assignments), List.copyOf(messages), elapsed);
		} finally {
			session.close();
			if (dir != null) {
				final File[] files = dir.listFiles();
				if (files != null) {
					for (final File file : files) {
						Files.deleteIfExists(file.toPath());
					}
				}
				Files.deleteIfExists(dir.toPath());
			}
		}
	}

	private static final class CaptureUA extends PDFUserAgent {
		final List<Observed> assignments = new ArrayList<Observed>();
		final boolean observe;

		CaptureUA(final boolean observe) {
			this.observe = observe;
		}

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.visitor = new PDFVisitor(this) {
				private int page;

				@Override
				public void nextPage(final PDFGC gc) {
					super.nextPage(gc);
					++this.page;
				}

				@Override
				public void visitAssignment(final RunningRegistry.Placement placement) {
					super.visitAssignment(placement);
					if (CaptureUA.this.observe && placement.template() != null) {
						CaptureUA.this.assignments.add(new Observed(this.page, placement.order(),
								placement.template().name(), placement.beginsPage(), placement.template()));
					}
				}
			};
		}
	}
}
