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
 * Runs the P2-3 plan-driven commit (including the plan consistency and identity anchor assertions in
 * {@code Floatings.splitPageAxis}) in-process (via {@code DirectSession}) against the SMOKE manifest
 * (51 documents representing floats, tables, page breaks, and vertical writing), and verifies that
 * no document fails with assertions enabled (2026-07-24; adapted P2-2's
 * {@code FloatSplitPlanShadowSmokeTest} when removing the shadow).
 *
 * <p>
 * A separate mechanism (a Gradle task in the server product) handles comparison with baseline images;
 * this provides supplementary evidence within the same JVM, where assertions are observable.
 * </p>
 */
public class FloatSplitCommitSmokeTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** A reduced corpus for intermediate sanity checks. */
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
				// **Skip missing inputs.** Some manifest entries refer to imported material
				// (snapshots of real sites) that cannot be published. These exist only
				// in the development working tree, not in this repository alone.
				// Treating their absence as a failure would always fail this test in a standalone checkout.
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
		// **Do not pass if no cases ran.** The greatest risk with this kind of test is
		// mistaking "all skipped because inputs were missing" for "all passed."
		assertTrue("走った文書が1件も無い(マニフェストの入力がすべて欠けている)", ran > 0);
		assertTrue("変換失敗: " + failures, failures.isEmpty());
	}

	/** A subset of the format used by {@code ImageTestRunner.parseManifest} (@file/skip=/source=). */
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
