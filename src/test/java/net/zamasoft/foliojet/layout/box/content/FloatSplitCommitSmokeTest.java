package net.zamasoft.foliojet.layout.box.content;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * P2-3のplan駆動commit({@code Floatings.splitPageAxis}の
 * plan整合assert・identity anchor assertを含む)を、SMOKE
 * マニフェスト(51文書——float・表・改ページ・縦書きの代表集合)へ
 * in-process({@code DirectSession}経由)で走らせ、assert有効のまま
 * 1文書も失敗しないことを検証します(2026-07-24。P2-2の
 * {@code FloatSplitPlanShadowSmokeTest}をshadow撤去にあわせて改修)。
 *
 * <p>
 * 基準画像との比較そのものは別の仕組み(サーバー製品側のgradleタスク)が
 * 担う——こちらはassertが観測できる同一JVM内での補助証拠。
 * </p>
 */
public class FloatSplitCommitSmokeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 中間サニティ用の絞り込みコーパス。 */
	private static final Path SMOKE_MANIFEST = Path.of("files/visual/SMOKE-MANIFEST.txt");

	public void testSmokeCorpusCommitsWithoutAssertionFailures() throws Exception {
		if (!Files.isRegularFile(SMOKE_MANIFEST)) {
			System.out.println("SKIP: " + SMOKE_MANIFEST.toAbsolutePath().normalize() + " がありません(単独checkout)");
			return;
		}
		assertTrue("このテストはassert有効(gradle testの既定)で走ること", Floatings.class.desiredAssertionStatus());
		final Path docsDir = SMOKE_MANIFEST.getParent();
		final List<String> sourcePaths = new ArrayList<>();
		parseManifest(SMOKE_MANIFEST, new HashSet<>(), sourcePaths);

		final List<String> failures = new ArrayList<>();
		final File scratch = File.createTempFile("float-split-commit-smoke", ".pdf");
		scratch.deleteOnExit();
		int missing = 0;
		for (final String sourcePath : sourcePaths) {
			final File input = docsDir.resolve(sourcePath).normalize().toFile();
			if (!input.isFile()) {
				// **入力が無いものは飛ばす。**マニフェストの一部は、公開できない
				// 取り込み資料(実サイトのスナップショット)を指している。それらは
				// 開発用の作業ツリーにだけあり、このリポジトリ単独では存在しない。
				// 無いことを失敗にすると、単独 checkout でこの試験が常に赤くなる
				++missing;
				continue;
			}
			try {
				this.transcode(input, scratch);
			} catch (final Exception | AssertionError e) {
				failures.add(sourcePath + ": " + e);
			}
		}
		scratch.delete();

		final int ran = sourcePaths.size() - missing;
		System.out.println("SMOKE documents: total=" + sourcePaths.size() + " ran=" + ran
				+ " missing=" + missing + " failed=" + failures.size());
		// **1件も走らなかったなら緑にしない。**「入力が無いので全部飛ばした」を
		// 「全部通った」と読み違えるのが、この形の試験のいちばんの危険
		assertTrue("走った文書が1件も無い(マニフェストの入力がすべて欠けている)", ran > 0);
		assertTrue("変換失敗: " + failures, failures.isEmpty());
	}

	/** {@code ImageTestRunner.parseManifest}と同じ形式(@file/skip=/source=)のサブセット。 */
	private static void parseManifest(final Path file, final Set<Path> seen, final List<String> out)
			throws IOException {
		final Path normalized = file.toAbsolutePath().normalize();
		if (!seen.add(normalized)) {
			throw new IOException("Manifest include cycle: " + normalized);
		}
		for (String line : Files.readAllLines(normalized, StandardCharsets.UTF_8)) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("@")) {
				parseManifest(normalized.getParent().resolve(line.substring(1)), seen, out);
				continue;
			}
			final String[] parts = line.split("\\s+");
			final Map<String, String> options = new HashMap<>();
			for (int i = 1; i < parts.length; ++i) {
				final int eq = parts[i].indexOf('=');
				if (eq > 0) {
					options.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
				}
			}
			if ("true".equals(options.get("skip"))) {
				continue;
			}
			out.add(options.getOrDefault("source", parts[0]));
		}
	}

	private void transcode(final File source, final File out) throws Exception {
		try (OutputStream os = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(os)));
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
}
