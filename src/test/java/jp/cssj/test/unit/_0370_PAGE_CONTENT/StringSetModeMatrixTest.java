package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 頁1の二代入と頁2への継承を実変換で検証します。 */
public class StringSetModeMatrixTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final Pattern TEXT = Pattern.compile("Text\\[\\\"([^\\\"]*)\\\"");

	record Message(short code, String detail) {
	}

	record Conversion(int pdfBytes, List<String> pages, List<Message> messages) {
	}

	public void testFourModesAcrossTwoPages() throws Exception {
		final String[] modes = { "first", "start", "last", "first-except" };
		final String[] firstPage = { "HEADERALPHA", "HEADERALPHA", "HEADERBETA", "" };
		for (int i = 0; i < modes.length; ++i) {
			final Conversion result = convert(
					"@page{@top-center{content:string(h," + modes[i] + ");font-size:9pt}}"
					+ "#a{string-set:h 'HEADERALPHA'}#b{string-set:h attr(data-heading)}"
					+ "#c{break-before:page}",
					"<div id='a'>A</div><div id='b' data-heading='HEADERBETA'>B</div><div id='c'>C</div>");
			assertTrue(result.pdfBytes() > 0);
			assertEquals(result.pages().toString(), 2, result.pages().size());
			// 頁先頭の要素が代入元ならstartもfirstを選ぶ。
			assertEquals(modes[i] + " / page 1", firstPage[i], headerText(result.pages().get(0)));
			assertEquals(modes[i] + " / page 2", "HEADERBETA", headerText(result.pages().get(1)));
			assertTrue(result.pages().get(0).contains("A"));
			assertTrue(result.pages().get(0).contains("B"));
			assertTrue(result.pages().get(1).contains("C"));
		}
	}

	/** content() の遅延完成と、build 時解決済みの値が同じ頁の文書順に並ぶことを確認します。 */
	public void testContentAndLiteralAssignmentsShareDocumentOrder() throws Exception {
		final Conversion result = convert(
				"@page{@top-left{content:'F[' string(h,first) ']'}"
				+ "@top-right{content:'L[' string(h,last) ']'}}"
				+ "#a{string-set:h content()}#b{string-set:h 'LASTVALUE'}#c{break-before:page}",
				"<div id='a'>FIRSTVALUE</div><div id='b'>B</div><div id='c'>C</div>");
		assertEquals(result.pages().toString(), 2, result.pages().size());
		assertTrue(result.pages().get(0), result.pages().get(0).contains("F[FIRSTVALUE]"));
		assertTrue(result.pages().get(0), result.pages().get(0).contains("L[LASTVALUE]"));
		assertTrue(result.pages().get(1), result.pages().get(1).contains("F[LASTVALUE]"));
		assertTrue(result.pages().get(1), result.pages().get(1).contains("L[LASTVALUE]"));
	}

	/** 疑似要素からの string-set は order(-1)を共有するが、例外にならず値が届く。 */
	public void testPseudoElementAssignmentsDoNotCollide() throws Exception {
		final Conversion result = convert(
				"@page{@top-center{content:'H[' string(h) ']'}}p::before{content:'X';string-set:h 'PSEUDO'}",
				"<p>1</p><p>2</p><p>3</p>");
		assertTrue(result.pdfBytes() > 0);
		assertEquals(result.pages().toString(), 1, result.pages().size());
		assertTrue(result.pages().get(0), result.pages().get(0).contains("H[PSEUDO]"));
	}

	/**
	 * 表は全行を読んでから配置するので、後の行のリテラル代入が前の行の content() 代入より
	 * 先に build される。所属頁への draw 時登録が無いと最後の頁で後の値が消える。
	 */
	public void testLaterLiteralSurvivesDelayedContentAcrossPages() throws Exception {
		final Conversion result = convert(
				"@page{size:120pt 70pt;margin:10pt}@page{@top-left{content:'L[' string(h,last) ']'}}"
				+ "tr{page-break-after:always}#r2{string-set:h content()}#r3{string-set:h 'B'}",
				"<table><tr id='r1'><td>one</td></tr><tr id='r2'><td>A</td></tr><tr id='r3'><td>three</td></tr></table>");
		System.err.println("[running R1a] table pages=" + result.pages().size());
		final String lastPage = result.pages().get(result.pages().size() - 1);
		assertTrue(lastPage, lastPage.contains("L[B]"));
		for (final String page : result.pages()) {
			if (page.contains("Text[\"A\"") && !page.contains("Text[\"three\"")) {
				assertTrue(page, page.contains("L[A]"));
			}
		}
	}

	private static String headerText(final String page) {
		final StringBuilder result = new StringBuilder();
		final Matcher matcher = Pattern.compile("HEADER(?:ALPHA|BETA)").matcher(page);
		while (matcher.find()) {
			result.append(matcher.group());
		}
		return result.toString();
	}

	public void testReplacedStringSetDoesNotReadParentText() throws Exception {
		final Conversion result = convert("@page{@top-center{content:'VALUE[' string(h,last) ']'}}"
				+ "img{width:8pt;height:8pt;string-set:h content()}",
				"<p>LEFT<img src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%221%22 height=%221%22/%3E'>RIGHT</p>");
		final Matcher value = Pattern.compile("VALUE\\[([^]]*)\\]").matcher(String.join("", result.pages()));
		assertTrue(result.pages().toString(), value.find());
		assertFalse(value.group(), value.group(1).contains("LEFT"));
		assertFalse(value.group(), value.group(1).contains("RIGHT"));
	}

	public void testContentsStringSetUsesOwnTextAndFirstChildPlacement() throws Exception {
		final Conversion result = convert("@page{@top-center{content:'VALUE[' string(h,last) ']'}}"
				+ ".source{display:contents;string-set:h content()}#next{break-before:page}",
				"<div>LEFT<div class='source'><p id='next'>OWN<span>TEXT</span></p></div>RIGHT</div>");
		assertEquals(result.pages().toString(), 2, result.pages().size());
		assertTrue(result.pages().get(0), result.pages().get(0).contains("VALUE[]"));
		assertTrue(result.pages().get(1), result.pages().get(1).contains("VALUE[OWNTEXT]"));
	}

	public void testImageLiteralAfterParentFirstPageWasCommitted() throws Exception {
		final Conversion result = convert("@page{@top-center{content:'VALUE[' string(h,last) ']'}}"
				+ "p{line-height:12pt}img{width:8pt;height:8pt;string-set:h 'IMAGE'}",
				"<p>" + "EARLY<br>".repeat(40)
				+ "<img src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%221%22 height=%221%22/%3E'>TAIL</p>");
		assertTrue(result.pages().toString(), result.pages().size() > 1);
		assertTrue(result.pages().toString(), result.pages().stream().anyMatch(
				page -> page.contains("TAIL") && page.contains("VALUE[IMAGE]")));
	}

	public void testBuildAheadValuesSurviveTwoEarlierPageCommits() {
		final var state = new net.zamasoft.foliojet.ua.PassContext().getBuildStringState();
		state.assign("h", "A", 1, false);
		state.assign("h", "B", 2, false);
		state.assign("h", "C", 3, false);
		state.begin("content", 1);
		state.begin("content", 2);
		state.begin("content", 3);
		for (long order = 1; order <= 2; ++order) {
			state.complete("h", order == 1 ? "A" : "B", order);
			state.complete("content", order == 1 ? "A" : "B", order);
			state.endPage();
			assertEquals("C", state.resolve("h", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value());
		}
		state.complete("content", "C", 3);
		assertEquals("C", state.resolve("content", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value());
		state.endPage();
		assertEquals("C", state.resolve("content", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.FIRST_EXCEPT).value());
	}

	public void testBodyLastAfterThreeBuildAheadTableRows() throws Exception {
		for (final String third : List.of("'C'", "content()")) {
			final Conversion result = convert("@page{size:120pt 70pt;margin:10pt}"
					+ "tr{page-break-after:always}#a{string-set:h 'A'}#b{string-set:h 'B'}"
					+ "#c{string-set:h " + third + "}#probe::before{content:'VALUE[' string(h,last) ']'}",
					"<table><tr id='a'><td>A</td></tr><tr id='b'><td>B</td></tr>"
					+ "<tr id='c'><td>C</td></tr></table><p id='probe'>PROBE</p>");
			System.err.println("[running R1b #3] table rows / " + third + ": " + result.pages());
			// content()はCの頁が確定してから使える。リテラルはbuild時からCを保つ。
			if ("'C'".equals(third)) {
				assertTrue(result.pages().toString(), String.join("", result.pages()).contains("VALUE[C]"));
			}
		}
	}

	/** R1a の解析試験でも使う、外部資源を必要としない二段階の変換ヘルパーです。 */
	static Conversion convert(final String css, final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:240pt 180pt;margin:30pt}html,body{margin:0;font-size:10pt}"
				+ css + "</style></head><body>" + body + "</body></html>";
		final File dumpDir = Files.createTempDirectory("foliojet-running-r1a-").toFile();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<Message> messages = new ArrayList<Message>();
		try {
			final DirectSession session = (DirectSession) new DirectDriver()
					.getSession(URI.create("copper:direct:"), null);
			try (AutoCloseable scope = DisplayListDumper.scopedDir(dumpDir.getPath())) {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler((code, args, text) -> messages.add(
						new Message(code, Arrays.toString(args) + " " + text)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///running-r1a.html"), "text/html", "UTF-8");
			} finally {
				session.close();
			}
			final File[] pages = dumpDir.listFiles((dir, name) -> name.startsWith("page-") && name.endsWith(".txt"));
			assertNotNull(pages);
			Arrays.sort(pages, Comparator.comparing(File::getName));
			final List<String> texts = new ArrayList<String>();
			for (final File page : pages) {
				final String displayList = Files.readString(page.toPath(), StandardCharsets.UTF_8);
				final StringBuilder text = new StringBuilder();
				final Matcher matcher = TEXT.matcher(displayList);
				while (matcher.find()) {
					text.append(matcher.group(1));
				}
				texts.add(text.toString());
				System.err.println("[running R1a] " + page.getName() + ": " + text + "\n" + displayList);
			}
			System.err.println("[running R1a] PDF bytes=" + out.size() + ", messages=" + messages);
			return new Conversion(out.size(), List.copyOf(texts), List.copyOf(messages));
		} finally {
			final File[] files = dumpDir.listFiles();
			if (files != null) {
				for (final File file : files) {
					Files.deleteIfExists(file.toPath());
				}
			}
			Files.deleteIfExists(dumpDir.toPath());
		}
	}
}
