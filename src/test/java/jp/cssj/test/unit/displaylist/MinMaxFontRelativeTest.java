package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * 絶対長さとフォント相対単位を比べる min()・max()・clamp() を、フォント寸法が定まってから解くことを
 * 固定します(2026-10-04、出版の報告: {@code min(10mm, 3em)} が不正な値として捨てられていた)。
 */
public class MinMaxFontRelativeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 高さ h の箱の幅です。箱ごとに高さを変えて見分ける。 */
	private static double width(final String dump, final int h) {
		final Matcher m = Pattern.compile("AbsoluteRectFrame\\[w=([-0-9.]+) h=" + h + "\\.00\\]").matcher(dump);
		assertTrue("box of height " + h + ": " + dump, m.find());
		return Double.parseDouble(m.group(1));
	}

	private static String box(final String style, final int h) {
		return "<span style='display:inline-block;background:red;height:" + h + "pt;" + style + "'></span> ";
	}

	public void testFontRelativeOperandsAreComparedAtComputedTime() throws Exception {
		final String html = "<!DOCTYPE html><html style='font-size:10pt'><head><meta charset='UTF-8'><style>"
				+ "@page{size:400pt 400pt;margin:10pt} body{margin:0;line-height:1}</style></head>"
				+ "<body><div style='font-size:20pt'>" //
				+ box("width:min(100pt, 3em)", 11) // 3em = 60pt
				+ box("width:min(50pt, 3em)", 12) // 50pt
				+ box("width:max(10mm, 1em)", 13) // 10mm = 28.35pt
				+ box("width:clamp(1rem, 50pt, 2rem)", 14) // rem = 10pt → 20pt
				+ box("width:calc(min(10pt, 1em) * 2 + 1em)", 15) // 2 × 10 + 20 = 40pt
				+ box("font-size:clamp(1rem, 30pt, 2rem);width:1em", 16) // font-size 20pt
				+ box("line-height:max(1lh, 30pt);width:1lh", 17) // 30pt
				+ "</div></body></html>";
		final Path dir = Files.createTempDirectory("minmax-font");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString());
				AutoCloseable g = DisplayListDumper.scopedDetailedGeometry(true)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final String dump = Files.readString(dir.resolve("page-0001.txt"));
		assertEquals(60, width(dump, 11), 0.01);
		assertEquals(50, width(dump, 12), 0.01);
		assertEquals(28.35, width(dump, 13), 0.01);
		assertEquals(20, width(dump, 14), 0.01);
		assertEquals(40, width(dump, 15), 0.01);
		assertEquals(20, width(dump, 16), 0.01);
		assertEquals(30, width(dump, 17), 0.01);
	}
}
