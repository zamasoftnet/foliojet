package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.awt.geom.AffineTransform;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.style.running.PageValueSnapshot;
import net.zamasoft.foliojet.css.style.running.RunningRegistry;
import net.zamasoft.foliojet.css.style.running.RunningRenderer;
import net.zamasoft.foliojet.css.style.running.RunningTemplate;
import net.zamasoft.foliojet.css.style.running.StyleSnapshot;
import net.zamasoft.foliojet.css.style.running.TemplateExpander;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.fragment.LayoutSource.BoxKind;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;
import net.zamasoft.foliojet.ua.Counter;
import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/** 再生前後の状態、出力文書の登録数、長文の実時間を検証します。 */
public final class RunningSideEffectTest extends TestCase {
	public void testValueRestorationWithoutLayout() throws Exception {
		final PDFUserAgent ua = new RunningRenderTest.TestUA();
		ua.getDocumentContext().setBaseURI(URI.create("file:///running-r2.html"));
		final String css = "font-family:serif; font-size:13pt; opacity:.8; color:rgba(12,34,56,.7);"
				+ "transform:translate(2pt,3pt); text-shadow:1pt 2pt 3pt red; box-shadow:1pt 2pt 3pt blue;"
				+ "background-image:linear-gradient(red,blue); font-feature-settings:'kern' 0; filter:brightness(.8);"
				+ "mix-blend-mode:multiply; background-blend-mode:screen; text-decoration-style:wavy;"
				+ "content:counter(page) counters(page,'.') string(title,last) attr(title) target-counter('#body',page) leader('.') 'END'";
		for (final String declarations : List.of("", css, "flex-basis:content; font-variation-settings:'wght' 500")) {
			final CSSStyle source = container(ua);
			if (!declarations.isEmpty()) {
				final var parsed = com.helger.css.reader.CSSReaderDeclarationList.readFromString(declarations,
						new com.helger.css.reader.CSSReaderSettings().setBrowserCompliantMode(true));
				assertNotNull(parsed);
				for (final var declaration : parsed.getAllDeclarations()) {
					final var property = net.zamasoft.foliojet.css.property.ElementPropertySet.getInstance().parseDeclaration(
							declaration.getProperty(), net.zamasoft.foliojet.css.token.Tokens.fromExpression(declaration.getExpression()),
							ua, ua.getDocumentContext().getBaseURI(), false);
					assertNotNull(declaration.getProperty(), property);
					property.applyProperty(source);
				}
			}
			final var snapshot = StyleSnapshot.capture(source);
			final String before = fingerprint(ua.getPassContext());
			final var expander = new TemplateExpander(ua, new PageValueSnapshot(ua, CSSElement.PAGE_SINGLE, null));
			final var restored = StyleSnapshot.capture(expander.restore(snapshot, null, null));
			assertTrue(expander.droppedProperties().toString(), expander.droppedProperties().isEmpty());
			for (final var property : snapshot.properties().entrySet()) {
				assertEquals(property.getKey(), property.getValue(), restored.properties().get(property.getKey()));
			}
			assertEquals(snapshot.declared(), restored.declared());
			assertEquals(before, fingerprint(ua.getPassContext()));
			System.err.println("[running R2] value restoration: properties=" + snapshot.properties().size()
					+ ", declarations=" + snapshot.declared() + ", dropped=" + expander.droppedProperties());
		}
	}

