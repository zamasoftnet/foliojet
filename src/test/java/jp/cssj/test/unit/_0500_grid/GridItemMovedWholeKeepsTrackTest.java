package jp.cssj.test.unit._0500_grid;

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
 * A grid item keeps its track when a page break inside its row's restyle moves it whole to the next grid fragment
 * (2026-11-13, sweep triage §211, mirror seed 101048191). The resume laid the same item out again without the track
 * and its auto width took the page (273pt for a 22pt column). The auto margin of a 43pt block in a column flex inside
 * then took 230pt, the block's inner width became -187pt and the continuation check stopped the whole conversion.
 */
public class GridItemMovedWholeKeepsTrackTest extends TestCase {
	private static final Pattern T = Pattern.compile("Text\\[\"(T\\d+)\"");

	private static final String DOC = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page{size:288pt 181pt;margin:10pt 0pt 10pt 15pt} body{margin:0;font:8pt/12.0pt serif}</style></head><body>"
			+ "<table style=\"border-spacing:3pt 6pt;\"><tr><td colspan=\"2\" style=\"padding-top:9pt;\">"
			+ "<div style=\"block-size:72pt;margin-bottom:6pt;padding-block-end:10pt;\"></div></td></tr></table>"
			+ "<p>T80</p>"
			+ "<div style=\"display:grid;grid-template-columns:42pt 22pt 28pt;padding-block-start:1pt;\">"
			+ "<div style=\"margin-block-start:9pt;\">T81</div><div>T82 T83 T84 T85 T86</div><div></div>"
			+ "<div><table style=\"table-layout:fixed;inline-size:116pt;\"><tr>"
			+ "<th style=\"padding-bottom:7pt;padding-inline-end:4pt;\"><br />T90 T91</th>"
			+ "<td style=\"border-style:solid;inline-size:75pt;\"></td></tr>"
			+ "<tr><th style=\"border-style:solid;padding-block-end:5pt;\">T95 T96</th></tr></table>"
			+ "<div style=\"column-count:2;margin-block-start:6pt;padding-top:8pt;\"><div style=\"column-count:2;\">"
			+ "<p>T101 T102 T103 T104 T105 T106 T107 T108 T109 T110 T111</p>"
			+ "<p>T113 T114 T115 T116 T117<br />T118 T119 T120<br />T121 T122<br />T123 T124<br />"
			+ "T125 T126 T127 T128 T129<br />T130</p></div>"
			+ "<div style=\"padding-top:7pt;\"><div>T133 T134 T135</div><div>T136 T137 T138 T139</div>"
			+ "<div>T140 T141 T142 T144 T145</div></div></div></div>"
			+ "<div style=\"padding-block-start:7pt;\"><div>"
			+ "<div style=\"display:flex;flex-direction:column;gap:10pt;margin-block-start:4pt;\">"
			+ "<div style=\"border-style:solid;\">T148 T149</div><div>T150 T151</div>"
			+ "<div style=\"inline-size:50pt;\">T152 T153 T154 T155 T156</div>"
			+ "<div style=\"inline-size:43pt;margin-inline-end:auto;\">T157 T158 T159 T160</div>"
			+ "</div></div></div></div></body></html>";

	public void testItemMovedWholeKeepsItsTrack() throws Exception {
		final File dir = Files.createTempDirectory("grid-item-moved-whole").toFile();
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
