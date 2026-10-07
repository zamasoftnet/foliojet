package jp.cssj.test.unit.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * XML external entities cannot reach local files or internal hosts (introduced on 2026-09-08).
 *
 * <p>
 * {@code <!DOCTYPE svg [<!ENTITY x SYSTEM "file:///…">]>} is a classic bypass for resource access control.
 * {@code input.include} sits before the source resolver, but <b>XML parser entity resolution
 * may bypass it</b>.
 * </p>
 *
 * <p>
 * Measure two things: <b>local-file reads</b> (whether secrets appear in the type area) and
 * <b>communication with internal hosts</b> (whether the server can be used as a relay).
 * Judge both by <b>whether distinctive content appears or requests reach the recording server</b>,
 * not by conversion success.
 * </p>
 */
public class XmlEntityTest extends TestCase {

	/** An unusual string that is recognizable if it appears in the type area. */
	private static final String SECRET = "XXESECRETMARKER";

	private ProbeServer probe;

	private File secretFile;

	@Override
	protected void setUp() throws Exception {
		this.probe = new ProbeServer();
		this.secretFile = new File("build/tmp/xxe-secret.txt").getAbsoluteFile();
		this.secretFile.getParentFile().mkdirs();
		Files.write(this.secretFile.toPath(), SECRET.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	protected void tearDown() throws Exception {
		this.probe.close();
		this.secretFile.delete();
	}

	private static String svgWithEntity(final String systemId) {
		return "<?xml version='1.0' encoding='UTF-8'?>"
				+ "<!DOCTYPE svg [<!ENTITY probe SYSTEM '" + systemId + "'>]>"
				+ "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='40'>"
				+ "<text x='0' y='20' font-size='12'>&probe;</text></svg>";
	}

	/**
	 * SVG external entities cannot read server-local files.
	 *
	 * <p>
	 * Serve the main document from the recording server. Serving it from {@code file:} would make it
	 * impossible to distinguish reading due to the same location from reading due to missing restrictions.
	 * </p>
	 */
	public void testExternalEntityCannotReadLocalFile() throws Exception {
		final String svg = svgWithEntity(this.secretFile.toURI().toString());
		this.probe.put("/doc.svg", "image/svg+xml", svg);
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.svg"))
				.convertUrl(this.probe.url("/doc.svg"));
		System.out.println("[XXE ローカル] " + r.describe());
		assertFalse("ローカルファイルの中身が版面に出た: " + r.describe(), containsSecret(r));
	}

	/**
	 * External entities cannot read local files even in the <b>untrusted input</b> setup.
	 *
	 * <p>
	 * The caller pushes the document itself, allows everything in the ACL, and sets
	 * {@code localAccessAllowed=false}. This approximates copper-mcp's {@code --untrusted}.
	 * Do not fetch the main document by URL: the recording server itself is at {@code 127.0.0.1},
	 * so this setting would <b>block fetching the main document too</b> (this actually happened once).
	 * </p>
	 */
	public void testExternalEntityCannotReadLocalFileWhenUntrusted() throws Exception {
		final String svg = svgWithEntity(this.secretFile.toURI().toString());
		final ConversionProbe.Result r = new ConversionProbe().include("**").localAccessAllowed(false)
				.convert(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml");
		System.out.println("[XXE 信頼しない入力] " + r.describe());
		assertFalse("信頼しない入力でもローカルファイルの中身が版面に出た: " + r.describe(), containsSecret(r));
	}

	/** SVG external entities do not communicate with unauthorized hosts. */
	public void testExternalEntityObeysAcl() throws Exception {
		this.probe.put("/entity.txt", "text/plain", SECRET);
		final String svg = svgWithEntity(this.probe.url("/entity.txt"));
		this.probe.put("/doc.svg", "image/svg+xml", svg);
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.svg"))
				.convertUrl(this.probe.url("/doc.svg"));
		System.out.println("[XXE 通信] 到達=" + this.probe.hitPaths() + " " + r.describe());
		assertEquals("許していない外部実体が取得された: " + this.probe.hitPaths(), 0, this.probe.hits("/entity.txt"));
		assertFalse("許していない外部実体の中身が版面に出た", containsSecret(r));
	}

	/** The same holds for HTML DOCTYPE. */
	public void testHtmlExternalEntityCannotReadLocalFile() throws Exception {
		final String html = "<!DOCTYPE html [<!ENTITY probe SYSTEM '" + this.secretFile.toURI() + "'>]>"
				+ "<html><body><p>&probe;</p></body></html>";
		this.probe.put("/doc.html", "text/html; charset=UTF-8", html);
		final ConversionProbe.Result r = new ConversionProbe().include(this.probe.url("/doc.html"))
				.convertUrl(this.probe.url("/doc.html"));
		System.out.println("[XXE HTML] " + r.describe());
		assertFalse("HTMLの外部実体でローカルファイルの中身が版面に出た: " + r.describe(), containsSecret(r));
	}

	/**
	 * Whether the secret appears in the type area.
	 *
	 * <p>
	 * PDF content streams are split by character advances, so <b>count it as present if its characters
	 * appear in order, even one at a time</b>. A naive {@code contains} would miss it.
	 * </p>
	 */
	private static boolean containsSecret(final ConversionProbe.Result r) throws Exception {
		final String ops = r.operators();
		int at = 0;
		for (int i = 0; i < SECRET.length(); i++) {
			at = ops.indexOf(SECRET.charAt(i), at);
			if (at < 0) {
				return false;
			}
			at++;
		}
		return true;
	}
}
