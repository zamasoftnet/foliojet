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
import jp.cssj.test.unit.TextWrapStyleOptIn;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Fallback parity test for CSS {@code text-wrap-style: pretty}
 * (M3c increment 4; migrated from the proprietary {@code text.line-breaker} property to CSS on 2026-07-25).
 * For documents containing fallback cases (floats, tabs, inline replaced elements/inline-blocks/ruby,
 * pre/pre-wrap, vertical writing, word-wrap:break-word, and mid-paragraph floats + inter-line page breaks),
 * verifies that pretty produces **exactly the same display list** as the default (auto=greedy).
 * This verifies increment 3's design guarantee: fallback replays accumulated events verbatim through
 * the same path as greedy layout. No golden is needed (both are generated in the same process
 * and compared directly).
 *
 * <p>
 * Because the same fixture must be laid out in both modes, pretty is enabled by loading an author
 * stylesheet ({@link TextWrapStyleOptIn#PRETTY_STYLESHEET}) via {@code input.default-stylesheet}.
 * </p>
 */
public class FallbackParityTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Documents constructed so that every paragraph requires fallback. */
	private static final String[] DOCUMENTS = { //
			"3200-line-breaker/parity-float.html", //
			"3200-line-breaker/parity-inline-parts.html", //
			"3200-line-breaker/parity-pre.html", //
			"3200-line-breaker/parity-vertical.html", //
			"3200-line-breaker/parity-midbreak-float.html", //
			"3200-line-breaker/parity-break-word.html", //
	};

	public FallbackParityTest(String name) {
		super(name);
	}

	public void testFallbackMatchesLegacy() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String doc : DOCUMENTS) {
			this.checkParity(doc, failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private void checkParity(final String doc, final List<String> failures) throws Exception {
		final String name = doc.replace('/', '_').replace(".html", "");
		final File legacyDir = new File("local/unittest/line-breaker-parity/" + name + "-legacy");
		final File optimizedDir = new File("local/unittest/line-breaker-parity/" + name + "-optimized");
		this.dump(doc, name + "-legacy", legacyDir, false);
		this.dump(doc, name + "-optimized", optimizedDir, true);

		final File[] legacyPages = legacyDir.listFiles((d, n) -> n.endsWith(".txt"));
		final File[] optimizedPages = optimizedDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, legacyPages);
		assertTrue("表示リストが出力されていません: " + doc, legacyPages.length > 0);
		if (optimizedPages == null || legacyPages.length != optimizedPages.length) {
			failures.add(doc + ": ページ数が一致しません (legacy=" + legacyPages.length + ", optimized="
					+ (optimizedPages == null ? 0 : optimizedPages.length) + ")");
			return;
		}
		for (final File legacy : legacyPages) {
			final File optimized = new File(optimizedDir, legacy.getName());
			final String expected = Files.readString(legacy.toPath(), StandardCharsets.UTF_8);
			final String got = Files.readString(optimized.toPath(), StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + legacy.getName() + ": フォールバックがlegacyと一致しません (legacy=" + legacy
						+ ", optimized=" + optimized + ")");
			}
		}
	}

	private void dump(final String doc, final String name, final File outDir, final boolean pretty)
			throws Exception {
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File("local/unittest/line-breaker-parity/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					if (pretty) {
						session.property("input.default-stylesheet", TextWrapStyleOptIn.PRETTY_STYLESHEET);
					}
					CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
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
