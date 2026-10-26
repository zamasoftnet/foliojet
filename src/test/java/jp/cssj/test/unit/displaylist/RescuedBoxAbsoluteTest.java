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
 * Verifies that the content of an absolutely positioned box is drawn when no ordinary pass over the page reaches the
 * box (2026-10-26, sweep WILD seeds 42500023, 42500043, 42500063, 42500095, 42500131).
 *
 * <p>
 * An absolutely positioned box lays its content out in its own {@code finishLayoutSelf}, which the page's finishLayout
 * pass reaches through the boxes holding it. Two shapes left it out:
 * </p>
 *
 * <ul>
 * <li>A box that does not split along the page axis and is longer than the page (here a vertical box in a
 * horizontal document) is cut into rescue fragments, which replace it in its container. The fragments pushed nothing
 * to finishLayout, so the absolutely positioned box in it was drawn empty on every fragment. The first fragment now
 * finishes the original box ({@code VisualRescueBox.pushFinishLayoutChildren}).</li>
 * <li>An inline-level absolutely positioned box alone in a line on the last page: the line takes no room, so the
 * page painted nothing and was dropped with the box ({@code TextBlockBox.paintsAnything}).</li>
 * </ul>
 */
public class RescuedBoxAbsoluteTest extends TestCase {
	private static List<String> pages(final String html) throws Exception {
		final Path dir = Files.createTempDirectory("rescued-box-absolute");
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

	/**
	 * The pages (0-based) that draw {@code token}, once for each time it is drawn, leaving out the copies continuation
	 * fragments draw as artifacts.
	 */
	private static List<Integer> drawn(final List<String> pages, final String token) {
		final String needle = "Text[\"" + token + "\"";
		final List<Integer> found = new ArrayList<>();
		for (int i = 0; i < pages.size(); ++i) {
			for (final String line : pages.get(i).split("\n")) {
				if (line.contains(needle) && !line.contains(" artifact ")) {
					found.add(i);
				}
			}
		}
		return found;
	}

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
			+ "@page{size:200pt 200pt;margin:10pt} body{margin:0;font:10pt/12pt serif} p{margin:0}</style></head><body>";

	/**
	 * A vertical box with a label in its corner, longer than the page of a horizontal document: it moves to page 2
	 * and is cut there and on page 3.
	 */
	public void testLabelInVerticalBoxLongerThanThePage() throws Exception {
		final List<String> pages = pages(HEAD + "<p>Intro</p>"
				+ "<div style='writing-mode:vertical-rl;position:relative;height:260pt;border:1pt solid'>"
				+ "<div style='position:absolute;top:0;left:0;writing-mode:horizontal-tb'>LABEL</div>"
				+ "<p>" + "Vertical text goes on. ".repeat(6) + "</p></div><p>After</p></body></html>");
		assertEquals(String.join("\n----\n", pages), List.of(1), drawn(pages, "LABEL"));
	}

	/**
	 * The shape of the sweep: a vertical flex container in a horizontal document, made longer than the page by its
	 * first item, holding an absolutely positioned paragraph in its second item.
	 */
	public void testAbsoluteInVerticalFlexContainerLongerThanThePage() throws Exception {
		final List<String> pages = pages(HEAD + "<div style='display:flex;writing-mode:vertical-rl'>"
				+ "<div style='width:38pt'><p><span style='display:inline-block;width:20pt;height:300pt'></span></p></div>"
				+ "<div><div style='position:absolute;top:25pt;left:17pt'><p>LABEL</p></div></div></div></body></html>");
		assertEquals(String.join("\n----\n", pages), List.of(0), drawn(pages, "LABEL"));
	}

	/** A paragraph holding only a stamp positioned at the page corner goes to page 2 after a full page. */
	public void testInlineAbsoluteAloneOnTheLastPage() throws Exception {
		final List<String> pages = pages("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 200pt;margin:0} body{margin:0;font:10pt/12pt serif} p{margin:0}</style></head><body>"
				+ "<p>first</p><div style='height:190pt'></div>"
				+ "<p><span style='position:absolute;bottom:10pt;right:10pt'>STAMP</span></p></body></html>");
		assertEquals(String.join("\n----\n", pages), List.of(1), drawn(pages, "STAMP"));
	}
}
