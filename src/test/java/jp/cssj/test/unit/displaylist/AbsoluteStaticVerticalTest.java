package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
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
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 縦組みのoffset未指定absoluteが静的位置へ描かれることを固定します。 */
public class AbsoluteStaticVerticalTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private record Point(double x, double y) {
	}

	public void testStaticPositionInVerticalRlAndLr() throws Exception {
		for (final String mode : new String[] { "vertical-rl", "vertical-lr" }) {
			final String dump = convert(mode);
			final Point before = point(dump, "ABS-PRE");
			final Point absolute = point(dump, "ABSSTATIC");
			final Point after = point(dump, "ABS-AFTER");
			assertTrue(mode + ": absoluteのXがページ内: " + absolute, absolute.x >= 0 && absolute.x <= 200);
			assertTrue(mode + ": absoluteのYがページ内: " + absolute, absolute.y >= 0 && absolute.y <= 150);
			assertEquals(mode + ": 静的位置は本文と同じ行軸位置", before.y, absolute.y, 0.75);
			assertEquals(mode + ": 静的位置は後続内容の列にある", after.x, absolute.x, 0.75);
			assertEquals(mode + ": 静的位置は後続内容と同じ行軸位置", after.y, absolute.y, 0.75);
		}
	}

	private static Point point(final String dump, final String text) {
		final Matcher matcher = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\\\"" + text)
				.matcher(dump);
		assertTrue(text + "が1ページ目の表示リストにありません:\n" + dump, matcher.find());
		return new Point(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)));
	}

	private static String convert(final String mode) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 150pt;margin:10pt}html,body,p{margin:0}"
				+ "body{writing-mode:" + mode + ";font:10pt/12pt monospace}"
				+ ".static{position:absolute;width:70pt;height:70pt}"
				+ "</style></head><body><p>ABS-PRE</p><div class='static'>ABSSTATIC</div>"
				+ "<p>ABS-AFTER</p></body></html>";
		final File dir = new File("local/unittest/absolute-static-" + mode);
		dir.mkdirs();
		final File[] old = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (old != null) {
			for (final File file : old) {
				file.delete();
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///absolute-static-" + mode + ".html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		final File page = new File(dir, "page-0001.txt");
		assertTrue(mode + ": 1ページ目の表示リストがありません", page.isFile());
		return Files.readString(page.toPath(), StandardCharsets.UTF_8);
	}
}
