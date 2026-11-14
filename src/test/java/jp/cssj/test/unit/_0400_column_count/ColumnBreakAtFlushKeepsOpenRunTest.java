package jp.cssj.test.unit._0400_column_count;

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
 * A column break taken at a flush between a text run's start and its first glyph keeps the run open for the glyphs
 * after it (2026-11-14, sweep triage §224, mirror seed 106867680). The break lays the rest of the column out again on
 * the same builder, and the lines it replays end their own runs. The glyphs after the flush then came with neither a
 * text block nor a font and the whole conversion stopped ("glyph outside a text run"). 19124 stopped the same way.
 */
public class ColumnBreakAtFlushKeepsOpenRunTest extends TestCase {
	private static final Pattern T = Pattern.compile("Text\\[\"(T\\d+)\"");

	private static final String DOC = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page{size:334pt 491pt;margin:10pt 5pt 15pt 0pt} body{margin:0;font:12pt/14.5pt serif}</style></head><body>"
			+ "<div style=\"display:flex;block-size:61pt;\"><div></div></div>"
			+ "<p style=\"margin-inline-start:1pt;\"><br />T53</p>"
			+ "<div style=\"column-count:2;\"><table style=\"margin-block-end:8pt;\"><caption>T55</caption>"
			+ "<tr><th style=\"border-style:solid;padding-block-end:9pt;\"><br />T60</th></tr>"
			+ "<tr><td style=\"border-style:solid;padding-block-end:9pt;\">T62</td></tr></table>"
			+ "<ul style=\"list-style-type:lower-alpha;\"><li>T63 T64 T65 T66 T67 T68</li></ul>"
			+ "<div style=\"display:flex;flex-direction:column;flex-wrap:wrap;gap:4pt;height:77pt;padding-bottom:8pt;\">"
			+ "<div style=\"background:#eee;flex:0 1 35pt;\"></div><div style=\"background:#eee;\">T71</div>"
			+ "<div style=\"background:#eee;inline-size:68pt;\"><div style=\"column-count:3;\">"
			+ "<p>T72 T73 T74 T75<br />T76 T77 T79 T80 T81 T82 T83 T84 T85 T86 T87</p>"
			+ "<p style=\"margin-block-start:auto;\">T88 T89 T90 T91<br />T92 T93 T94<br />T95 T96<br />"
			+ "T97 T98 T102 T103 T104 T105 T106 T107 T108 T109</p></div></div></div></div></body></html>";

	public void testRunStaysOpenAcrossTheColumnBreak() throws Exception {
		final File dir = Files.createTempDirectory("column-break-at-flush").toFile();
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
