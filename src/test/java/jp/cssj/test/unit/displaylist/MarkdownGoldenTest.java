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
 * Display-list golden comparison tests for Markdown input (MarkdownParser).
 *
 * <p>
 * Markdown emits XNI events directly from the CommonMark syntax tree into TagBalancer
 * (see MarkdownParser), so this test checks conversion correctness: headings, paragraphs,
 * emphasis, lists, code blocks, blockquotes, horizontal rules, links, and raw HTML passthrough.
 * rawhtml.md covers <b>content following</b> raw HTML blocks. Passing through html/head/body
 * skeleton events synthesized by the fragment scanner lets each fragment's end body close
 * the outer body, leaving later content outside it (an actual defect on 2026-08-10;
 * basic.md did not catch it because raw HTML appeared only at its end).
 * Autodetect the MIME type from the .md file extension (null mimeType argument)
 * to verify the production input path (extension detection) as well.
 * </p>
 */
public class MarkdownGoldenTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String[] DOCUMENTS = { //
			"3070-MARKDOWN/basic.md", //
			"3070-MARKDOWN/rawhtml.md", //
			// Aozora Bunko-style ruby (2026-08-11).
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
				// Initial generation of baseline data.
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
				// Autodetect the MIME type from the .md extension.
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
