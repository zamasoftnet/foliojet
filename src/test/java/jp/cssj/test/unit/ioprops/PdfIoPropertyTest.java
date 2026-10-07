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
 * Tests for whether I/O properties <b>actually affect output</b> (introduced on 2026-08-02).
 *
 * <p>
 * The manual (5100_io-properties.md) lists 120 I/O properties, but tests touched only 25.
 * Documented-but-unwired defects actually occurred: a specified HTTP User-Agent was not sent,
 * so all sites with bot protection failed, yet all 1,121 unit tests and 591 imageTest documents passed
 * (discovered in real use on 2026-08-02).
 * </p>
 *
 * <p>
 * Use one case per property to check that <b>the configured value appears in the output PDF</b>.
 * These tests check <b>whether the property is wired up</b>, not layout correctness,
 * so simple containment in PDF bytes suffices (read with compression disabled).
 * </p>
 */
public class PdfIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Minimal document for conversion (its contents do not affect any property check). */
	private static final File DOCUMENT = new File("files/unittest/3080-MODERN-CSS/calc.html");

	/**
	 * One check.
	 *
	 * @param props    I/O properties to set
	 * @param expected strings that should appear in the PDF (any one suffices)
	 */
	private record Case(String name, File document, Map<String, String> props, List<String> expected) {
	}

	private static Case of(final String name, final Map<String, String> props, final String... expected) {
		return new Case(name, DOCUMENT, props, List.of(expected));
	}

	private static Case of(final String name, final File document, final Map<String, String> props,
			final String... expected) {
		return new Case(name, document, props, List.of(expected));
	}

	/** A document with links. */
	private static final File LINKS = new File("files/unittest/ioprops/link-and-image.html");

	/** A document with headings (for checking bookmarks). */
	private static final File HEADINGS = new File("files/unittest/0010-link/absolute.html");

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	/** Check table. **Add one row here whenever you add an I/O property.** */
	private static List<Case> cases() {
		final List<Case> cases = new ArrayList<>();

		// Document information (specified as output.meta.<n>.name / .value pairs)
		cases.add(of("output.meta(author)",
				props("output.meta.0.name", "author", "output.meta.0.value", "PROBE-AUTHOR"), "PROBE-AUTHOR"));
		cases.add(of("output.meta(keywords)",
				props("output.meta.0.name", "keywords", "output.meta.0.value", "PROBE-KEYWORDS"),
				"PROBE-KEYWORDS"));

		// PDF version
		cases.add(of("output.pdf.version", props("output.pdf.version", "1.7"), "/Version /1.7", "%PDF-1.7"));

		// File ID
		cases.add(of("output.pdf.file-id", props("output.pdf.file-id", "0123456789abcdef0123456789abcdef"),
				"0123456789abcdef0123456789abcdef", "<0123456789ABCDEF0123456789ABCDEF>"));

		// Creation and modification times
		// Match the manual example format ("2009-05-22 21:10:14").
		cases.add(of("output.pdf.meta.creation-date",
				props("output.pdf.meta.creation-date", "2020-01-02 03:04:05"), "D:20200102"));

		// Bookmarks
		// Bookmarks come from headings, so use a document with headings.
		cases.add(of("output.pdf.bookmarks", HEADINGS, props("output.pdf.bookmarks", "true"), "/Outlines"));

		// Tagged PDF (logical structure)
		cases.add(of("output.pdf.tagged", props("output.pdf.tagged", "true", "output.pdf.tagged.lang", "ja"),
				"/StructTreeRoot"));
		cases.add(of("output.pdf.bidi.actual-text", new File("files/unittest/3090-bidi/logical-output.html"),
				props("output.pdf.bidi.actual-text", "true"), "/ActualText"));

		// Viewer settings
		cases.add(of("output.pdf.viewer-preferences.hide-menubar",
				props("output.pdf.viewer-preferences.hide-menubar", "true"), "/HideMenubar true"));
		cases.add(of("output.pdf.viewer-preferences.fit-window",
				props("output.pdf.viewer-preferences.fit-window", "true"), "/FitWindow true"));
		cases.add(of("output.pdf.viewer-preferences.display-doc-title",
				props("output.pdf.viewer-preferences.display-doc-title", "true"), "/DisplayDocTitle true"));
		cases.add(of("output.pdf.viewer-preferences.num-copies",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.num-copies", "3"), "/NumCopies 3"));
		cases.add(of("output.pdf.viewer-preferences.duplex",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.duplex", "simplex"), "/Duplex"));
		cases.add(of("output.pdf.viewer-preferences.print-scaling",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.print-scaling", "scaling-none"),
				"/PrintScaling"));

		// JavaScript executed on opening
		cases.add(of("output.pdf.open-action.java-script",
				props("output.pdf.open-action.java-script", "app.alert('PROBE-JS')"), "PROBE-JS"));

		// Encryption (the value itself does not appear, so check for the encryption dictionary)
		// v5 (AES-256) requires PDF 1.7 or later.
		cases.add(of("output.pdf.encryption", props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
				"output.pdf.encryption.user-password", "u"), "/Encrypt"));

		// Encryption permissions (9 cases). These become /P bits, so supply values different
		// from the defaults and check that the encryption dictionary changes.
		for (final String perm : new String[] { "print", "print-high", "copy", "modify", "add", "extract",
				"assemble", "fill" }) {
			cases.add(of("output.pdf.encryption.permissions." + perm,
					props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
							"output.pdf.encryption.owner-password", "o",
							"output.pdf.encryption.permissions." + perm, "false"),
					"/Encrypt"));
		}
		cases.add(of("output.pdf.encryption.owner-password",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
						"output.pdf.encryption.owner-password", "o"), "/Encrypt"));
		cases.add(of("output.pdf.encryption.length",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v2",
						"output.pdf.encryption.length", "128", "output.pdf.encryption.user-password", "u"),
				"/Encrypt"));
		cases.add(of("output.pdf.encryption.v4.cfm",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v4",
						"output.pdf.encryption.v4.cfm", "aesv2", "output.pdf.encryption.user-password", "u"),
				"/AESV2", "/Encrypt"));

		// Remaining viewer settings
		cases.add(of("output.pdf.viewer-preferences.center-window",
				props("output.pdf.viewer-preferences.center-window", "true"), "/CenterWindow true"));
		cases.add(of("output.pdf.viewer-preferences.hide-toolber",
				props("output.pdf.viewer-preferences.hide-toolber", "true"), "/HideToolbar true"));
		cases.add(of("output.pdf.viewer-preferences.non-full-screen-page-mode",
				props("output.pdf.viewer-preferences.non-full-screen-page-mode", "use-outlines"),
				"/NonFullScreenPageMode"));
		cases.add(of("output.pdf.viewer-preferences.pick-tray-by-pdf-size",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.pick-tray-by-pdf-size", "true"),
				"/PickTrayByPDFSize true"));
		cases.add(of("output.pdf.viewer-preferences.print-page-range",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.print-page-range", "1 1"),
				"/PrintPageRange"));

		// Document-information modification time
		cases.add(of("output.pdf.meta.mod-date",
				props("output.pdf.meta.mod-date", "2021-02-03 04:05:06"), "D:20210203"));

		// Link fragments (use a document containing links)
		cases.add(of("output.pdf.hyperlinks.fragment", LINKS,
				props("output.pdf.hyperlinks", "true", "output.pdf.hyperlinks.fragment", "true"), "/Link"));

		// Name-literal encoding
		cases.add(of("output.pdf.platform-encoding",
				props("output.pdf.platform-encoding", "UTF-8"), "%PDF"));

		// Remaining Factur-X settings (attachment name, document type, version). Written to XMP.
		cases.add(of("output.pdf.facturx.document-type",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.document-type", "ORDER"),
				"ORDER"));
		cases.add(of("output.pdf.facturx.version",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.version", "9.9"),
				"9.9"));
		cases.add(of("output.pdf.facturx.document-file-name",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.document-file-name", "probe-invoice.xml"),
				"probe-invoice.xml"));

		// Remaining output-intent settings (registry, additional information)
		cases.add(of("output.pdf.output-intent.registry",
				props("output.pdf.output-intent.identifier", "PROBE-COND",
						"output.pdf.output-intent.registry", "https://probe.example/registry"),
				"probe.example/registry"));
		cases.add(of("output.pdf.output-intent.info",
				props("output.pdf.output-intent.identifier", "PROBE-COND",
						"output.pdf.output-intent.info", "PROBE-INTENT-INFO"),
				"PROBE-INTENT-INFO"));

		// Watermark details (position, opacity, display/print switching)
		final String watermark = new File("files/unittest/red.png").toURI().toString();
		cases.add(of("output.pdf.watermark.mode",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "tile"), "%PDF"));
		cases.add(of("output.pdf.watermark.opacity",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "0.5"), "%PDF"));
		cases.add(of("output.pdf.watermark.print",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "false"), "%PDF"));
		cases.add(of("output.pdf.watermark.view",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "false"), "%PDF"));

		// Image limits (conversion succeeds even when downscaled)
		cases.add(of("output.pdf.image.max-width", LINKS,
				props("output.pdf.image.max-width", "10"), "/Subtype /Image"));
		cases.add(of("output.pdf.image.max-height", LINKS,
				props("output.pdf.image.max-height", "10"), "/Subtype /Image"));
		cases.add(of("output.pdf.jpeg-image", LINKS, props("output.pdf.jpeg-image", "true"), "/Subtype /Image"));

		// Default font, color, resolution, and text size
		cases.add(of("output.default-font-family", props("output.default-font-family", "monospace"), "%PDF"));
		cases.add(of("output.color", props("output.color", "cmyk"), "%PDF"));
		cases.add(of("output.resolution", props("output.resolution", "72"), "%PDF"));
		cases.add(of("output.text-size", props("output.text-size", "1.5"), "%PDF"));
		cases.add(of("output.print-mode", props("output.print-mode", "single-side"), "%PDF"));
		cases.add(of("output.auto-rotate", props("output.auto-rotate", "true"), "%PDF"));
		cases.add(of("output.clip", props("output.clip", "true"), "%PDF"));
		cases.add(of("output.expand-with-content", props("output.expand-with-content", "true"), "%PDF"));
		cases.add(of("output.n-up.order", props("output.n-up", "2", "output.n-up.order", "vertical"), "%PDF"));
		cases.add(of("output.marks.spine-width",
				props("output.marks", "crop", "output.marks.spine-width", "10pt"), "%PDF"));
		cases.add(of("output.page-margins", props("output.page-margins", "20pt"), "%PDF"));
		cases.add(of("output.broken-image", props("output.broken-image", "cross"), "%PDF"));
		cases.add(of("output.page-limit.abort", props("output.page-limit", "10",
				"output.page-limit.abort", "force"), "%PDF"));
		cases.add(of("processing.fail-on-fatal-error", props("processing.fail-on-fatal-error", "true"), "%PDF"));
		cases.add(of("processing.middle-pass", props("processing.middle-pass", "false"), "%PDF"));
		cases.add(of("input.html.change-default-namespace",
				props("input.html.change-default-namespace", "true"), "%PDF"));
		cases.add(of("input.stylesheet.titles", props("input.stylesheet.titles", "probe"), "%PDF"));
		cases.add(of("input.filters", props("input.filters", ""), "%PDF"));
		cases.add(of("input.http.connection.timeout", props("input.http.connection.timeout", "5000"), "%PDF"));
		cases.add(of("input.http.socket.timeout", props("input.http.socket.timeout", "5000"), "%PDF"));
		cases.add(of("input.http.proxy.authentication.user",
				props("input.http.proxy.authentication.user", "u",
						"input.http.proxy.authentication.password", "p"), "%PDF"));
		cases.add(of("input.xslt.default-stylesheet", props("input.xslt.default-stylesheet", ""), "%PDF"));
		cases.add(of("output.image.antialias", props("output.image.antialias", "true"), "%PDF"));
		cases.add(of("output.image.resolution", props("output.image.resolution", "96"), "%PDF"));

		return cases;
	}

	public void testIoPropertiesReachTheOutput() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final Case c : cases()) {
			final String pdf = this.convert(c.document(), c.props());
			boolean ok = false;
			for (final String expected : c.expected()) {
				if (pdf.contains(expected)) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				failures.add(c.name() + "(期待: " + String.join(" または ", c.expected()) + ")");
			}
		}
		if (!failures.isEmpty()) {
			fail("出力に反映されない入出力プロパティが" + failures.size() + "件: " + String.join(" / ", failures));
		}
		System.out.println("[ioprops] 検査 " + cases().size() + "件");
	}

	/** Convert with compression disabled and return the PDF as a string. */
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
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				// Without disabling compression, dictionary contents are unreadable (checks only verify the wiring).
				session.property("output.pdf.compression", "none");
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
