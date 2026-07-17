package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 表示リスト(Drawerのダンプ)のgolden比較テストです。
 * レイアウトの幾何・描画順の回帰を、画像比較より厳密に検出します。
 *
 * <p>
 * 基準データは files/unittest/display-list-golden/ 以下にあります。
 * 意図的なレイアウト変更で更新する場合は、該当ディレクトリを削除して
 * このテストを実行すると再生成されます(再生成した実行は失敗扱いになります)。
 * </p>
 */
public class DisplayListGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 対象文書。ブロック・フロート・テーブル・縦書き・段組・生成内容をカバーする。 */
	private static final String[] DOCUMENTS = { //
			"0120-float/auto-width.html", //
			"0120-float/float-in-moved-block.html", //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			"0460-segment-restyle/text-tail-avoid.html", //
			"0460-segment-restyle/float-in-moved.html", //
			"0460-segment-restyle/float-split-in-chain.html", //
			"0460-segment-restyle/float-uncut-before-prefix.html", //
			"0460-segment-restyle/nested-break-in-replay.html", //
			"0390-writing-mode/vert-cell-specified-pagebreak.html", //
			"0240-table/z-order.html", //
			"0240-table/absolute.html", //
			"0390-writing-mode/border-collapse.html", //
			"0390-writing-mode/absolute.html", //
			"0400-column-count/nest.html", //
			"0350-line-height/small-line-height.html", //
			"0140-content/counters.html", //
			"0450-hyphens/hyphens.html", //
			"0470-margin-boxes/margin-boxes.html", //
	};

	public void testDisplayLists() throws Exception {
		List<String> failures = new ArrayList<>();
		for (String doc : DOCUMENTS) {
			String name = doc.replace('/', '_').replace(".html", "");
			File outDir = new File("local/unittest/display-list/" + name);
			deleteChildren(outDir);
			File goldenDir = new File("files/unittest/display-list-golden/" + name);

			System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
			try {
				this.transcode(new File("files/unittest/" + doc), name);
			} finally {
				System.clearProperty(DisplayListDumper.DIR_PROPERTY);
			}

			File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
			assertNotNull("表示リストが出力されていません: " + doc, pages);
			assertTrue("表示リストが出力されていません: " + doc, pages.length > 0);

			if (!goldenDir.isDirectory()) {
				// 基準データの初回生成
				goldenDir.mkdirs();
				for (File page : pages) {
					Files.copy(page.toPath(), new File(goldenDir, page.getName()).toPath());
				}
				failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
				continue;
			}

			File[] goldenPages = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
			if (goldenPages.length != pages.length) {
				failures.add(doc + ": ページ数が基準と異なります (golden=" + goldenPages.length + ", actual="
						+ pages.length + ")");
				continue;
			}
			for (File golden : goldenPages) {
				Path actual = new File(outDir, golden.getName()).toPath();
				String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
				String got = Files.readString(actual, StandardCharsets.UTF_8);
				if (!expected.equals(got)) {
					failures.add(doc + "/" + golden.getName() + ": 表示リストが基準と一致しません (expected="
							+ golden + ", actual=" + actual + ")");
				}
			}
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void transcode(File source, String name) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteChildren(File dir) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			child.delete();
		}
	}
}