	public void testComputedStylesRoundTripAndDriverAnchors() throws Exception {
		final AuditUA ua = new AuditUA(false, agent -> {
			final var page = new PageValueSnapshot(agent, agent.getPassContext().getPageSide(), null);
			final var expander = new TemplateExpander(agent, page);
			final var parents = new ArrayDeque<CSSStyle>();
			parents.push(container(agent));
			final var template = agent.getPassContext().getRunningState().resolve("h", Mode.FIRST).value();
			assertNotNull(template);
			int count = 0;
			for (final var event : template.events()) {
				if (event instanceof RunningTemplate.Start start) {
					final CSSStyle restored = expander.restore(start.style(), parents.peek(), start.pseudo());
					final var actual = StyleSnapshot.capture(restored);
					for (final var property : start.style().properties().entrySet()) {
						if (!"position".equals(property.getKey())) {
							assertEquals("計算値を再計算しない: " + property.getKey(), property.getValue(), actual.properties().get(property.getKey()));
						}
					}
					final Set<String> expectedDeclarations = new java.util.HashSet<String>(start.style().declared());
					final Set<String> actualDeclarations = new java.util.HashSet<String>(actual.declared());
					expectedDeclarations.remove("position");
					actualDeclarations.remove("position");
					assertEquals(expectedDeclarations, actualDeclarations);
					parents.push(restored);
					++count;
				} else if (event instanceof RunningTemplate.End) {
					parents.pop();
				}
			}
			assertTrue(count >= 4);
			assertEquals(1, parents.size());
			assertTrue(expander.droppedProperties().toString(), expander.droppedProperties().isEmpty());
			System.err.println("[running R2] style roundtrip=" + count + ", dropped=" + expander.droppedProperties());

			final BlockParams params = params(agent, parents.peek());
			final ReplacedRecipe replaced = ReplacedRecipe.freeze(new InlineReplacedBox(new ReplacedParams(), new InlinePos())).orElseThrow();
			final List<SegmentEvent> events = List.of(
					new SegmentEvent.BeginBox(BoxRecipe.freeze(BoxKind.FLOW, params, new FlowPos())),
					new SegmentEvent.Text(17, "ORIGINAL", true), new SegmentEvent.Replaced(replaced),
					new SegmentEvent.Leader("."), new SegmentEvent.Assignment(123), new SegmentEvent.EndBox());
			final ObservingBuilder source = new ObservingBuilder(agent, params);
			new SegmentExecutor(source, 40L).drive(events);
			assertEquals(List.of(40L, 42L), source.anchors);
			for (int i = 0; i < 2; ++i) {
				final ObservingBuilder detached = new ObservingBuilder(agent, params);
				new SegmentExecutor(detached, SegmentExecutor.AnchorMode.NONE).drive(events);
				assertEquals(List.of(-1L, -1L), detached.anchors);
				assertEquals(source.operations, detached.operations);
			}
			assertEquals(List.of("begin", "text:17:ORIGINAL:true", "image", "leader:.", "end"), source.operations);
			assertEquals("ORIGINAL", ((SegmentEvent.Text) events.get(1)).text());
			try {
				new SegmentExecutor(source, SegmentExecutor.AnchorMode.NONE).drive(List.of(new SegmentEvent.Barrier(
						java.util.Optional.empty(), net.zamasoft.foliojet.layout.segment.BarrierReason.UNKNOWN_TYPE)));
				fail("Barrierを黙って再生しない");
			} catch (final IllegalStateException expected) {
				assertTrue(expected.getMessage().contains("barrier"));
			}
		});
		final var result = RunningRenderTest.convert(RunningRenderTest.document(
				"#h{position:running(h);font-size:13pt;opacity:.8;color:rgba(12,34,56,.7);"
				+ "transform:translate(2pt,3pt);text-shadow:1pt 2pt 3pt red;box-shadow:1pt 2pt 3pt blue;"
				+ "background:linear-gradient(red,blue);font-feature-settings:'kern' 0;filter:brightness(.8)}"
				+ "#h span{font-size:120%;opacity:.5;filter:contrast(.9);flex-basis:content}"
				+ "#h::before{content:counter(page) ':' target-counter('#body',page)}",
				"<div id='h'><span>VALUE</span><b>END</b></div><p id='body'>BODY</p>"), ua, false, false, Map.of());
		assertEquals(1, ua.checkedPages);
		RunningRenderTest.assertNoReplayWarnings(result);
	}

	private static final class ObservingBuilder extends DocumentBuilder {
		final List<Long> anchors = new ArrayList<Long>();
		final List<String> operations = new ArrayList<String>();

		ObservingBuilder(final PDFUserAgent ua, final BlockParams params) {
			super(new MeasurePageGenerator(ua, params, 100, 30));
		}

		@Override
		public void startBox(final INonReplacedBox box) {
			this.anchors.add(box.getSourceAnchor());
			this.operations.add("begin");
		}

