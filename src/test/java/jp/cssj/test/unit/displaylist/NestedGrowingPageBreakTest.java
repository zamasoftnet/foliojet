package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
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
 * Termination regression for nested, continually growing page breaks
 * (2026-10-07, reduced strict sweep seed 12453214).
 * A float continuation inside vertical columns was rebuilt to the same dimensions each time.
 * Resuming a page break triggered another break, growing the cursor by 43.2 pt per step and nesting
 * 53,000 levels until {@code StackOverflowError}. Since the cursor differed each time,
 * the fingerprint counting identical page breaks did not catch it.
 *
 * <p>
 * The page break was abandoned (placed with overflow, T168 at x=289.8 on 120 pt paper) until 2026-10-28. Then the
 * continuation stopped being rebuilt at the same size: a forced split of the grid (width 0pt, whose items paint
 * nothing past the cut line) takes the slack path and consumes the cut ({@code GridBox.split}, wild seed 45210105),
 * so the document ends without the stall alarm, with every word on the paper. {@link AlternatingPageBreakLivelockTest}
 * and {@link TallBlockPaginationTest} still exercise the alarm.
 * </p>
 */
public class NestedGrowingPageBreakTest extends TestCase {
	private static final String HTML = """
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{font:normal 6pt/1.2 serif;writing-mode:vertical-lr}
			p,div,td{margin:0}
			</style></head><body>
			<div style="width:51pt">

			</div>
			<div style="column-count:2">

			<div style="float:left">

			<input size="11" style="font-size:medium;width:66pt;line-height:inherit" />
			<div style="display:grid;width:0pt;grid-template-columns:fit-content(0pt) minmax(0pt,1fr)">
			<p><span style="display:inline-block;width:111pt;height:46pt"></span></p>

			<div style="width:23pt">
			T164
			<div style="width:calc(35% + 8em);min-width:8em;max-width:90%">

			T165

			</div>

			T168

			</div>

			<div>

			</div>

			</div>
			</div>

			<div style="writing-mode:horizontal-tb;min-width:8em">

			</div>

			</div>
			</body></html>
			""";

	public NestedGrowingPageBreakTest(final String name) {
		super(name);
	}

	public void testNestedGrowingBreaksTerminate() throws Exception {
		final File dir = Files.createTempDirectory("nested-growing-page-break").toFile();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try (AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			session.property("output.page-limit", "20");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///nested-growing-page-break.html"), "text/html", null);
		} finally {
			session.close();
		}
		assertTrue("PDF が出ていない", out.size() > 0);
		// Every word once, on the 110pt type area (the abandoned break put T168 at x=289.8).
		final Map<String, Integer> draws = new TreeMap<>();
		final Pattern text = Pattern.compile("x=(-?[\\d.]+) y=-?[\\d.]+ Text\\[\"(T\\d+)\"");
		int pages = 0;
		for (File page; (page = new File(dir, String.format("page-%04d.txt", pages + 1))).exists(); ++pages) {
			final Matcher m = text.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				draws.merge(m.group(2), 1, Integer::sum);
				assertTrue(m.group(0), Double.parseDouble(m.group(1)) < 110);
			}
		}
		assertTrue("pages=" + pages, pages <= 4);
		assertEquals("{T164=1, T165=1, T168=1}", draws.toString());
	}
}
