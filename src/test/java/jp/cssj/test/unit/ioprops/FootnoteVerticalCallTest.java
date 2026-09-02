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
 * 縦組みの脚注の呼び出し番号の位置の試験です(2026-09-02)。
 *
 * <p>
 * 利用者の報告: 縦書きで脚注の番号が右にずれる。呼び出し
 * ({@code ::footnote-call})はラベル画像({@code FootnoteLabelImage})で、
 * 縦組みの行の中では本文の字の列(x範囲)に収まっていなければならない。
 * ページJSONの文字列の矩形で確かめる。
 * </p>
 */
public class FootnoteVerticalCallTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html() {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:100mm 150mm;margin:10mm}html{writing-mode:vertical-rl}body{margin:0;font-size:10pt}"
				+ ".note{float:footnote}"
				+ "</style></head><body>"
				+ "<p>縦組みの本文<span class=\"note\">縦の注ALPHA</span>のつづき。</p>"
				+ "</body></html>";
	}

	/** {@code {value, x1, y1, x2, y2}}(transformを足したページ座標)。 */
	private record Run(String value, double x1, double y1, double x2, double y2) {
	}

	public void testCallLabelStaysInTheLineColumn() throws Exception {
		final CapturingResults r = convert();
		final String page = r.text("pages/0001.json");
		final List<Run> runs = runs(page);
		assertTrue("the note body must be placed: " + runs, page.contains("縦の注") && page.contains("ALPHA"));
		Run body = null, label = null;
		for (final Run run : runs) {
			if (run.value.equals("縦組みの本文")) {
				body = run;
			} else if (run.value.equals("1") && label == null) {
				label = run;
			}
		}
		assertNotNull("the body run must be found: " + runs, body);
		assertNotNull("the call label must be found: " + runs, label);
		// 縦組みでは本文の列は細く(1字幅)、呼び出しの数字はその列の右肩に居るべき。
		// Chromeの上付きは親のフォントサイズの1/3だけ右へ寄るので、数字の
		// 大半は列の中に収まる(以前は数字の左端が列の右端に来て、丸ごと
		// 列の外へはみ出していた——利用者の報告「縦書きで右にずれる」)。
		// ページJSONの横書きrunの矩形は「開始x〜開始x+フォントサイズ」なので、
		// 数字の実幅は矩形の幅(=font-size 8.3)の55%と見る
		final double size = label.x2 - label.x1;
		assertTrue("the call label must sit on the body's column (body x=" + body.x1 + ".." + body.x2
				+ ", label x=" + label.x1 + ".." + label.x2 + "):\n" + runs + "\n" + this.displayList,
				label.x1 >= body.x1 - 0.5 && label.x1 + size * 0.55 <= body.x2 + 1.0);
	}

	private static List<Run> runs(final String json) {
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

	/** 表示リストの写し(失敗時の診断用)。 */
	private String displayList = "";

	private CapturingResults convert() throws Exception {
		final CapturingResults results = new CapturingResults();
		final java.io.File dumpDir = java.nio.file.Files.createTempDirectory("fn-vertical-dl").toFile();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try (AutoCloseable dump = net.zamasoft.foliojet.layout.draw.DisplayListDumper.scopedDir(dumpDir.getPath())) {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html().getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-vertical-call.html"), "text/html", "UTF-8");
		} finally {
			session.close();
			final StringBuilder sb = new StringBuilder();
			final java.io.File[] files = dumpDir.listFiles();
			if (files != null) {
				java.util.Arrays.sort(files);
				for (final java.io.File f : files) {
					sb.append("== ").append(f.getName()).append('\n').append(java.nio.file.Files.readString(f.toPath()));
					f.delete();
				}
			}
			dumpDir.delete();
			this.displayList = sb.toString();
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