		@Override
		public void addReplacedBox(final AbstractReplacedBox box) {
			this.anchors.add(box.getSourceAnchor());
			this.operations.add("image");
		}

		@Override
		public void characters(final int offset, final char[] text, final int start, final int length, final boolean fixed) {
			this.operations.add("text:" + offset + ":" + new String(text, start, length) + ":" + fixed);
			java.util.Arrays.fill(text, 'x');
		}

		@Override
		public void addLeader(final String pattern) {
			this.operations.add("leader:" + pattern);
		}

		@Override
		public void endBox() {
			this.operations.add("end");
		}
	}

	public void testPassContextDoesNotChangeDuringReplay() throws Exception {
		final AuditUA ua = new AuditUA(true, agent -> {
			final String before = fingerprint(agent.getPassContext());
			final PageValueSnapshot page = new PageValueSnapshot(agent, agent.getPassContext().getPageSide(), "chapter");
			final CSSStyle container = container(agent);
			final RunningRenderer renderer = new RunningRenderer(agent, page);
			final var content = renderer.prepare(new ElementFunctionValue("h", Mode.FIRST), container);
			assertNotNull(content);
			final var mini = content.layout(params(agent, container), 150, 30);
			final Drawer drawer = new Drawer(0);
			RunningRenderer.draw(mini, drawer, 0, 0);
			final StringBuilder dump = new StringBuilder();
			drawer.dump(dump, "");
			assertTrue(dump.toString(), RunningRenderTest.artifactText(dump.toString()).contains("HEADER"));
			assertEquals("counter/string/buildString/pending/registryを含む全PassContext", before, fingerprint(agent.getPassContext()));
			System.err.println("[running R2] PassContext unchanged; pending=" + agent.getPassContext().getRunningRegistry().pendingCount());
		});
		final var result = RunningRenderTest.convert(RunningRenderTest.document(
				"@page{@top-center{content:element(h)}}#h{position:running(h);counter-reset:page 99;counter-increment:page 7;string-set:title 'BAD'}"
				+ "#h::before{content:counter(page);string-set:title content();counter-increment:page}"
				+ "#body{string-set:title 'GOOD'}#next{break-before:page}",
				"<div id='h'>HEADER</div><p id='body'>BODY</p><p id='next'>NEXT</p>"), ua, true, true, Map.of());
		assertEquals(2, ua.checkedPages);
		assertEquals(1L, ua.getPassContext().getRunningRegistry().assignedCount());
		assertEquals(0, ua.getPassContext().getRunningRegistry().pendingCount());
		assertEquals("GOOD", ua.getPassContext().getStringState().resolve("title", Mode.LAST).value());
		assertEquals("GOOD", ua.getPassContext().getBuildStringState().resolve("title", Mode.LAST).value());
		RunningRenderTest.assertNoReplayWarnings(result);
	}

	public void testSnapshotCopiesPageValuesAndReferenceViewOnlyMarksConsumption() {
		final PDFUserAgent ua = new RunningRenderTest.TestUA();
		ua.getPassContext().getCounterScope(0, true).reset("page", 4);
		ua.getPassContext().getCounterScope(0, true).reset("pages", 12);
		ua.getPassContext().getStringState().assign("title", "TITLE", 1, true);
		final String before = fingerprint(ua.getPassContext());
		final PageValueSnapshot snapshot = new PageValueSnapshot(ua, CSSElement.PAGE_FIRST_LEFT, "chapter");
		assertEquals(before, fingerprint(ua.getPassContext()));
		ua.getPassContext().getCounterScope(0, true).increment("page", 1);
		ua.getPassContext().getStringState().clear("title", 2, true);
		assertEquals(4, snapshot.counter("page"));
		assertEquals(List.of(12), snapshot.counters("pages"));
		assertEquals("TITLE", snapshot.string("title", Mode.START));
		assertTrue(snapshot.left());
		assertTrue(snapshot.first());
		assertFalse(snapshot.right());
		assertFalse(snapshot.single());
		assertEquals("chapter", snapshot.pageName());
		assertTrue(new PageValueSnapshot(ua, CSSElement.PAGE_SINGLE, null).single());

		final PageRef references = new PageRef();
		final URI uri = URI.create("file:///document.html#target");
		references.addFragment(uri, new Counter[] { new Counter("page", 7) });
		references.addFragment(uri, new Counter[] { new Counter("page", 9) });
		references.reset();
		final var fragment = references.getFragment(uri);
		final var second = (PageRef.Fragment) references.getFragments(uri).stream().skip(1).findFirst().orElseThrow();
		assertFalse(fragment.staleConsumed);
		assertEquals(List.of(7), references.counterView(true).counters(uri, "page", false));
		assertTrue(fragment.staleConsumed);
		assertFalse("単数参照は未使用の重複fragmentを消費しない", second.staleConsumed);
		assertEquals(List.of(7, 9), references.counterView(true).counters(uri, "page", true));
		assertTrue(second.staleConsumed);
		references.reset();
		final String stale = fingerprint(references);
		assertTrue(references.counterView(true).counters(uri, "page", true).isEmpty());
		assertEquals("古いfragmentを削除しない", stale, fingerprint(references));
	}

