package jp.cssj.test.unit.security;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import junit.framework.TestCase;

/**
 * A {@code jar:} URL inside an SVG opens no connection.
 *
 * <p>
 * Batik keeps a handler of its own for {@code jar:} URLs, which opened the inner URL with {@code java.net.URL}: past
 * {@code input.include}/{@code input.exclude}, the local-access guard, the redirect guard and the size and time
 * limits that the engine's resolver applies to every other fetch from an SVG ({@link SvgAclEnforcementTest}). An
 * SVG could make the server connect to any host and port named inside the {@code jar:} URL.
 * </p>
 *
 * <p>
 * The main document comes from the recording server, as in {@link SvgFetchReachabilityTest}: Batik's own check
 * compares the host of the resource (for {@code jar:}, of the inner URL) with the document's, and would hide the
 * path otherwise. Everything is allowed by the access control, so that only the refusal of the scheme is measured.
 * </p>
 */
public class SvgJarUrlTest extends TestCase {

	private ProbeServer probe;

	@Override
	protected void setUp() throws Exception {
		this.probe = new ProbeServer();
		// A real archive with a stylesheet that paints green, so that a fetch would also show on the page
		final ByteArrayOutputStream archive = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(archive)) {
			zip.putNextEntry(new ZipEntry("a.css"));
			zip.write(".p { fill: #00ff00; } rect { fill: #00ff00; }".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		this.probe.put("/probe.jar", "application/java-archive", archive.toByteArray());
	}

	@Override
	protected void tearDown() throws Exception {
		this.probe.close();
	}

	private String jarUrl() {
		return "jar:" + this.probe.url("/probe.jar") + "!/a.css";
	}

	/** {@code @import} of a {@code jar:} URL in the style sheet of an inline SVG. */
	public void testCssImportOfJarUrlOpensNoConnection() throws Exception {
		this.probe.put("/doc.html", "text/html; charset=UTF-8",
				"<!DOCTYPE html><html><body><svg width='40' height='40'>" + "<style>@import url(\"" + this.jarUrl()
						+ "\");</style>" + "<rect class='p' x='0' y='0' width='40' height='40'/>"
						+ "</svg><p>AFTER</p></body></html>");
		final ConversionProbe.Result r = new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.html"));
		this.assertNothingFetched(r);
		assertTrue("the refused URL must be reported (2814): " + r.describe(),
				r.deniedResource("2814", this.jarUrl()));
	}

	/** {@code <?xml-stylesheet?>} naming a {@code jar:} URL in an SVG document of its own. */
	public void testXmlStylesheetOfJarUrlOpensNoConnection() throws Exception {
		this.probe.put("/doc.svg", "image/svg+xml",
				"<?xml version='1.0' encoding='UTF-8'?>" + "<?xml-stylesheet type='text/css' href='" + this.jarUrl()
						+ "'?>" + "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
						+ "<rect x='0' y='0' width='40' height='40'/></svg>");
		final ConversionProbe.Result r = new ConversionProbe().include("**").convertUrl(this.probe.url("/doc.svg"));
		this.assertNothingFetched(r);
	}

	private void assertNothingFetched(final ConversionProbe.Result r) throws Exception {
		assertEquals("the archive named by a jar: URL in an SVG was fetched: " + this.probe.hitPaths(), 0,
				this.probe.hits("/probe.jar"));
		assertFalse("a style sheet from a jar: URL was applied: " + r.describe(), r.hasColor("rg", 0, 1, 0));
		assertTrue("the conversion must still produce the document: " + r.describe(),
				new String(r.pdf(), 0, Math.min(5, r.pdf().length), StandardCharsets.ISO_8859_1).equals("%PDF-"));
	}
}
