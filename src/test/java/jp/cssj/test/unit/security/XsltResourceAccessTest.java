package jp.cssj.test.unit.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * ACLs apply to resource fetching from XSLT (introduced on 2026-09-08).
 *
 * <p>
 * FolioJet's {@code XSLTProcessorFilter} implements {@code URIResolver} and installs it in both
 * the transformer factory and transformer. Thus, {@code document()} and {@code xsl:import}
 * pass through {@code ua.resolve()}: <b>this correctly uses Saxon's extension point</b>.
 * </p>
 *
 * <p>
 * However, {@code unparsed-text()} uses {@code UnparsedTextURIResolver}, not {@code URIResolver},
 * and whether Saxon substitutes the latter is <b>implementation-dependent</b>.
 * An attempted measurement on 2026-09-08 failed because the probe itself was broken,
 * leaving this unverified. Check it here.
 * </p>
 */
public class XsltResourceAccessTest extends TestCase {

	private static final String SECRET = "XSLTSECRETMARKER";

	private ProbeServer probe;

	private File secretFile;

	@Override
	protected void setUp() throws Exception {
		this.probe = new ProbeServer();
		this.secretFile = new File("build/tmp/xslt-secret.txt").getAbsoluteFile();
		this.secretFile.getParentFile().mkdirs();
		Files.write(this.secretFile.toPath(), SECRET.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	protected void tearDown() throws Exception {
		this.probe.close();
		this.secretFile.delete();
	}

	/** Place XSLT on the recording server that evaluates {@code expr} in its body and emits it in the type area. */
	private void putStylesheet(final String expr) {
		this.probe.put("/s.xsl", "text/xsl",
				"<?xml version='1.0' encoding='UTF-8'?>"
						+ "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
						+ "<xsl:template match='/data'><html><body><p>[<xsl:value-of select=\"" + expr
						+ "\"/>]</p></body></html></xsl:template></xsl:stylesheet>");
		this.probe.put("/d.xml", "application/xml",
				"<?xml version='1.0' encoding='UTF-8'?>"
						+ "<?xml-stylesheet href='" + this.probe.url("/s.xsl") + "' type='text/xsl'?>"
						+ "<data/>");
	}

	/** An executable wrapping checked exceptions for passing to {@link #assertDenied}. */
	private void renderQuietly() {
		try {
			this.renderRestricted();
		} catch (final RuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Lay out with only the main document and stylesheet allowed. */
	private ConversionProbe.Result renderRestricted() throws Exception {
		return new ConversionProbe().include(this.probe.url("/d.xml")).include(this.probe.url("/s.xsl"))
				.convertUrl(this.probe.url("/d.xml"));
	}

	/**
	 * Verify that access is denied.
	 *
	 * <p>
	 * In XSLT, denial <b>propagates as an exception and stops conversion</b>, rather than producing
	 * a type area with missing contents. This is stronger blocking, so the pass criterion is
	 * that the exception contains the denied URI.
	 * </p>
	 */
	private void assertDenied(final String deniedUri, final Runnable body) {
		try {
			body.run();
			fail("拒まれなかった: " + deniedUri);
		} catch (final RuntimeException e) {
			final String text = describe(e);
			assertTrue("拒否されたが、理由に対象のURIが無い: " + text, text.contains(deniedUri));
			assertTrue("拒否ではない失敗: " + text, text.contains("Access denied"));
		}
	}

	private static String describe(final Throwable e) {
		final StringBuilder buff = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause()) {
			buff.append(t.getMessage()).append(" / ");
			if (t.getCause() == t) {
				break;
			}
		}
		return buff.toString();
	}

	/** {@code unparsed-text()} cannot read local files. */
	public void testUnparsedTextCannotReadLocalFile() throws Exception {
		this.putStylesheet("unparsed-text('" + this.secretFile.toURI() + "')");
		// file: notation varies between file:/F:/… and file:///F:/…, so compare by filename.
		this.assertDenied(this.secretFile.getName(), this::renderQuietly);
	}

	/** {@code unparsed-text()} does not access unauthorized hosts. */
	public void testUnparsedTextObeysAcl() throws Exception {
		this.probe.put("/secret.txt", "text/plain", SECRET);
		this.putStylesheet("unparsed-text('" + this.probe.url("/secret.txt") + "')");
		this.assertDenied(this.probe.url("/secret.txt"), this::renderQuietly);
		assertEquals("許していないunparsed-text()の取得先へ通信した: " + this.probe.hitPaths(), 0,
				this.probe.hits("/secret.txt"));
	}

	/** {@code unparsed-text()} works when allowed (positive case). Detect excessive blocking. */
	public void testUnparsedTextAllowedWhenIncluded() throws Exception {
		this.probe.put("/secret.txt", "text/plain", SECRET);
		this.putStylesheet("unparsed-text('" + this.probe.url("/secret.txt") + "')");
		final ConversionProbe.Result r = new ConversionProbe().include("**").convertUrl(this.probe.url("/d.xml"));
		System.out.println("[XSLT unparsed-text 許可] 到達=" + this.probe.hitPaths() + " 版面="
				+ containsSecret(r) + " " + r.describe());
		assertTrue("許したunparsed-text()が取得されていない: " + this.probe.hitPaths(),
				this.probe.hits("/secret.txt") >= 1);
	}

	/** The ACL applies to {@code document()}. */
	public void testDocumentFunctionObeysAcl() throws Exception {
		this.probe.put("/other.xml", "application/xml", "<r>" + SECRET + "</r>");
		this.putStylesheet("document('" + this.probe.url("/other.xml") + "')/r");
		this.assertDenied(this.probe.url("/other.xml"), this::renderQuietly);
		assertEquals("許していないdocument()の取得先へ通信した: " + this.probe.hitPaths(), 0,
				this.probe.hits("/other.xml"));
	}

	/** {@code document()} cannot read local files. */
	public void testDocumentFunctionCannotReadLocalFile() throws Exception {
		final File xml = new File("build/tmp/xslt-secret.xml").getAbsoluteFile();
		Files.write(xml.toPath(), ("<r>" + SECRET + "</r>").getBytes(StandardCharsets.UTF_8));
		try {
			this.putStylesheet("document('" + xml.toURI() + "')/r");
			this.assertDenied(xml.getName(), this::renderQuietly);
		} finally {
			xml.delete();
		}
	}

	/** Check that characters appear in sequence one at a time, so character-advance splits do not hide them. */
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
