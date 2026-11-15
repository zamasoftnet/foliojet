package jp.cssj.test.unit._0415_column_fill;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
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
 * Balancing cuts a paragraph only where the actual cut keeps its orphans and widows (2026-11-15, publishing report
 * TECH-20261115-001, the book's last chapter). Balancing took the boundary after a paragraph's first line; the actual
 * cut moved that line to the next column, and the last column ran a line past the balanced height into what follows
 * the columns, there a column-span: all equation.
 */
public class BalanceKeepsOrphansAndWidowsTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("y=([-\\d.]+) Text\\[\"(.*?)\" asc=([\\d.]+) desc=([\\d.]+)");

	private static String paragraph(final String name, final int lines) {
		final StringBuilder p = new StringBuilder("<p>");
		for (int i = 1; i <= lines; ++i) {
			p.append(i > 1 ? "<br />" : "").append(name).append(i);
		}
		return p.append("</p>").toString();
	}

	/**
	 * The bottom of the lowest text before {@code NEXT} less the top of {@code NEXT}, in pt.
	 */
	private static double overrun(final String body) throws Exception {
		final String doc = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page{size:300pt 400pt;margin:20pt} body{margin:0;font:10pt/20pt serif} p{margin:0;orphans:2;widows:2}"
				+ " .c{column-count:2;column-gap:20pt} .span{column-span:all}</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("balance-orphans-widows").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), doc, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(pages);
		assertEquals(1, pages.length);
		double bottom = Double.NEGATIVE_INFINITY, next = Double.NaN;
		final Matcher m = TEXT.matcher(Files.readString(pages[0].toPath(), StandardCharsets.UTF_8));
		while (m.find()) {
			final double y = Double.parseDouble(m.group(1));
			if ("NEXT".equals(m.group(2))) {
				next = y;
			} else if (m.group(2).startsWith("A") || m.group(2).startsWith("B")) {
				bottom = Math.max(bottom, y + Double.parseDouble(m.group(3)) + Double.parseDouble(m.group(4)));
			}
		}
		assertFalse(Double.isNaN(next));
		return bottom - next;
	}

	/** Ten lines in two columns of five: the fifth would be the second paragraph's first line alone. */
	public void testOrphansAfterTheBalancedCut() throws Exception {
		final double over = overrun("<div class=\"c\">" + paragraph("A", 4) + paragraph("B", 6) + "</div><div>NEXT</div>");
		assertTrue(String.valueOf(over), over < 1);
	}

	/** The fifth line would leave the first paragraph's last line alone in the second column. */
	public void testWidowsAfterTheBalancedCut() throws Exception {
		final double over = overrun("<div class=\"c\">" + paragraph("A", 6) + paragraph("B", 4) + "</div><div>NEXT</div>");
		assertTrue(String.valueOf(over), over < 1);
	}

	/** The columns before a column-span: all element are balanced the same way. */
	public void testOrphansBeforeASpanner() throws Exception {
		final double over = overrun("<div class=\"c\">" + paragraph("A", 4) + paragraph("B", 6)
				+ "<div class=\"span\">NEXT</div><p>C1</p></div>");
		assertTrue(String.valueOf(over), over < 1);
	}
}