	public void testSvgReplayDoesNotRetainImageMaps() throws Exception {
		final AuditUA ua = new AuditUA(true, agent -> {
			final var imageMaps = agent.getUAContext().getImageMaps();
			final var before = new java.util.HashMap<Object, net.zamasoft.foliojet.ua.ImageMap>(imageMaps);
			final var container = container(agent);
			final var renderer = new RunningRenderer(agent,
					new PageValueSnapshot(agent, agent.getPassContext().getPageSide(), null));
			for (int i = 0; i < 3; ++i) {
				final var content = renderer.prepare(new ElementFunctionValue("h", Mode.FIRST), container);
				assertNotNull(content);
				final var drawer = new Drawer(0);
				RunningRenderer.draw(content.layout(params(agent, container), 100, 30), drawer, 0, 0);
				assertSame(imageMaps, agent.getUAContext().getImageMaps());
				assertEquals(before, imageMaps);
				System.err.println("[running R2] SVG replay imageMaps=" + imageMaps.size() + ", before=" + before.size());
			}
		});
		final String svg = "data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%2216%22%20height=%228%22%3E"
				+ "%3Crect%20width=%2216%22%20height=%228%22/%3E%3C/svg%3E";
		final var result = RunningRenderTest.convert(RunningRenderTest.document(
				"@page{@top-left{content:element(h)}@top-right{content:element(h)}}#h{position:running(h)}"
				+ "#h span{display:inline-block;width:12pt;height:6pt;background-image:url('" + svg + "')}p+p{break-before:page}",
				"<div id='h'><svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' width='16' height='8'>"
				+ "<a xlink:href='https://example.com/'><rect width='16' height='8'/></a>"
				+ "<image width='16' height='8' xlink:href='" + svg + "'/></svg><img src='" + svg + "'><span></span></div>"
				+ "<p>ONE</p><p>TWO</p><p>THREE</p>"), ua, false, false, Map.of());
		assertEquals(3, ua.checkedPages);
		RunningRenderTest.assertNoReplayWarnings(result);
	}

