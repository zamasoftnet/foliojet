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
 * {@code text-justify}と韓国語の両端揃えの試験です(2026-09-02)。
 *
 * <p>
 * Chrome の実測(2026-09-01): 韓国語の両端揃えは<b>空白だけが伸び、音節の送りは
 * 1画素も動かない</b>。Copper は全ての文字間へ均等に配っていた。ページSVGの
 * {@code <text x="…">}は字形ごとのxを持つので、語の中の送りと語間の両方を
 * 測れる。
 * </p>
 */
public class TextJustifyKoreanTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String KOREAN = "한국어 문장의 양끝 맞춤을 확인하는 긴 문장입니다 다음 줄로 넘어가야 합니다";

	private static String html(final String lang, final String textAlign, final String textJustify,
			final String wordBreak) {
		return "<!DOCTYPE html><html lang=\"" + lang + "\"><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:200pt 120pt;margin:10pt}body{margin:0;font-size:10pt}"
				// keep-all なら語の途中で折れないので、行に余りができる。normal なら
				// 音節の間で折れて1行目はちょうど埋まる(2行目に余りが残る)
				+ "p{width:150pt;margin:0;word-break:" + wordBreak + ";text-align:" + textAlign + ";text-justify:"
				+ textJustify + "}"
				+ "</style></head><body><p>" + KOREAN + "</p></body></html>";
	}

	/** 1つの{@code <text>}: 字形のx列とy、元の文字列。 */
	private record Run(double[] xs, double y, String text) {
	}

	/** 韓国語の既定(auto)は語間だけを伸ばす。語の中の送りは左揃えと同じ。 */
	public void testKoreanAutoStretchesOnlyWordSpaces() throws Exception {
		final List<Run> left = line(runs(convert(html("ko", "left", "auto", "keep-all"))), 0);
		final List<Run> justified = line(runs(convert(html("ko", "justify", "auto", "keep-all"))), 0);
		assertTrue("the first line must hold several words: " + left.size(), left.size() >= 3);
		assertEquals("the same words must be on the first line", words(left), words(justified));
		assertIntraWordAdvancesEqual(left, justified);
		// 語間は伸びている
		assertTrue("word gaps must grow: " + gaps(left) + " -> " + gaps(justified),
				gaps(justified).get(0) > gaps(left).get(0) + 0.5);
		// 行は右端まで届く(最後の語の最後の字形の後端が幅150ptに近い)
		final Run last = justified.get(justified.size() - 1);
		final double lastX = last.xs[last.xs.length - 1];
		assertTrue("the justified line must reach the right edge: last glyph x=" + lastX, lastX > 150 - 10 - 1);
	}

	/** {@code inter-character}なら文字間にも配る(音節の送りが変わる)。 */
	public void testInterCharacterDistributesBetweenSyllables() throws Exception {
		final List<Run> left = line(runs(convert(html("ko", "left", "auto", "normal"))), 1);
		final List<Run> distributed = line(runs(convert(html("ko", "justify", "inter-character", "normal"))), 1);
		assertEquals(words(left), words(distributed));
		boolean changed = false;
		for (int i = 0; i < left.size() && !changed; ++i) {
			final double[] a = left.get(i).xs, b = distributed.get(i).xs;
			for (int j = 1; j < a.length; ++j) {
				if (Math.abs((a[j] - a[j - 1]) - (b[j] - b[j - 1])) > 0.05) {
					changed = true;
					break;
				}
			}
		}
		assertTrue("inter-character must widen the advances inside a word", changed);
	}

	/** {@code none}は両端揃えをしない(左揃えと同じ位置)。 */
	public void testNoneLeavesTheLineAlone() throws Exception {
		final List<Run> left = line(runs(convert(html("ko", "left", "auto", "keep-all"))), 0);
		final List<Run> none = line(runs(convert(html("ko", "justify", "none", "keep-all"))), 0);
		assertEquals(words(left), words(none));
		for (int i = 0; i < left.size(); ++i) {
			assertEquals("run " + i + " must not move", left.get(i).xs[0], none.get(i).xs[0], 0.05);
		}
	}

	/** 言語が無ければ従来どおり(文字間にも配る)。既存の版面を動かさない。 */
	public void testUntaggedTextKeepsTheGeneralDistribution() throws Exception {
		final List<Run> left = line(runs(convert(html("", "left", "auto", "normal"))), 1);
		final List<Run> justified = line(runs(convert(html("", "justify", "auto", "normal"))), 1);
		assertEquals(words(left), words(justified));
		boolean changed = false;
		for (int i = 0; i < left.size() && !changed; ++i) {
			final double[] a = left.get(i).xs, b = justified.get(i).xs;
			for (int j = 1; j < a.length; ++j) {
				if (Math.abs((a[j] - a[j - 1]) - (b[j] - b[j - 1])) > 0.05) {
					changed = true;
					break;
				}
			}
		}
		assertTrue("without a language the advances inside a word are still distributed", changed);
	}

	private static void assertIntraWordAdvancesEqual(final List<Run> a, final List<Run> b) {
		for (int i = 0; i < a.size(); ++i) {
			final double[] x = a.get(i).xs, y = b.get(i).xs;
			assertEquals("glyph count of run " + i, x.length, y.length);
			for (int j = 1; j < x.length; ++j) {
				assertEquals("advance inside word " + i + " glyph " + j, x[j] - x[j - 1], y[j] - y[j - 1], 0.05);
			}
		}
	}

	private static List<String> words(final List<Run> runs) {
		final List<String> words = new ArrayList<>();
		for (final Run r : runs) {
			words.add(r.text);
		}
		return words;
	}

	/** 隣り合う語の間(前の語の最後の字形から次の語の最初の字形まで)。 */
	private static List<Double> gaps(final List<Run> runs) {
		final List<Double> gaps = new ArrayList<>();
		for (int i = 1; i < runs.size(); ++i) {
			final double[] prev = runs.get(i - 1).xs;
			gaps.add(runs.get(i).xs[0] - prev[prev.length - 1]);
		}
		return gaps;
	}

	/** 上から{@code index}番目の行のrun(xの順)。 */
	private static List<Run> line(final List<Run> runs, final int index) {
		final List<Double> ys = new ArrayList<>();
		for (final Run r : runs) {
			boolean seen = false;
			for (final double y : ys) {
				if (Math.abs(r.y - y) < 0.5) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				ys.add(r.y);
			}
		}
		ys.sort(null);
		assertTrue("line " + index + " must exist: " + ys, index < ys.size());
		final double y = ys.get(index);
		final List<Run> line = new ArrayList<>();
		for (final Run r : runs) {
			if (Math.abs(r.y - y) < 0.5) {
				line.add(r);
			}
		}
		line.sort((p, q) -> Double.compare(p.xs[0], q.xs[0]));
		return line;
	}

	private static List<Run> runs(final String svg) {
		final List<Run> runs = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("<text x=\"([^\"]*)\" y=\"([^\"]*)\"[^>]*data-copper-text=\"([^\"]*)\"").matcher(svg);
		while (m.find()) {
			final String[] xs = m.group(1).trim().split("\\s+");
			final double[] x = new double[xs.length];
			for (int i = 0; i < xs.length; ++i) {
				x[i] = Double.parseDouble(xs[i]);
			}
			final double y = Double.parseDouble(m.group(2).trim().split("\\s+")[0]);
			runs.add(new Run(x, y, m.group(3)));
		}
		return runs;
	}

	private String convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Sans KR'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///text-justify.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results.text("pages/0001.svg");
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
