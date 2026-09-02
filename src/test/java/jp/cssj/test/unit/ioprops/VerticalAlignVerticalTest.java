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
 * 縦組みの {@code vertical-align: middle / text-top / text-bottom}(2026-09-02)。
 *
 * <p>
 * 縦組みの行は中央線揃えで、字面は列の左右にサイズの半分ずつ。以前は横組みの
 * 計量(x-height・ascent 0.88 倍)をそのまま使っていたので、middle は右へ半 x-height、
 * text-top は列の右端から張り出していた。ページ JSON の字形矩形(縦組みの run の
 * x 範囲=列)で、小さい字の列が親の列に対してどこに来るかを見る。
 * </p>
 */
public class VerticalAlignVerticalTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html(final String align) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100mm 150mm;margin:10mm}html{writing-mode:vertical-rl}body{margin:0;font-size:24pt}"
				+ "span{font-size:12pt;vertical-align:" + align + "}"
				+ "</style></head><body><p>親<span>子</span>親</p></body></html>";
	}

	private record Run(String value, double x1, double y1, double x2, double y2) {
	}

	public void testMiddleCentresTheSmallColumn() throws Exception {
		final Run[] r = runs(html("middle"));
		final double parentCentre = (r[0].x1 + r[0].x2) / 2, childCentre = (r[1].x1 + r[1].x2) / 2;
		assertEquals("middle: the small column must be centred on the parent's column: " + r[0] + " " + r[1],
				parentCentre, childCentre, 0.6);
	}

	public void testTextTopAlignsTheRightEdges() throws Exception {
		final Run[] r = runs(html("text-top"));
		assertEquals("text-top: the right edges (the top of the font in vertical-rl) must meet: " + r[0] + " " + r[1],
				r[0].x2, r[1].x2, 0.6);
	}

	public void testTextBottomAlignsTheLeftEdges() throws Exception {
		final Run[] r = runs(html("text-bottom"));
		assertEquals("text-bottom: the left edges must meet: " + r[0] + " " + r[1], r[0].x1, r[1].x1, 0.6);
	}

	/** {親, 子} の run。 */
	private Run[] runs(final String html) throws Exception {
		final List<Run> all = parse(convert(html));
		Run parent = null, child = null;
		for (final Run run : all) {
			if (run.value.startsWith("親") && parent == null) {
				parent = run;
			} else if (run.value.equals("子")) {
				child = run;
			}
		}
		assertNotNull("parent run: " + all, parent);
		assertNotNull("child run: " + all, child);
		assertTrue("the parent column must be wider than the child's: " + parent + " " + child,
				parent.x2 - parent.x1 > child.x2 - child.x1 + 4);
		return new Run[] { parent, child };
	}

	private static List<Run> parse(final String json) {
		final List<Run> runs = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile(
				"\\{\"value\":\"([^\"]*)\",\"font\":\"[^\"]*\",\"size\":[^,]*,\"transform\":\\[([^\\]]*)\\],\"bounds\":\\[([^\\]]*)\\]\\}")
				.matcher(json);
		while (m.find()) {
			final String[] t = m.group(2).split(",");
			final String[] b = m.group(3).split(",");
			final double a = Double.parseDouble(t[0]), bb = Double.parseDouble(t[1]), c = Double.parseDouble(t[2]),
					d = Double.parseDouble(t[3]), e = Double.parseDouble(t[4]), f = Double.parseDouble(t[5]);
			final double x1 = Double.parseDouble(b[0]), y1 = Double.parseDouble(b[1]), x2 = Double.parseDouble(b[2]),
					y2 = Double.parseDouble(b[3]);
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
			for (final double[] p : new double[][] { { x1, y1 }, { x2, y1 }, { x1, y2 }, { x2, y2 } }) {
				final double px = a * p[0] + c * p[1] + e, py = bb * p[0] + d * p[1] + f;
				minX = Math.min(minX, px);
				minY = Math.min(minY, py);
				maxX = Math.max(maxX, px);
				maxY = Math.max(maxY, py);
			}
			runs.add(new Run(m.group(1), minX, minY, maxX, maxY));
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
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///vertical-align-vertical.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results.text("pages/0001.json");
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
