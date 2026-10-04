package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * ページ分割SVGで、MathML とインライン SVG の中の描画が頁の上の位置に置かれることを固定します
 * (2026-10-04、TECH-20261003-004 の⑳)。
 *
 * <p>
 * どちらも Graphics2D の橋渡しで描き、橋渡しは GC を直近の{@code begin()}の状態へ戻して
 * ({@code resetState()})変換の差分を掛け直す。ページ分割SVGの GC は初期状態(単位行列)へ
 * 戻していたので、数式の 2 字目以降・入れ子の図形・クリップが頁の原点の近くに描かれていた。
 * </p>
 */
public class PagedSvgNestedTransformTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(metadata.getURI().toString(), out);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// 何もしない
		}
	}

	public void testMathAndInlineSvgAreDrawnAtTheirPlace() throws Exception {
		// 余白 72pt。中身は全部その内側に描かれる
		final String html = """
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><style>
				@page { size: 400pt 600pt; margin: 72pt } body { margin: 0; font-size: 9pt }
				svg { width: 60mm; height: auto }
				</style></head><body>
				<p>x <math xmlns="http://www.w3.org/1998/Math/MathML"><msup><mi>x</mi><mn>2</mn></msup><mo>+</mo>
				<mfrac><mn>1</mn><mn>3</mn></mfrac></math></p>
				<math xmlns="http://www.w3.org/1998/Math/MathML" display="block"><mi>e</mi><mo>(</mo><mi>P</mi>
				<mo>,</mo><mi>Q</mi><msup><mo>)</mo><mrow><mi>a</mi><mi>b</mi></mrow></msup></math>
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 30" width="60mm" height="30mm">
				<rect x="1" y="1" width="58" height="28" fill="#eef" stroke="#24a" stroke-width="0.6"/>
				<circle cx="15" cy="15" r="8" fill="#c33"/></svg>
				</body></html>""";
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///nested-transform.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final ByteArrayOutputStream page = results.data.get("pages/0001.svg");
		assertNotNull(results.data.keySet().toString(), page);
		final String svg = page.toString(StandardCharsets.UTF_8);

		// 図形とクリップの経路の始点。頁の原点の近く(余白の中)に描かれたものがあってはならない
		final Matcher m = Pattern.compile("<path d=\"M(-?[0-9.]+)[ ,](-?[0-9.]+)").matcher(svg);
		final List<String> stray = new ArrayList<>();
		int paths = 0;
		while (m.find()) {
			++paths;
			final double x = Double.parseDouble(m.group(1));
			final double y = Double.parseDouble(m.group(2));
			// 頁全体のクリップ(原点から始まる長方形)は除く
			if ((x < 60 || y < 60) && !(x == 0 && y == 0)) {
				stray.add(m.group(1) + "," + m.group(2));
			}
		}
		assertTrue("math glyphs and shapes are drawn as paths: " + paths, paths >= 6);
		assertEquals("paths drawn near the page origin", List.of(), stray);
	}
}
