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
 * Balancing keeps a heading with {@code break-after: avoid} where the actual cut puts it (2026-11-15, publishing report
 * TECH-20261115-001, the book's chapter 4). The balancer cut the single stack after the heading, the actual cut moved
 * the heading to the second column with its paragraph, and the last column ran a line past the balanced height into the
 * block after the multicol.
 */
public class BalanceKeepsAvoidedBreakTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("y=([-\\d.]+) Text\\[\"(.*?)\" asc=([\\d.]+) desc=([\\d.]+)");

	private static final String HEAD = "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"utf-8\"><style>"
			+ "@page{size:182mm 257mm;margin:10.4% 8.8% 11% 10.4%} .text{column-count:2}{CSS}</style></head><body>"
			+ "<div class=\"text\">"
			+ "<p>次亜塩素酸イオンは光や熱で分解していくうえ、ラベルのない容器に入った漂白剤はいずれ誰かの手で酸と出会うので、できた液は保存しないニャ。</p>"
			+ "<h3>電位-pH 図</h3>"
			+ "<p>酸を入れてはならぬ理由を平衡の式で書くニャ。水の中の酸化された塩素は、溶けた塩素 えんそ、次亜塩素酸 えんそ、次亜塩素酸イオン えんそ の三つの姿をとり、次の平衡で結ばれている。</p>"
			+ "</div><div>NEXT</div></body></html>";

	/** The bottom of the lowest text in the multicol less the top of the block after it, in pt. */
	private static double overrun(final String css) throws Exception {
		final String doc = HEAD.replace("{CSS}", css);
		final File dir = Files.createTempDirectory("balance-avoided-break").toFile();
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
			} else {
				bottom = Math.max(bottom, y + Double.parseDouble(m.group(3)) + Double.parseDouble(m.group(4)));
			}
		}
		assertFalse(Double.isNaN(next));
		return bottom - next;
	}

	public void testHeadingKeptWithItsParagraph() throws Exception {
		// The UA style sheet gives h3 break-after: avoid
		assertTrue(String.valueOf(overrun("")), overrun("") < 1);
	}

	public void testParagraphKeptWithTheHeading() throws Exception {
		final String css = " h3{break-after:auto} h3+p{break-before:avoid}";
		assertTrue(String.valueOf(overrun(css)), overrun(css) < 1);
	}
}
