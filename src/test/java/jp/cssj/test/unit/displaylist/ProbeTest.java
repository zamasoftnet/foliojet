package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

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
 * Tool to lay out arbitrary documents with the local engine and emit PDFs and per-page display lists
 * (2026-10-05). Avoid writing a throwaway test for each investigation. Without {@code -Dfoliojet.probe},
 * do nothing (inactive during ordinary tests).
 *
 * <pre>
 * wsl -e bash dev/tools/wsl/probe.sh name=/mnt/f/…/a.html [name=…] [-- I/O-property=value …]
 * </pre>
 *
 * <p>
 * {@code foliojet.probe} contains "name=path" pairs separated by {@code |};
 * {@code foliojet.probeProps} contains "property=value" pairs separated by {@code |}.
 * Determine input type by extension (.epub→EPUB, .html/.htm→HTML, others→XHTML).
 * Output goes to {@code local/probes/name/} (out.pdf and page-NNNN.txt).
 * Set {@code input.property-pi=true} and {@code input.include=**} by default
 * to enable page-size processing instructions.
 * </p>
 */
public class ProbeTest extends TestCase {
	public ProbeTest(String name) {
		super(name);
	}

	public void testProbe() throws Exception {
		final String spec = System.getProperty("foliojet.probe");
		if (spec == null || spec.isBlank()) {
			return;
		}
		final String props = System.getProperty("foliojet.probeProps", "");
		for (final String item : spec.split("\\|")) {
			final int eq = item.indexOf('=');
			final String name = item.substring(0, eq).trim();
			final File input = new File(item.substring(eq + 1).trim());
			final File dir = new File("local/probes/" + name);
			dir.mkdirs();
			final File[] old = dir.listFiles();
			if (old != null) {
				for (final File f : old) {
					f.delete();
				}
			}
			final String lower = input.getName().toLowerCase();
			final String mime = lower.endsWith(".epub") ? "application/epub+zip"
					: lower.endsWith(".html") || lower.endsWith(".htm") ? "text/html" : "application/xhtml+xml";
			final long started = System.nanoTime();
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
						null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					for (final String p : props.split("\\|")) {
						final int peq = p.indexOf('=');
						if (peq > 0) {
							session.property(p.substring(0, peq).trim(), p.substring(peq + 1).trim());
						}
					}
					CTISessionHelper.transcodeFile(session, input, mime, null);
				} finally {
					session.close();
				}
			}
			final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
			System.out.println("[probe] " + name + " pages=" + (pages == null ? 0 : pages.length) + " "
					+ (System.nanoTime() - started) / 1_000_000 + "ms -> " + dir.getPath());
		}
	}
}
