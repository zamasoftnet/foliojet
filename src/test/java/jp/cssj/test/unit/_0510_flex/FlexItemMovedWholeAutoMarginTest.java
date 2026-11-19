package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TreeSet;
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
 * An item of a column flex container moved whole to the next page resolves its auto margins within the outer width it
 * keeps (2026-11-19, sweep triage §245, mirror seed 136674597). Laid out at the foot of the first page beside a float,
 * the container was narrower than the item of width 50pt, whose margin-left: auto was 0. On the next page the float
 * sits above it and the container is wider: the restyle kept the item's outer width of 50pt but gave its auto margin
 * the room the wider line left, so its width came out negative and the continuation check stopped the whole
 * conversion when the table inside broke across pages.
 */
public class FlexItemMovedWholeAutoMarginTest extends TestCase {
	private static final Pattern T = Pattern.compile("Text\\[\"(T\\d+)\"");

	private static final String DOC = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page{size:210pt 286pt;margin:10pt 0pt 5pt 15pt} body{margin:0;font:11pt/16.5pt serif}</style></head><body>"
			+ "<div style=\"height:250pt\"></div>"
			+ "<div style=\"display:flex;\"><div><div style=\"float:right;inline-size:82pt;\"><p>T48</p></div>"
			+ "<div style=\"display:grid;\"><div>T50 T51 T52</div></div>"
			+ "<div style=\"display:flex;flex-direction:column;\">"
			+ "<div style=\"padding-block-start:7pt;\">T56</div><div style=\"padding-bottom:8pt;\">T57 T58</div>"
			+ "<div style=\"width:50pt;margin-left:auto;\">"
			+ "<table style=\"table-layout:fixed;width:69pt;border-spacing:2pt 3pt;\">"
			+ "<tr><td></td><td>T61 T62 T63 T64</td></tr>"
			+ "<tr><td colspan=\"2\" style=\"border-style:solid;padding-block-end:10pt;\">T65 T66 T67 T68</td></tr>"
			+ "<tr><td>T74 T75 T76 T77</td></tr></table></div></div></div></div></body></html>";

	public void testItemMovedWholeKeepsItsWidth() throws Exception {
		final File dir = Files.createTempDirectory("flex-item-moved-whole").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), DOC, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(pages);
		final TreeSet<String> drawn = new TreeSet<>();
		for (final File page : pages) {
			final Matcher m = T.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				drawn.add(m.group(1));
			}
		}
		final TreeSet<String> written = new TreeSet<>();
		final Matcher m = Pattern.compile("T\\d+").matcher(DOC);
		while (m.find()) {
			written.add(m.group());
		}
		assertEquals("every word is drawn", written, drawn);
	}
}
