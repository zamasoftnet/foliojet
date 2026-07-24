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
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * P2-2のshadow比較({@code Floatings.splitPageAxis}の純判定
 * {@code FloatSplitPlan}と実行結果の突き合わせ)を、copperpdf4のSMOKE
 * マニフェスト(51文書——float・表・改ページ・縦書きの代表集合)へ
 * in-process({@code DirectSession}経由、{@code TailShadowCorpusAudit}と
 * 同じパターン)で走らせ、
 * {@code FLOAT_SPLIT_PLAN_MISMATCHES == 0}かつ
 * {@code FLOAT_SPLIT_PLAN_MATCHES > 0}(shadowが空振りしていない)ことを
 * 検証します(2026-07-24新設)。
 *
 * <p>
 * imageTest本体(基準画像比較)はcopperpdf4/devのgradleタスクが担う——
 * こちらはカウンタが観測できる同一JVM内での補助証拠。copperpdf4リポジトリ
 * が隣に無い環境(単独checkout)ではスキップする。
 * </p>
 */
public class FloatSplitPlanShadowSmokeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** foliojet4作業ディレクトリから見たcopperpdf4のSMOKEマニフェスト。 */
	private static final Path SMOKE_MANIFEST = Path.of("../copperpdf4/dev/files/visual/SMOKE-MANIFEST.txt");

	public void testSmokeCorpusShadowHasNoMismatches() throws Exception {
		if (!Files.isRegularFile(SMOKE_MANIFEST)) {
			System.out.println("SKIP: " + SMOKE_MANIFEST.toAbsolutePath().normalize() + " がありません(単独checkout)");
			return;
		}
		final Path docsDir = SMOKE_MANIFEST.getParent();
		final List<String> sourcePaths = new ArrayList<>();
		parseManifest(SMOKE_MANIFEST, new HashSet<>(), sourcePaths);

		ContinuationStats.reset();
		final List<String> failures = new ArrayList<>();
		final File scratch = File.createTempFile("float-split-plan-smoke", ".pdf");
		scratch.deleteOnExit();
		for (final String sourcePath : sourcePaths) {
			final long mismatchesBefore = ContinuationStats.FLOAT_SPLIT_PLAN_MISMATCHES.get();
			try {
				this.transcode(docsDir.resolve(sourcePath).normalize().toFile(), scratch);
			} catch (final Exception e) {
				failures.add(sourcePath + ": " + e);
			}
			if (ContinuationStats.FLOAT_SPLIT_PLAN_MISMATCHES.get() != mismatchesBefore) {
				System.out.println("FLOAT_SPLIT_PLAN mismatch caused by: " + sourcePath);
			}
		}
		scratch.delete();

		System.out.println("SMOKE documents: total=" + sourcePaths.size() + " failed=" + failures.size());
		System.out.println("FLOAT_SPLIT_PLAN_MATCHES=" + ContinuationStats.FLOAT_SPLIT_PLAN_MATCHES.get());
		System.out.println("FLOAT_SPLIT_PLAN_MISMATCHES=" + ContinuationStats.FLOAT_SPLIT_PLAN_MISMATCHES.get());

		assertTrue("変換失敗: " + failures, failures.isEmpty());
		assertTrue("このコーパスではsplitPageAxisのshadow比較が少なくとも1回は発火するはずです(空振り検知)",
				ContinuationStats.FLOAT_SPLIT_PLAN_MATCHES.get() > 0);
		assertEquals("FLOAT_SPLIT_PLAN_MISMATCHES", 0, ContinuationStats.FLOAT_SPLIT_PLAN_MISMATCHES.get());
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
