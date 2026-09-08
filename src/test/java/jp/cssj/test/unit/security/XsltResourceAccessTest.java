package jp.cssj.test.unit.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * XSLTからの資源取得にACLが効くこと(2026-09-08新設)。
 *
 * <p>
 * FolioJetは{@code XSLTProcessorFilter}が{@code URIResolver}を実装し、
 * 変換器ファクトリと変換器の両方に差しています。だから{@code document()}や
 * {@code xsl:import}は{@code ua.resolve()}を通ります——<b>これがSaxonの
 * 用意した差し込み口を正しく使った形</b>です。
 * </p>
 *
 * <p>
 * ただし{@code unparsed-text()}は{@code URIResolver}ではなく
 * {@code UnparsedTextURIResolver}の担当で、Saxonが前者で代用するかどうかは
 * <b>実装依存</b>です。2026-09-08に一度測ろうとしてプローブ自体が壊れ、
 * 未確認のまま残っていました。ここで確かめます。
 * </p>
 */
public class XsltResourceAccessTest extends TestCase {

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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

	/** 本体で{@code expr}を評価して版面に出すXSLTを、記録用サーバへ置きます。 */
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

	/** {@link #assertDenied}へ渡すための、検査例外を包んだ実行です。 */
	private void renderQuietly() {
		try {
			this.renderRestricted();
		} catch (final RuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** 主文書とスタイルシートだけを許して組みます。 */
	private ConversionProbe.Result renderRestricted() throws Exception {
		return new ConversionProbe().include(this.probe.url("/d.xml")).include(this.probe.url("/s.xsl"))
				.convertUrl(this.probe.url("/d.xml"));
	}

	/**
	 * 拒否されることを確かめます。
	 *
	 * <p>
	 * XSLTでは拒否が<b>例外として上がり、変換がそこで止まります</b>
	 * (版面が出て中身だけ落ちる、ではない)。遮断としてはその方が強いので、
	 * 例外に拒んだURIが入っていることを合格条件にします。
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

	/** {@code unparsed-text()}でローカルファイルを読めないこと。 */
	public void testUnparsedTextCannotReadLocalFile() throws Exception {
		this.putStylesheet("unparsed-text('" + this.secretFile.toURI() + "')");
		// file: の表記は file:/F:/… と file:///F:/… で揺れるのでファイル名で照合する
		this.assertDenied(this.secretFile.getName(), this::renderQuietly);
	}

	/** {@code unparsed-text()}が許していないホストへ出ていかないこと。 */
	public void testUnparsedTextObeysAcl() throws Exception {
		this.probe.put("/secret.txt", "text/plain", SECRET);
		this.putStylesheet("unparsed-text('" + this.probe.url("/secret.txt") + "')");
		this.assertDenied(this.probe.url("/secret.txt"), this::renderQuietly);
		assertEquals("許していないunparsed-text()の取得先へ通信した: " + this.probe.hitPaths(), 0,
				this.probe.hits("/secret.txt"));
	}

	/** 許したときは{@code unparsed-text()}が使えること(正例)。遮断しすぎの検出。 */
	public void testUnparsedTextAllowedWhenIncluded() throws Exception {
		this.probe.put("/secret.txt", "text/plain", SECRET);
		this.putStylesheet("unparsed-text('" + this.probe.url("/secret.txt") + "')");
		final ConversionProbe.Result r = new ConversionProbe().include("**").convertUrl(this.probe.url("/d.xml"));
		System.out.println("[XSLT unparsed-text 許可] 到達=" + this.probe.hitPaths() + " 版面="
				+ containsSecret(r) + " " + r.describe());
		assertTrue("許したunparsed-text()が取得されていない: " + this.probe.hitPaths(),
				this.probe.hits("/secret.txt") >= 1);
	}

	/** {@code document()}にACLが効くこと。 */
	public void testDocumentFunctionObeysAcl() throws Exception {
		this.probe.put("/other.xml", "application/xml", "<r>" + SECRET + "</r>");
		this.putStylesheet("document('" + this.probe.url("/other.xml") + "')/r");
		this.assertDenied(this.probe.url("/other.xml"), this::renderQuietly);
		assertEquals("許していないdocument()の取得先へ通信した: " + this.probe.hitPaths(), 0,
				this.probe.hits("/other.xml"));
	}

	/** {@code document()}でローカルファイルを読めないこと。 */
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

	/** 字送りで分断されても拾えるように、1文字ずつ順に現れるかで見ます。 */
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
