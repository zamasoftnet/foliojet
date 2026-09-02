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
 * 表のセルの中の脚注の試験です(2026-09-02)。
 *
 * <p>
 * cti.liの報告(2026-09-01、本番 build 19044): {@code <td>}の中の
 * {@code float: footnote}は<b>注の本文がどこにも出ず</b>、呼び出しの番号も
 * 頁ごとの採番から外れて文書通番のままだった。原因はページ確定時の
 * 呼び出し走査({@code RootBuilder.scanFootnoteCalls})が表と絶対配置の中へ
 * 降りていなかったこと。ページのJSON(文字列と位置)で本文と番号を確かめる。
 * </p>
 */
public class FootnoteInTableCellTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 1頁目は段落の注、2頁目は表のセルの注と絶対配置の注。頁ごとに1から
	 * 振り直されるので、2頁目のセルの注は「1」、絶対配置の注は「2」。
	 */
	private static String html() {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100mm 150mm;margin:10mm}body{margin:0;font-size:10pt}"
				+ ".note{float:footnote}" + ".next{page-break-before:always}"
				+ "table{border-collapse:collapse}td{border:1pt solid #000;padding:2pt}"
				+ ".abs{position:absolute;top:60mm;left:10mm}"
				+ "</style></head><body>"
				+ "<p>段落の本文<span class=\"note\">段落の注ALPHA</span>です。</p>"
				+ "<div class=\"next\"><table><tr><td>セルの本文<span class=\"note\">セルの注BRAVO</span>です。</td>"
				+ "<td>隣のセル</td></tr></table>"
				+ "<div class=\"abs\">絶対配置の本文<span class=\"note\">絶対配置の注CHARLIE</span>です。</div></div>"
				+ "</body></html>";
	}

	public void testFootnoteInsideTableCellIsPlacedAndNumberedPerPage() throws Exception {
		final CapturingResults r = convert();
		assertEquals("two pages are expected: " + r.order + "\n" + dump(r), 2, pageCount(r));
		final String page1 = r.text("pages/0001.json");
		final String page2 = r.text("pages/0002.json");

		assertTrue("the paragraph's note must be on page 1", page1.contains("段落の注ALPHA"));
		assertFalse("the cell's note belongs to page 2", page1.contains("BRAVO"));

		// **本文が出ること**。以前は呼び出しが見つからずEOFで捨てられていた
		// (和文とラテンは別のrunになるので、それぞれで見る)
		assertTrue("the note inside the table cell must be placed on page 2:\n" + page2,
				page2.contains("セルの注") && page2.contains("BRAVO"));
		assertTrue("the note inside the absolutely positioned box must be placed on page 2:\n" + page2,
				page2.contains("絶対配置の注CHARLIE"));

		// **番号が頁ごとに振り直されること**。2頁目の呼び出しは1と2で、
		// 文書通番の2・3ではない
		final List<String> labels2 = labels(page2);
		assertTrue("page 2 must number its notes from 1: " + labels2, labels2.contains("1"));
		assertTrue("page 2 must number its second note 2: " + labels2, labels2.contains("2"));
		assertFalse("page 2 must not carry the document-wide number 3: " + labels2, labels2.contains("3"));
	}

	/**
	 * ページJSONの短い数字だけの文字列(脚注の呼び出し・番号の候補)。
	 * 本文の文字列は数字だけにならないので、これで番号を拾える。
	 */
	private static List<String> labels(final String pageJson) {
		final List<String> labels = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"value\":\"(\\d{1,2})\"")
				.matcher(pageJson);
		while (m.find()) {
			labels.add(m.group(1));
		}
		return labels;
	}

	/** 各ページの文字列を1行ずつ(失敗時の診断用)。 */
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

	private CapturingResults convert() throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html().getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-in-table-cell.html"), "text/html", "UTF-8");
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
