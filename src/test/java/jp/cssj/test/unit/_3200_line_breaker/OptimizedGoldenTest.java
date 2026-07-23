package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code text.line-breaker=optimized}有効時の選択品質を固定するgolden
 * テストです(M3c増分4)。和文justify・欧文ハイフネーションの適格段落に
 * ついて、K-Pが選択した行分割のdisplay listを
 * {@code files/unittest/display-list-golden/}配下の基準と比較する
 * ({@code DisplayListGoldenTest}と同じ流儀だが、こちらはoptimized
 * プロパティ付きで変換する点だけが異なる)。初回実行時は基準を生成して
 * 意図的に失敗する——内容を目視確認してコミットすること。
 */
public class OptimizedGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String[] DOCUMENTS = { //
			"3200-line-breaker/optimized-ja-justify.html", //
			"3200-line-breaker/optimized-en-hyphen.html", //
	};

	public OptimizedGoldenTest(String name) {
		super(name);
	}

	public void testOptimizedGoldens() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			this.checkDocument(doc, failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void checkDocument(final String doc, final List<String> failures) throws Exception {
		final String name = doc.replace('/', '_').replace(".html", "") + "_optimized";
		final File outDir = new File("local/unittest/display-list/" + name);
		deleteChildren(outDir);
		final File goldenDir = new File("files/unittest/display-list-golden/" + name);

		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			this.transcode(new File("files/unittest/" + doc), name);
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, pages);
		assertTrue("表示リストが出力されていません: " + doc, pages.length > 0);

		if (!goldenDir.isDirectory()) {
			goldenDir.mkdirs();
			for (final File page : pages) {
				Files.copy(page.toPath(), new File(goldenDir, page.getName()).toPath());
			}
			failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
			return;
		}

		final File[] goldenPages = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (goldenPages.length != pages.length) {
			failures.add(doc + ": ページ数が基準と異なります (golden=" + goldenPages.length + ", actual="
					+ pages.length + ")");
			return;
		}
		for (final File golden : goldenPages) {
			final File actual = new File(outDir, golden.getName());
			final String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
			final String got = Files.readString(actual.toPath(), StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + golden.getName() + ": 表示リストが基準と一致しません (expected=" + golden
						+ ", actual=" + actual + ")");
			}
		}
	}

	private void transcode(final File source, final String name) throws Exception {
		final File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				session.property("text.line-breaker", "optimized");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
