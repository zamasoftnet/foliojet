package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 図の説明({@code <figcaption>})の中の脚注の試験です(2026-09-03)。
 *
 * <p>
 * cti.liの報告(2026-09-02、本番 build 19051): 縦組みの本文に横組みの
 * {@code <figure>}(直交フロー)があり、その {@code <figcaption>} に
 * {@code float: footnote} の注があると、図が次の頁へ送られても注は元の頁に
 * 残り、番号は頁ごとの数え直しを受けず文書通番のままだった。注は呼び出しと
 * 同じ頁に、番号は頁ごとに 1 から、呼び出しと注の頭で同じ値でなければならない。
 * </p>
 */
public class FootnoteInFigcaptionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String STYLE = "@page{size:100mm 150mm;margin:10mm}"
			+ "body{margin:0;font-size:10pt;line-height:1.5;writing-mode:vertical-rl}"
			+ "p{margin:0}" + ".note{float:footnote;font-size:8pt}"
			+ "figure{margin:0;break-inside:avoid;writing-mode:horizontal-tb}"
			+ ".art{background:#ccc}";

	/**
	 * 段落で1頁目の大半を埋め、幅60mmの図(横組み)を続ける。図は1頁目の残りに
	 * 収まらず2頁目へ送られる。図の説明の注は2頁目の最初の注なので番号は1。
	 */
	private static String pushedFigure() {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">").append(STYLE)
				.append("</style></head><body>");
		// 1行(縦組みでは1列)15pt幅。版面の行方向は80mm≒227pt。11列≒165ptで残り≒62pt<60mm
		for (int i = 0; i < 11; ++i) {
			sb.append("<p>本文の段落").append(i).append("です。</p>");
		}
		sb.append("<figure><div class=\"art\" style=\"width:60mm;height:40mm\"></div>")
				.append("<figcaption>図の説明<span class=\"note\">図の注ALPHA</span>です。</figcaption></figure>")
				.append("<p>図の後の本文<span class=\"note\">本文の注BRAVO</span>です。</p>");
		sb.append("</body></html>");
		return sb.toString();
	}

	public void testNoteInPushedFigureFollowsTheFigure() throws Exception {
		final CapturingResults r = convert(pushedFigure());
		assertEquals("two pages are expected: " + r.order + "\n" + dump(r), 2, pageCount(r));
		final String page1 = r.text("pages/0001.json");
		final String page2 = r.text("pages/0002.json");
		assertFalse("the figure was pushed to page 2, so its note must not stay on page 1:\n" + dump(r),
				page1.contains("ALPHA"));
		assertTrue("the caption's note must be on page 2 with the figure:\n" + dump(r), page2.contains("ALPHA"));
		assertTrue("the paragraph's note must be on page 2:\n" + dump(r), page2.contains("BRAVO"));
		// 番号は頁ごとに1から: 呼び出し 1,2 と注の頭 1,2
		assertEquals("calls on page 2: " + dump(r), List.of("1", "2"), calls(page2));
		assertEquals("markers on page 2: " + dump(r), List.of("1", "2"), markers(page2));
	}

	/**
	 * 改頁を避ける大きな図が3つ続き、その後の段落に注がある。図は1頁ずつに
	 * 割られ、注は段落の頁(4頁目)に番号1で置かれる。以前は注が図の頁に
	 * 溜まっている間に「停滞」と判定され、呼び出しの2頁前に通番で置かれた。
	 */
	public void testNoteAfterQueuedFiguresWaitsForItsCall() throws Exception {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">").append(STYLE)
				.append("</style></head><body>");
		for (int i = 0; i < 3; ++i) {
			sb.append("<figure><div class=\"art\" style=\"width:75mm;height:40mm\"></div>")
					.append("<figcaption>図").append(i).append("の説明です。</figcaption></figure>");
		}
		sb.append("<p>図の後の本文<span class=\"note\">本文の注ALPHA</span>です。</p>");
		sb.append("</body></html>");
		final CapturingResults r = convert(sb.toString());
		assertEquals("four pages are expected: " + r.order + "\n" + dump(r), 4, pageCount(r));
		for (int i = 1; i <= 3; ++i) {
			final String page = r.text(String.format("pages/%04d.json", i));
			assertFalse("page " + i + " holds a figure only:\n" + dump(r), page.contains("ALPHA"));
		}
		final String page4 = r.text("pages/0004.json");
		assertTrue("the note must be on the paragraph's page:\n" + dump(r), page4.contains("ALPHA"));
		assertEquals("call on page 4: " + dump(r), List.of("1"), calls(page4));
		assertEquals("marker on page 4: " + dump(r), List.of("1"), markers(page4));
	}

	/** 図が同じ頁に収まるときは、直交フローの中の呼び出しもその頁の番号で数える。 */
	public void testNoteInFittingFigureIsNumberedOnItsPage() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">" + STYLE
				+ "</style></head><body>"
				+ "<p>本文の段落<span class=\"note\">本文の注ALPHA</span>です。</p>"
				+ "<figure><div class=\"art\" style=\"width:30mm;height:30mm\"></div>"
				+ "<figcaption>図の説明<span class=\"note\">図の注BRAVO</span>です。</figcaption></figure>"
				+ "</body></html>";
		final CapturingResults r = convert(html);
		assertEquals("one page is expected: " + r.order + "\n" + dump(r), 1, pageCount(r));
		final String page1 = r.text("pages/0001.json");
		assertTrue(page1.contains("ALPHA") && page1.contains("BRAVO"));
		assertEquals("calls on page 1: " + dump(r), List.of("1", "2"), calls(page1));
		assertEquals("markers on page 1: " + dump(r), List.of("1", "2"), markers(page1));
	}

	/** ページJSONの数字だけの文字列(::footnote-call の番号)。 */
	private static List<String> calls(final String pageJson) {
		return values(pageJson, "\"value\":\"(\\d{1,2})\"");
	}

	/** ページJSONの「N. 」(::footnote-marker の番号)。 */
	private static List<String> markers(final String pageJson) {
		return values(pageJson, "\"value\":\"(\\d{1,2})\\. \"");
	}

	private static List<String> values(final String pageJson, final String regex) {
		final List<String> labels = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(pageJson);
		while (m.find()) {
			labels.add(m.group(1));
		}
		return labels;
	}

	private static String dump(final CapturingResults r) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= pageCount(r); ++i) {
			final String json = r.text(String.format("pages/%04d.json", i));
			sb.append("page ").append(i).append(": ");
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"value\":\"([^\"]*)\"").matcher(json);
			while (m.find()) {
				sb.append('[').append(m.group(1)).append(']');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static int pageCount(final CapturingResults r) {
		return (int) r.order.stream().filter(u -> u.startsWith("pages/") && u.endsWith(".svg")).count();
	}

	private CapturingResults convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-in-figcaption.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// 何もしない
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}
	}
}
