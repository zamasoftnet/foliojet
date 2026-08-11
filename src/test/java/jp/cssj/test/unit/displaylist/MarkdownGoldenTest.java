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
 * Markdown入力(MarkdownParser)の表示リストgolden比較テストです。
 *
 * <p>
 * MarkdownはCommonMarkの構文木からXNIイベントを直接発行してTagBalancerへ
 * 流す実装(MarkdownParser参照)のため、このテストは変換の正しさ(見出し・
 * 段落・強調・リスト・コードブロック・引用・水平線・リンク・生HTML透過)を
 * 確認する。rawhtml.mdは生HTMLブロックの<b>後続内容</b>の被覆——断片
 * スキャナが合成するhtml/head/body骨組みイベントを素通しすると、断片ごとの
 * end bodyが外側のbodyを閉じて以降の内容がbodyの外に落ちる(2026-08-10に
 * 実際に起きた欠陥。basic.mdは生HTMLが末尾のみで掛からなかった)。
 * MIME型はファイル拡張子(.md)から自動判定させる(mimeType引数にnull)ことで、
 * 実運用の入力経路(拡張子判定)もあわせて検証する。
 * </p>
 */
public class MarkdownGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String[] DOCUMENTS = { //
			"3070-MARKDOWN/basic.md", //
			"3070-MARKDOWN/rawhtml.md", //
			// 青空文庫式ルビ(2026-08-11)
			"3070-MARKDOWN/aozora-ruby.md", //
	};

	public void testMarkdown() throws Exception {
		List<String> failures = new ArrayList<>();
		for (String doc : DOCUMENTS) {
			String name = doc.replace('/', '_').replace(".md", "");
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
				// MIME型は拡張子(.md)から自動判定させる
				CTISessionHelper.transcodeFile(session, source, null, null);
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
