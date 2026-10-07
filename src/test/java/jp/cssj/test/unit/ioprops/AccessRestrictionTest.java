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
 * Contract for fetch restrictions ({@code input.include} / {@code input.exclude})
 * (introduced on 2026-08-03).
 *
 * <p>
 * <b>Restrictions did not apply to local files.</b> When an embedding application injected
 * a custom fetch mechanism through {@code setSourceResolver}, that mechanism resolved
 * {@code file:} resources first, bypassing restrictions. Both the command line and web applications
 * inject a generic resolver, so this also occurred in production. Servers converting untrusted HTML
 * <b>could not prevent local file reads</b> (found by comprehensive I/O property tests on 2026-08-02;
 * changed to the safe behavior by the owner's decision on 2026-08-03).
 * </p>
 */
public class AccessRestrictionTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private File document;

	private File secret;

	protected void setUp() throws Exception {
		final File dir = new File("local/unittest/acl");
		dir.mkdirs();
		this.secret = new File(dir, "secret.css");
		Files.writeString(this.secret.toPath(), "p { color: #123456 }\n", StandardCharsets.UTF_8);
		this.document = new File(dir, "doc.html");
		Files.writeString(this.document.toPath(), "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
				+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"secret.css\">"
				+ "</head><body><p>probe</p></body></html>\n", StandardCharsets.UTF_8);
	}

	/**
	 * <b>Excluded local files are not read.</b>
	 *
	 * <p>
	 * Check with the embedding application's resolver (a generic resolver) injected:
	 * restrictions already worked without an injected resolver, so testing without one proves nothing.
	 * </p>
	 */
	public void testExcludedLocalFileIsNotRead() throws Exception {
		// **The first rule wins**, so put the exclusion first.
		final List<Short> codes = this.convert(props("input.exclude", "**/secret.css", "input.include", "**"));
		assertTrue("除外した資源が読めなかったと通知されること: " + codes, hasWarning(codes));
	}

	/** Allowed local files can be read (restrictions are not too broad). */
	public void testIncludedLocalFileIsRead() throws Exception {
		final List<Short> codes = this.convert(props("input.include", "**"));
		assertFalse("許可した資源で警告が出ないこと: " + codes, hasWarning(codes));
	}

	/**
	 * With no restrictions configured, the injected resolver remains usable as before.
	 *
	 * <p>
	 * Restrictions default to denying access when nothing matches. Applying them first regardless
	 * of whether they are configured would stop all fetching for users who have not configured them.
	 * </p>
	 */
	public void testNoRestrictionKeepsEmbeddedResolver() throws Exception {
		final List<Short> codes = this.convert(props());
		assertFalse("制限が無ければ資源が読めること: " + codes, hasWarning(codes));
	}

	/** Check for failure to read CSS (2803) or a resource warning. */
	private static boolean hasWarning(final List<Short> codes) {
		for (final Short code : codes) {
			if ((code.shortValue() & 0xF000) == 0x2000) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<String, String>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private List<Short> convert(final Map<String, String> properties) throws Exception {
		final List<Short> codes = new ArrayList<Short>();
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> codes.add(Short.valueOf(code)));
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				// **Inject the embedding application's resolver** (the same setup as CLI and web applications).
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, this.document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return codes;
	}
}
