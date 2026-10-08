package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A row flex item broken across many pages keeps breaking on its last page (2026-10-09, qiita-article). Each page of a
 * 66-line {@code pre} with a background keeps 9 lines of 21pt on a 200pt page and leaves 11pt, which the line ledger
 * of the continuation subtracted as if consumed; by the last page the ledger said 186pt while the item was 252pt, the
 * fragment was kept whole and lines 64 to 66 ran below the page (in qiita-article half of the text, past the
 * livelock guard).
 */
public class FlexRowSplitCoverTest extends TestCase {
	private static final Pattern TEXT_Y = Pattern.compile("y=([-\\d.]+) Text\\[\"([^\"]*)\"");

	public void testLastPageStillBreaks() throws Exception {
		this.check(66, 8);
	}

	/**
	 * The ledger falls behind by 11pt a page, so on a long item it lags by more than a page (2026-10-09, sphinx-api:
	 * 875pt after 105 pages): 400 lines take 45 pages, none below the page (42 pages and lines down to 630pt before).
	 */
	public void testLagLongerThanAPage() throws Exception {
		this.check(400, 45);
	}

	private void check(final int lineCount, final int pageCount) throws Exception {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= lineCount; ++i) {
			lines.append("line ").append(i).append('\n');
		}
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 0 } body { margin: 0; font: 10pt/21pt serif }"
				+ " pre { margin: 0; font: inherit; background: #ddd }</style></head><body>"
				+ "<div style=\"display: flex\"><div><pre>" + lines + "</pre></div></div></body></html>";
		final File dir = Files.createTempDirectory("flex-row-split-cover").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertEquals("pages", pageCount, pages.length);
		int count = 0;
		for (final File page : pages) {
			final Matcher m = TEXT_Y.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				if (m.group(2).matches("\\d+")) {
					++count;
					assertTrue(page.getName() + " line " + m.group(2) + " below the page at " + m.group(1),
							Double.parseDouble(m.group(1)) + 21 <= 200 + 0.01);
				}
			}
		}
		assertEquals("lines", lineCount, count);
	}
}
