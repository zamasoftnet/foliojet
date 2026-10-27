package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Absolutely positioned boxes in a page float or a margin note inside an absolutely positioned box (2026-10-27,
 * sweep seed 43575183, WILD). The outer box binds its content only when the page is finalized, so the float reached
 * the page after the page's floats were listed for finalization: it was drawn, but the boxes in it kept no content.
 */
public class PageFloatInAbsoluteTest extends TestCase {
	public void testTopFloat() throws Exception {
		final String dump = layout("", "<div style=\"position: absolute\"><div style=\"float: top\"><div style=\""
				+ "position: absolute\">T6</div>T7</div></div>");
		assertTrue(dump, dump.contains("\"T6\""));
		assertTrue(dump, dump.contains("\"T7\""));
	}

	public void testTopFloatInVerticalText() throws Exception {
		final String dump = layout("html { writing-mode: vertical-rl }", "<div style=\"position: absolute\"><div "
				+ "style=\"float: top\"><div style=\"position: absolute\">T6</div>T7</div></div>");
		assertTrue(dump, dump.contains("\"T6\""));
	}

	/** One level deeper, and a float beside the absolutely positioned box. */
	public void testNested() throws Exception {
		final String dump = layout("", "<div style=\"position: absolute\"><div style=\"position: absolute\"><div "
				+ "style=\"float: top\"><div style=\"float: left\">T8</div><div><div style=\"position: absolute\">T6"
				+ "</div></div>T7</div></div></div>");
		assertTrue(dump, dump.contains("\"T6\""));
		assertTrue(dump, dump.contains("\"T8\""));
	}

	public void testMarginNote() throws Exception {
		final String dump = layout("", "<div style=\"position: absolute\"><div style=\"float: -cssj-note-start; "
				+ "width: 30pt\"><div style=\"position: absolute\">T6</div>T7</div></div>");
		assertTrue(dump, dump.contains("\"T6\""));
	}

	/** The display lists of all pages. */
	private static String layout(final String css, final String body) throws Exception {
		final File dir = Files.createTempDirectory("page-float-in-absolute").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 150pt; margin: 5pt 40pt } body { margin: 0; font: 11pt/1.2 serif } div { margin: 0 } "
				+ css + "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final StringBuilder all = new StringBuilder();
		for (int n = 1;; ++n) {
			final File page = new File(dir, String.format("page-%04d.txt", n));
			if (!page.exists()) {
				break;
			}
			all.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return all.toString();
	}
}
