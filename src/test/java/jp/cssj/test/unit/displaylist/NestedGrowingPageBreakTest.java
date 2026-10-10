package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Termination regression for nested, continually growing page breaks
 * (2026-10-07, reduced strict sweep seed 12453214).
 * A float continuation inside vertical columns was rebuilt to the same dimensions each time.
 * Resuming a page break triggered another break, growing the cursor by 43.2 pt per step and nesting
 * 53,000 levels until {@code StackOverflowError}. Since the cursor differed each time,
 * the fingerprint counting identical page breaks did not catch it.
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

			<input size="11" style="font-size:medium;width:66pt" />
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

	public void testNestedGrowingBreaksAreAbandoned() throws Exception {
		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///nested-growing-page-break.html"), "text/html", null);
		} finally {
			session.close();
		}
		assertTrue("PDF が出ていない", out.size() > 0);
		// Terminate by abandoning the page break (place with overflow).
		assertTrue("the nested growing page break was not abandoned",
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get() > alarms);
	}
}
