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
 * A float inside an orthogonal box stays in the box when the page breaks after it (2026-10-26, triage §77, code review of
 * unfixed sibling paths).
 *
 * <p>
 * When the page broke after a vertical box in a horizontal document, {@code FlowContainer.aggregateFloatings} also went
 * into the vertical box and compared its float's position, which lies on the box's own block axis (x), with the page
 * limit on the y axis. The float was moved to the next page: the box kept the space the text had left for it, and the
 * figure appeared at the top of the next page and pushed the next paragraph aside. An orthogonal box is not split in the
 * page axis, so its floats now stay with it.
 * </p>
 */
public class OrthogonalBoxFloatPageBreakTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"([^\"]*)\"");

	private static final String PARAGRAPH = "国語の教科書の一部を例に、縦書きの囲みを横組みの本文の中に置く。ここでは短い随筆を縦書きで"
			+ "引用し、その中に小さな挿絵を回り込ませている。本文は横組みのまま続く。";
	private static final String AFTER = "囲みのあとも本文が続き、この段落の途中で頁が変わる。囲みの中の挿絵は囲みの中に残って"
			+ "いなければならない。囲みのあとも本文が続き、この段落の途中で頁が変わる。";

	public void testFloatInVerticalBoxStaysOnItsPage() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (final String head : new String[] { "甲", "乙", "丙", "丁", "戊", "己" }) {
			body.append("<p>").append(head).append('。').append(PARAGRAPH).append("</p>");
		}
		body.append("<div class='tate'>春はあけぼの。やうやう白くなりゆく山ぎは、すこしあかりて、紫だちたる雲のほそくたなびきたる。"
				+ "夏は夜。月のころはさらなり、やみもなほ、ほたるの多く飛びちがひたる。また、ただ一つ二つなど、ほのかにうち光りて"
				+ "行くもをかし。雨など降るもをかし。秋は夕暮れ。夕日のさして山の端いと近うなりたるに、からすの寝どころへ行くとて、"
				+ "三つ四つ、二つ三つなど飛びいそぐさへあはれなり。<div class='fig'>図一</div>まいて雁などのつらねたるが、"
				+ "いと小さく見ゆるはいとをかし。日入りはてて、風の音、虫の音など、はたいふべきにあらず。</div>");
		for (final String head : new String[] { "庚", "辛", "壬", "癸" }) {
			body.append("<p>").append(head).append('。').append(AFTER).append("</p>");
		}
		final Path dir = convert("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'><style>"
				+ "@page{size:A5;margin:15mm} body{font:10pt/1.6 serif;margin:0} p{margin:0 0 4pt;text-indent:1em}"
				+ ".tate{writing-mode:vertical-rl;height:12em;border:0.5pt solid #888;padding:4pt;margin:6pt 0}"
				+ ".fig{float:left;width:3em;height:4em;border:1pt solid #c00;margin:0 0 4pt 4pt}"
				+ "</style></head><body>" + body + "</body></html>");
		final String first = Files.readString(dir.resolve("page-0001.txt"));
		final String second = Files.readString(dir.resolve("page-0002.txt"));
		assertTrue("the figure stays in the vertical box on page 1:\n" + first, first.contains("Text[\"図一\""));
		assertFalse("the figure is not moved to page 2:\n" + second, second.contains("Text[\"図一\""));
		// The paragraph continuing on page 2 starts at the column's start edge, as every paragraph does.
		final Matcher m = TEXT.matcher(second);
		assertTrue(second, m.find());
		assertTrue("page 2 begins with the paragraph, not a figure:\n" + second, m.group(3).startsWith("辛"));
		assertEquals("at the text indent, not pushed aside by a figure:\n" + second, 10.0, Double.parseDouble(m.group(1)),
				0.01);
	}

	private static Path convert(final String html) throws Exception {
		final Path dir = Files.createTempDirectory("orthogonal-box-float");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString())) {
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
		return dir;
	}
}