	public void testPlainMarginKeepsLogicalTextReplacement() throws Exception {
		final String logical = "ABC אבג";
		final AuditUA ua = new AuditUA(false, agent -> {
			try {
				final Object box = marginBox(agent, "content:'" + logical + "';font-size:10pt");
				final var place = Class.forName("net.zamasoft.foliojet.css.style.MarginBoxes").getDeclaredMethod("place",
						net.zamasoft.foliojet.ua.UserAgent.class, box.getClass(), double.class, double.class,
						double.class, double.class, Drawer.class, net.zamasoft.foliojet.layout.visitor.Visitor.class);
				place.setAccessible(true);
				final Drawer drawer = new Drawer(0);
				place.invoke(null, agent, box, 0.0, 0.0, 200.0, 30.0, drawer,
						net.zamasoft.foliojet.layout.visitor.ArtifactVisitor.INSTANCE);
				final List<String> replacements = new ArrayList<String>();
				drawer.draw(new net.zamasoft.pdfg2d.gc.NoOpGC(agent.getFontManager()) {
					@Override
					public net.zamasoft.pdfg2d.gc.GC.State beginTextReplacement(final String text) {
						replacements.add(text);
						return net.zamasoft.pdfg2d.gc.GC.NO_OP_STATE;
					}
				});
				System.err.println("[running R2] plain margin beginTextReplacement=" + replacements);
				assertEquals(List.of(logical), replacements);
			} catch (final ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		final var result = RunningRenderTest.convert(RunningRenderTest.document(
				"@page{@top-center{content:'" + logical + "'}}", "<p>BODY</p>"), ua, false, false,
				Map.of("output.pdf.bidi.actual-text", "true", "layout.bidi.paragraph", "true"));
		assertEquals(1, ua.checkedPages);
		RunningRenderTest.assertNoReplayWarnings(result);
	}

	/** privateなマージン組版を、公開APIを増やさず検査します。 */
	static Object marginBox(final PDFUserAgent ua, final String css) throws ReflectiveOperationException {
		final var parsed = com.helger.css.reader.CSSReaderDeclarationList.readFromString(css,
				new com.helger.css.reader.CSSReaderSettings().setBrowserCompliantMode(true));
		assertNotNull(parsed);
		final var declaration = new net.zamasoft.foliojet.css.Declaration();
		for (final var item : parsed.getAllDeclarations()) {
			final var property = net.zamasoft.foliojet.css.property.ElementPropertySet.getInstance().parseDeclaration(
					item.getProperty(), net.zamasoft.foliojet.css.token.Tokens.fromExpression(item.getExpression()),
					ua, ua.getDocumentContext().getBaseURI(), false);
			assertNotNull(item.getProperty(), property);
			declaration.addProperty(property);
		}
		final var create = Class.forName("net.zamasoft.foliojet.css.style.MarginBoxes$Box").getDeclaredMethod("create",
				net.zamasoft.foliojet.ua.UserAgent.class, net.zamasoft.foliojet.css.MarginBoxName.class,
				net.zamasoft.foliojet.css.Declaration.class, RunningRenderer.class);
		create.setAccessible(true);
		final Object box = create.invoke(null, ua, net.zamasoft.foliojet.css.MarginBoxName.TOP_CENTER, declaration,
				new RunningRenderer(ua, new PageValueSnapshot(ua, ua.getPassContext().getPageSide(), null)));
		assertNotNull(box);
		return box;
	}

	public void testTaggedStructureDestinationsBookmarksLinksAndFormsAreNotDuplicated() throws Exception {
		final String css = "@page{@top-center{content:element(h)}}#h{position:running(h)}.next{break-before:page}";
		final String body = "<h1 id='body-heading'>BODYHEADING</h1><p><a href='https://example.com/'>BODYLINK</a></p>"
				+ "<p><input name='body-field' value='BODYFORM'></p><p class='next'>PAGE TWO</p>";
		final String header = "<div id='h'><h2 id='header-heading'>RUNNINGHEADING</h2>"
				+ "<a href='https://example.com/header'>RUNNINGLINK</a><input name='header-field' value='HEADERFORM'></div>";
		final Map<String, String> properties = Map.of("output.pdf.bookmarks", "true", "output.pdf.hyperlinks", "true", "output.pdf.forms", "true");
		final var without = RunningRenderTest.convert(RunningRenderTest.document(css, body), new RunningRenderTest.TestUA(), true, false, properties);
		final var with = RunningRenderTest.convert(RunningRenderTest.document(css, header + body), new RunningRenderTest.TestUA(), true, true, properties);
		final Counts expected = counts(without.pdf());
		final Counts actual = counts(with.pdf());
		System.err.println("[running R2] PDF registrations: without=" + expected + ", with=" + actual);
		assertTrue(expected.structures() > 0);
		assertTrue(expected.destinations() > 0);
		assertTrue(expected.bookmarks() > 0);
		assertTrue(expected.links() > 0);
		assertEquals(expected, actual);
		assertTrue(with.pages().toString(), with.pages().stream().anyMatch(page -> RunningRenderTest.artifactText(page).contains("RUNNINGHEADING")));
		RunningRenderTest.assertNoReplayWarnings(with);
	}

	public void testThousandPagesTimeRatio() throws Exception {
		final String body = "<p>BODY</p>".repeat(1_000);
		final String css = "p+p{break-before:page}.head{position:running(h)}@page{@top-center{content:element(h)}}";
		final String without = RunningRenderTest.document(css, body);
		final String with = RunningRenderTest.document(css, "<span class='head'>HEADER</span>" + body);
		// 初期化とJITの片寄りを減らす。大量文書では表示リストをファイルへ書かない。
		for (final String header : List.of("", "<span class='head'>HEADER</span>")) {
			RunningRenderTest.convert(RunningRenderTest.document(css, header + "<p>BODY</p>".repeat(20)),
					new RunningRenderTest.TestUA(), false, false, Map.of());
		}
		final var baseline = RunningRenderTest.convert(without, new RunningRenderTest.TestUA(), false, false, Map.of());
		final var running = RunningRenderTest.convert(with, new RunningRenderTest.TestUA(), false, false, Map.of());
		try (final var a = Loader.loadPDF(baseline.pdf()); final var b = Loader.loadPDF(running.pdf())) {
			assertEquals(1_000, a.getNumberOfPages());
			assertEquals(1_000, b.getNumberOfPages());
		}
		final double ratio = (double) running.nanos() / baseline.nanos();
		System.err.println("[running R2] 1000 pages: without=" + baseline.nanos() / 1_000_000.0 + "ms, with="
				+ running.nanos() / 1_000_000.0 + "ms, ratio=" + ratio + ", target<=2");
		assertTrue("計測揺れを許した上限3倍: " + ratio, ratio <= 3.0);
		RunningRenderTest.assertNoReplayWarnings(running);
	}

	static CSSStyle container(final PDFUserAgent ua) {
		final CSSStyle style = CSSStyle.getCSSStyle(ua, null, CSSElement.ANON);
		style.set(Display.INFO, DisplayValue.BLOCK_VALUE);
		return style;
	}

	static BlockParams params(final PDFUserAgent ua, final CSSStyle style) {
		final BlockParams params = new BlockParams();
		params.fontStyle = style.getFontStyle();
		params.fontManager = ua.getFontManager();
		params.lineBreakRules = net.zamasoft.foliojet.css.lang.LanguageProfileBundle.getLanguageProfile(null).getTextBreakingRules(style);
		return params;
	}

	/** 本流の最後のvisit直後とendPage直前が、通常再生を挟む観測点です。 */
	static final class AuditUA extends PDFUserAgent {
		private final boolean audit;
		private final Consumer<PDFUserAgent> after;
		int checkedPages;

		AuditUA(final boolean audit, final Consumer<PDFUserAgent> after) {
			this.audit = audit;
			this.after = after;
		}

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.visitor = new PDFVisitor(this) {
				private String before;
				private Map<Object, net.zamasoft.foliojet.ua.ImageMap> beforeImages;

				@Override
				public void nextPage(final PDFGC gc) {
					super.nextPage(gc);
					this.before = null;
					this.beforeImages = null;
				}

				@Override
				public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x, final double y) {
					super.visitBox(transform, box, drawer, x, y);
					if (audit) {
						this.before = fingerprint(getPassContext());
						this.beforeImages = new java.util.HashMap<Object, net.zamasoft.foliojet.ua.ImageMap>(getUAContext().getImageMaps());
					}
				}

				@Override
				public void visitAssignment(final RunningRegistry.Placement placement) {
					super.visitAssignment(placement);
					if (audit) {
						this.before = fingerprint(getPassContext());
						this.beforeImages = new java.util.HashMap<Object, net.zamasoft.foliojet.ua.ImageMap>(getUAContext().getImageMaps());
					}
				}

				@Override
				public void endPage() {
					if (audit) {
						assertNotNull(this.before);
						assertEquals("通常のマージン再生前後", this.before, fingerprint(getPassContext()));
						assertEquals("通常のマージン再生前後のimageMaps", this.beforeImages, getUAContext().getImageMaps());
					}
					after.accept(AuditUA.this);
					++checkedPages;
					super.endPage();
				}
			};
		}
	}

	/** テスト内だけで可変状態の全フィールドを比較し、診断APIだけに検査を依存させません。 */
	private static String fingerprint(final Object value) {
		final StringBuilder out = new StringBuilder();
		append(value, out, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
		return out.toString();
	}

	private static void append(final Object value, final StringBuilder out, final Set<Object> seen) {
		if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean
				|| value instanceof Character || value instanceof Enum<?> || value instanceof URI) {
			out.append('[').append(value).append(']');
			return;
		}
		out.append(value.getClass().getName()).append('@').append(System.identityHashCode(value));
		if (!seen.add(value)) {
			return;
		}
		if (value instanceof Map<?, ?> map) {
			map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey()))).forEach(entry -> {
				append(entry.getKey(), out, seen);
				append(entry.getValue(), out, seen);
			});
		} else if (value instanceof Iterable<?> items) {
			for (final Object item : items) {
				append(item, out, seen);
			}
		} else if (value.getClass().isArray()) {
			for (int i = 0; i < Array.getLength(value); ++i) {
				append(Array.get(value, i), out, seen);
			}
		} else if (value.getClass().getName().startsWith("net.zamasoft.foliojet.")
				&& !value.getClass().getName().contains("$$Lambda$") && !(value instanceof CSSStyle)) {
			try {
				for (Class<?> type = value.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
					for (final Field field : type.getDeclaredFields()) {
						if (!Modifier.isStatic(field.getModifiers())) {
							field.setAccessible(true);
							out.append(field.getName());
							append(field.get(value), out, seen);
						}
					}
				}
			} catch (final IllegalAccessException e) {
				throw new AssertionError(e);
			}
		}
		out.append(';');
	}

	private record Counts(int structures, int destinations, int bookmarks, int annotations, int links, int forms) {
	}

	private static Counts counts(final byte[] pdf) throws Exception {
		try (final var document = Loader.loadPDF(pdf)) {
			final var catalog = document.getDocumentCatalog();
			final var stack = new ArrayDeque<PDStructureNode>();
			assertNotNull(catalog.getStructureTreeRoot());
			stack.push(catalog.getStructureTreeRoot());
			int structures = 0;
			while (!stack.isEmpty()) {
				for (final Object kid : stack.pop().getKids()) {
					if (kid instanceof PDStructureNode node) {
						++structures;
						stack.push(node);
					}
				}
			}
			final COSDictionary names = catalog.getCOSObject().getCOSDictionary(COSName.NAMES);
			final COSDictionary direct = catalog.getCOSObject().getCOSDictionary(COSName.DESTS);
			final int destinations = nameCount(names == null ? null : names.getDictionaryObject(COSName.DESTS))
					+ (direct == null ? 0 : direct.size());
			int bookmarks = 0;
			final var outline = catalog.getDocumentOutline();
			final var outlines = new ArrayDeque<org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem>();
			if (outline != null && outline.getFirstChild() != null) {
				outlines.push(outline.getFirstChild());
			}
			while (!outlines.isEmpty()) {
				final var item = outlines.pop();
				++bookmarks;
				if (item.getNextSibling() != null) { outlines.push(item.getNextSibling()); }
				if (item.getFirstChild() != null) { outlines.push(item.getFirstChild()); }
			}
			int annotations = 0, links = 0, forms = 0;
			for (final var page : document.getPages()) {
				final COSArray array = page.getCOSObject().getCOSArray(COSName.ANNOTS);
				annotations += array == null ? 0 : array.size();
				for (final var annotation : page.getAnnotations()) {
					if ("Link".equals(annotation.getSubtype())) { ++links; }
				}
			}
			if (catalog.getAcroForm() != null) {
				for (final var field : catalog.getAcroForm().getFieldTree()) { ++forms; }
			}
			return new Counts(structures, destinations, bookmarks, annotations, links, forms);
		}
	}

	private static int nameCount(final COSBase value) {
		if (!(value instanceof COSDictionary dictionary)) {
			return 0;
		}
		final COSArray names = dictionary.getCOSArray(COSName.NAMES);
		int count = names == null ? 0 : names.size() / 2;
		final COSArray kids = dictionary.getCOSArray(COSName.KIDS);
		if (kids != null) {
			for (int i = 0; i < kids.size(); ++i) {
				count += nameCount(kids.getObject(i));
			}
		}
		return count;
	}
}
