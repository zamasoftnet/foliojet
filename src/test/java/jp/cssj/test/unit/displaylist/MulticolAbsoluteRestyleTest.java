package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies that an absolutely positioned box inside multi-column layout survives when its columns are laid out again
 * on the next page (2026-10-26, sweep WILD seed 42500097).
 *
 * <p>
 * Laying a box out again on the next page replays its container. A plain {@code FlowContainer} lays its contents out
 * again in itself and keeps its absolutes where they are ({@code restyleAbsolutes=false}). Columns are laid out again
 * into one fresh column and the old columns are dropped, but they were replayed with the same flag, so their absolutes
 * were dropped with them and nothing of the box was drawn. Both column replays had the defect:
 * {@code ColumnsContainer.restyle} (a closed box, here a float moved whole to the next page) and the worklist's
 * {@code MulticolRestyleScope} (an open chain, here a block kept together with {@code break-inside: avoid}).
 * </p>
 */
public class MulticolAbsoluteRestyleTest extends TestCase {
	private static List<String> pages(final String html) throws Exception {
		final Path dir = Files.createTempDirectory("multicol-absolute-restyle");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final List<String> pages = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (final Path page : files.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList()) {
				pages.add(Files.readString(page));
			}
		}
		return pages;
	}

	/** The pages (0-based) that draw {@code token}, once for each time it is drawn. */
	private static List<Integer> drawn(final List<String> pages, final String token) {
		final String needle = "Text[\"" + token + "\"";
		final List<Integer> found = new ArrayList<>();
		for (int i = 0; i < pages.size(); ++i) {
			for (int at = pages.get(i).indexOf(needle); at >= 0; at = pages.get(i).indexOf(needle, at + 1)) {
				found.add(i);
			}
		}
		return found;
	}

	/**
	 * A float holding two balanced columns does not fit beside the content before it and moves whole to page 2
	 * ({@code ColumnsContainer.restyle}). The table fills the second column; with one column the container stays a
	 * plain {@code FlowContainer} and kept its absolutes.
	 */
	public void testFloatWithBalancedColumnsMovedToTheNextPage() throws Exception {
		final List<String> pages = pages("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:120pt 400pt;margin:5pt}"
				+ "body{margin:0;font:13pt/1.2 serif;writing-mode:vertical-rl}"
				+ "p,div,td{margin:0;padding:0} td{border:1pt solid black}</style></head><body>"
				+ "<div style='display:inline-block;width:71pt'></div><ol style='list-style:none'><li>T25</li></ol>"
				+ "<div style='float:left;width:31%;min-width:8em'>"
				+ "<div style='margin:3pt;padding:1pt;border:2pt solid black'><div style='column-count:2;column-gap:11pt'>"
				+ "<div style='position:absolute;top:18pt;left:9pt;writing-mode:vertical-rl;width:97pt'><p>T34</p></div>"
				+ "<table><tr><td colspan='2'></td></tr><tr><td colspan='2'>T44</td></tr></table>"
				+ "</div></div></div></body></html>");
		assertEquals(String.join("\n----\n", pages), List.of(1), drawn(pages, "T44"));
		assertEquals("the absolutely positioned box moves with its float\n" + String.join("\n----\n", pages), List.of(1),
				drawn(pages, "T34"));
	}

	/**
	 * The same in a common shape: a sidebar kept together with {@code break-inside: avoid}, a list in two columns and a
	 * "NEW" badge, which does not fit below the text on page 1.
	 */
	public void testSidebarWithColumnsMovedToTheNextPage() throws Exception {
		final String text = "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt"
				+ " ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris"
				+ " nisi ut aliquip ex ea commodo consequat.</p>";
		final StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 420pt;margin:20pt} body{margin:0;font:10pt/1.4 serif}</style></head><body>");
		html.append(text.repeat(4)).append("<aside style='float:right;width:45%;break-inside:avoid;position:relative;"
				+ "border:1pt solid;padding:4pt'><h4 style='margin:0'>Related</h4>"
				+ "<ul style='column-count:2;margin:0;padding-left:12pt'>"
				+ "<div style='position:absolute;top:2pt;right:2pt;font:bold 8pt sans-serif'>NEW</div>");
		for (int i = 1; i <= 14; ++i) {
			html.append("<li>Item ").append(i).append("</li>");
		}
		html.append("</ul></aside>").append(text.repeat(2)).append("</body></html>");
		final List<String> pages = pages(html.toString());
		assertEquals(String.join("\n----\n", pages), List.of(1), drawn(pages, "Related"));
		assertEquals("the badge moves with its sidebar\n" + String.join("\n----\n", pages), List.of(1),
				drawn(pages, "NEW"));
	}

	/**
	 * Columns kept together with {@code break-inside: avoid} start near the end of page 1 and are laid out again on
	 * page 2 as an open chain ({@code MulticolRestyleScope}).
	 */
	public void testColumnsKeptTogetherMovedToTheNextPage() throws Exception {
		final StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 200pt;margin:10pt} body{margin:0;font:10pt/12pt serif} p{margin:0}"
				+ "</style></head><body>");
		for (int i = 0; i < 9; ++i) {
			html.append("<p>before").append(i).append("</p>");
		}
		html.append("<div style='break-inside:avoid;column-count:2'>"
				+ "<div style='position:absolute;top:0;right:0'>LBL</div>");
		for (char c = 'a'; c <= 'p'; ++c) {
			html.append("<p>").append(c).append("</p>");
		}
		html.append("</div></body></html>");
		final List<String> pages = pages(html.toString());
		assertEquals(String.join("\n----\n", pages), List.of(1), drawn(pages, "a"));
		assertEquals("the absolutely positioned label moves with its columns\n" + String.join("\n----\n", pages),
				List.of(1), drawn(pages, "LBL"));
	}
}
