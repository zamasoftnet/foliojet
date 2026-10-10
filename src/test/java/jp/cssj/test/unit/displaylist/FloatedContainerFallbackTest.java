package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A floated flex or grid container falls back to a block (warning 2823). With text directly inside and an auto width,
 * its shrink-to-fit measurement replayed the content in a flex (grid) container, since the block kept the container's
 * params; the text opened an anonymous item that the recording never had, and the conversion failed (TwoPass NO_RANGE,
 * 2026-10-10, A/B ff-e). Direct text is laid out as the same float written as a block, the paragraph after it beside the
 * float (as in Chrome); with an element before the text, the items are stacked, as warning 2823 says.
 */
public class FloatedContainerFallbackTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static String document(final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 200pt; margin: 0 }
				body { margin: 0; font-size: 10pt; line-height: 20pt }
				p { margin: 0 }
				</style></head><body><p>before</p>%s<p>after</p></body></html>
				""".formatted(body);
	}

	public void testDirectTextAsTheBlockFloat() throws Exception {
		final String block = convert("block", document("<div style=\"float: left\">Open</div>"));
		assertTrue("後ろの段落が float の横:\n" + block, x(block, "after") > x(block, "Open"));
		for (final String display : new String[] { "flex", "grid" }) {
			final String page = convert(display,
					document("<div style=\"display: " + display + "; float: left\">Open</div>"));
			assertEquals(display + " の float は block の float と同じ組み", block, page);
		}
	}

	public void testItemsStacked() throws Exception {
		for (final String display : new String[] { "flex", "grid" }) {
			final String page = convert(display + "-items",
					document("<div style=\"display: " + display + "; float: left\"><span>x</span>Open</div>"));
			assertTrue(display + " の項目は縦に積む:\n" + page, y(page, "Open") > y(page, "x"));
			assertTrue(display + " の後ろの段落が float の横:\n" + page, x(page, "after") > x(page, "Open"));
		}
	}

	/**
	 * The block keeps what display: block gives it (the codex review of 2026-10-10): the element, so string-set takes
	 * the uppercased text it draws into the running head, and the text settings (text-justify: none keeps the natural
	 * space under text-align-last: justify). Params frozen and materialized lost the element, and the head read "a b".
	 */
	public void testElementAndTextSettingsAsTheBlock() throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 120pt; margin: 20pt; @top-center { content: string(h) } }
				body { margin: 0; font: 10pt/1.2 serif }
				.f { display: %s; float: left; width: 140pt; text-align: justify; text-align-last: justify;
					text-justify: none; text-transform: uppercase; string-set: h content() }
				</style></head><body><div class="f">a b</div></body></html>
				""";
		final String block = convert("settings-block", html.formatted("block"));
		assertFalse("柱は描いた文字(大文字):\n" + block, block.contains("Text[\"a\""));
		assertTrue("本文の A と B は自然な空き:\n" + block, x(block, "B") - x(block, "A") < 15);
		for (final String display : new String[] { "flex", "grid" }) {
			assertEquals(display + " の float は block の float と同じ組み", block,
					convert("settings-" + display, html.formatted(display)));
		}
	}

	private static double x(final String page, final String text) {
		return coordinate(page, text, 1);
	}

	private static double y(final String page, final String text) {
		return coordinate(page, text, 2);
	}

	private static double coordinate(final String page, final String text, final int group) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(group));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Converts and returns the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/floated-container-fallback/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "floated-container-fallback-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		Arrays.sort(pages);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
