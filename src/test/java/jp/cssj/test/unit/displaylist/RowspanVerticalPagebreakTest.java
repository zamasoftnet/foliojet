package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/** 縦書きのrowspan表が枠だけのページ断片を作らないことを固定します。 */
public class RowspanVerticalPagebreakTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final File VERTICAL_FIXTURE = new File(
			"files/unittest/0495-span/rowspan-vertical-pagebreak.html");
	private static final String[] TABLE_TOKENS = {
			"S01", "R01", "A01", "B01", "R02", "A02", "B02",
			"S02", "R03", "A03", "B03", "R04", "A04", "B04"
	};
	private static final String[] CONTENT_TOKENS = {
			"BEFORE", "S01", "R01", "A01", "B01", "R02", "A02", "B02",
			"S02", "R03", "A03", "B03", "R04", "A04", "B04", "AFTER"
	};
	private static final Pattern TEXT_POSITION = Pattern.compile(
			"x=(-?[0-9.]+) y=(-?[0-9.]+) Text\\[\\\"([^\\\"]+)\\\"");

	public void testVerticalRowspanMakesNoBorderOnlyPage() throws Exception {
		final File[] pages = convert("vertical", VERTICAL_FIXTURE);
		final StringBuilder all = new StringBuilder();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			if (dump.contains("CollapsedBorders[")) {
				boolean hasContent = false;
				for (final String token : CONTENT_TOKENS) {
					if (dump.contains("Text[\"" + token + "\"")) {
						hasContent = true;
						break;
					}
				}
				assertTrue("page " + (i + 1) + " がつぶし境界とノンブルだけです\n" + dump, hasContent);
			}
		}

		int previous = -1;
		for (final String token : TABLE_TOKENS) {
			final String needle = "Text[\"" + token + "\"";
			final int at = all.indexOf(needle);
			assertTrue("表の文字がありません: " + token, at >= 0);
			assertEquals("表の文字が重複しています: " + token, at, all.lastIndexOf(needle));
			assertTrue("表の文字がソース順ではありません: " + token, at > previous);
			previous = at;
		}

		final double pageAxis = mmToPt(103.2 - 5.6 * 2);
		final double inlineExtent = mmToPt(206.4 - 9.7 * 2);
		final double wrongRowspanUnit = inlineExtent / 4 * 2;
		assertTrue("誤ったinline軸計測がページを少しだけ超えるfixtureではありません",
				wrongRowspanUnit > pageAxis && wrongRowspanUnit - pageAxis < 5);
		final double correctRowSize = 8 + 3 * 2 + 1;
		final double afterSize = 8;
		final int expectedPages = 1 + (int) Math.ceil((correctRowSize * 4 + afterSize) / pageAxis);
		assertEquals("正しい行寸法から求めたページ数と一致しません", expectedPages, pages.length);
	}

	public void testHorizontalControlCoordinates() throws Exception {
		final File dir = outputDir("horizontal");
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			writer.write(horizontalControl());
		}
		final File[] pages = convert("horizontal", input);
		assertEquals("横書きcontrolのページ数", 1, pages.length);
		final String dump = Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
		assertTextPosition(dump, "R01", 53.5, 3.5);
		assertTextPosition(dump, "R02", 53.5, 18.5);
		assertTextPosition(dump, "R03", 53.5, 33.5);
		assertTextPosition(dump, "R04", 53.5, 48.5);
	}

	private static double mmToPt(final double mm) {
		return mm * 72 / 25.4;
	}

	private static void assertTextPosition(final String dump, final String token, final double x, final double y) {
		final Matcher matcher = TEXT_POSITION.matcher(dump);
		while (matcher.find()) {
			if (token.equals(matcher.group(3))) {
				assertEquals(token + " x", x, Double.parseDouble(matcher.group(1)), 0.01);
				assertEquals(token + " y", y, Double.parseDouble(matcher.group(2)), 0.01);
				return;
			}
		}
		fail("表示リストに文字がありません: " + token + "\n" + dump);
	}

	private static File outputDir(final String name) {
		return new File("local/unittest/rowspan-vertical-pagebreak/" + name);
	}

	private static File[] convert(final String name, final File input) throws Exception {
		final File dir = outputDir(name);
		dir.mkdirs();
		final File[] oldPages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (oldPages != null) {
			for (final File oldPage : oldPages) {
				oldPage.delete();
			}
		}
		try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
				AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} finally {
				session.close();
			}
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull("表示リストがありません: " + name, pages);
		Arrays.sort(pages);
		assertTrue("表示リストがありません: " + name, pages.length > 0);
		return pages;
	}

	private static String horizontalControl() {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:0}body{margin:0;font:normal 8pt/1 serif;writing-mode:horizontal-tb}
				table{table-layout:fixed;inline-size:100%;border-collapse:collapse}
				td{border:1pt solid black;padding:3pt;vertical-align:middle}</style></head>
				<body><table><col span="4" /><tbody>
				<tr><td rowspan="2">S01</td><td>R01</td><td>A01</td><td>B01</td></tr>
				<tr><td>R02</td><td>A02</td><td>B02</td></tr>
				<tr><td rowspan="2">S02</td><td>R03</td><td>A03</td><td>B03</td></tr>
				<tr><td>R04</td><td>A04</td><td>B04</td></tr>
				</tbody></table></body></html>
				""";
	}
}
