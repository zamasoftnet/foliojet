package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Use differences to check whether I/O properties <b>actually take effect</b>
 * (introduced on 2026-08-02).
 *
 * <p>
 * Some {@code PdfIoPropertyTest} cases only check that setting a property does not break conversion;
 * <b>they pass even if the property is completely ignored</b>.
 * Such a defect (the HTTP User-Agent was not sent) actually persisted for a long time.
 * Here, <b>convert the same document with two settings and check that the output differs</b>.
 * This detects whether the property is wired up without knowing where or how its value appears.
 * </p>
 *
 * <p>
 * No difference might also mean that the property does not affect this particular document,
 * so <b>choosing a document it should affect</b> is central to designing this check.
 * </p>
 */
public class EffectiveIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File TEXT = new File("files/unittest/ioprops/two-pages.html");

	private static final File WITH_IMAGE = new File("files/unittest/ioprops/link-and-image.html");

	/** One difference check. */
	private record Case(String name, File document, Map<String, String> a, Map<String, String> b) {
	}

	private static List<Case> cases() {
		final List<Case> cases = new ArrayList<>();
		final String watermark = new File("files/unittest/red.png").toURI().toString();

		cases.add(new Case("output.default-font-family", TEXT,
				props("output.default-font-family", "serif"), props("output.default-font-family", "monospace")));
		cases.add(new Case("output.color", TEXT, props("output.color", "rgb"), props("output.color", "cmyk")));
		cases.add(new Case("output.resolution", WITH_IMAGE,
				props("output.resolution", "96"), props("output.resolution", "192")));
		cases.add(new Case("output.text-size", TEXT,
				props("output.text-size", "1"), props("output.text-size", "2")));
		cases.add(new Case("output.page-margins", TEXT,
				props("output.page-margins", "0pt"), props("output.page-margins", "50pt")));
		cases.add(new Case("output.n-up.order", TEXT,
				props("output.n-up", "2", "output.n-up.order", "horizontal"),
				props("output.n-up", "2", "output.n-up.order", "vertical")));
		cases.add(new Case("output.marks.spine-width", TEXT,
				props("output.marks", "crop", "output.marks.spine-width", "0pt"),
				props("output.marks", "crop", "output.marks.spine-width", "20pt")));
		cases.add(new Case("output.print-mode", TEXT,
				props("output.print-mode", "double-side"), props("output.print-mode", "left-side")));
		cases.add(new Case("output.pdf.watermark.mode", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "center"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "tile")));
		cases.add(new Case("output.pdf.watermark.opacity", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "1"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "0.3")));
		cases.add(new Case("output.pdf.watermark.print", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "true"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "false")));
		cases.add(new Case("output.pdf.watermark.view", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "true"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "false")));
		cases.add(new Case("output.pdf.image.max-width", WITH_IMAGE,
				props("output.pdf.image.max-width", "1000"), props("output.pdf.image.max-width", "8")));
		cases.add(new Case("output.pdf.image.max-height", WITH_IMAGE,
				props("output.pdf.image.max-height", "1000"), props("output.pdf.image.max-height", "8")));
		cases.add(new Case("output.pdf.jpeg-image", WITH_IMAGE,
				props("output.pdf.jpeg-image", "false"), props("output.pdf.jpeg-image", "true")));
		cases.add(new Case("output.broken-image", WITH_IMAGE,
				props("input.exclude", "**/red.png", "input.include", "**", "output.broken-image", "hidden"),
				props("input.exclude", "**/red.png", "input.include", "**", "output.broken-image", "cross")));
		return cases;
	}

	public void testPropertiesChangeTheOutput() throws Exception {
		final List<String> noEffect = new ArrayList<>();
		for (final Case c : cases()) {
			final String a = this.convert(c.document(), c.a());
			final String b = this.convert(c.document(), c.b());
			if (a.equals(b)) {
				noEffect.add(c.name());
			}
		}
		if (!noEffect.isEmpty()) {
			fail("値を変えても出力が変わらない(配線されていない疑い)" + noEffect.size() + "件: "
					+ String.join(" / ", noEffect));
		}
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				if (!properties.containsKey("input.include") && !properties.containsKey("input.exclude")) {
					session.property("input.include", "**");
				}
				session.property("output.pdf.compression", "none");
				// Without fixing the generation time and file ID, output changes even with identical settings,
				// making the difference check meaningless.
				session.property("output.pdf.meta.creation-date", "2020-01-02 03:04:05");
				session.property("output.pdf.meta.mod-date", "2020-01-02 03:04:05");
				session.property("output.pdf.file-id", "0123456789abcdef0123456789abcdef");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
