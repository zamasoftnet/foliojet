package jp.cssj.test.unit.security;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * XMLの外部実体でローカルファイルや内側のホストへ届かないこと(2026-09-08新設)。
 *
 * <p>
 * {@code <!DOCTYPE svg [<!ENTITY x SYSTEM "file:///…">]>}は、資源アクセス制御の
 * 古典的な迂回路です。{@code input.include}はソースリゾルバの手前にありますが、
 * <b>XMLパーサの実体解決はそこを通らない</b>ことがあります。
 * </p>
 *
 * <p>
 * 測るのは2つ。<b>ローカルファイルの読み出し</b>(秘密が版面に出るか)と、
 * <b>内側のホストへの通信</b>(踏み台になるか)です。どちらも
 * 「変換が成功したか」ではなく、<b>固有の内容が出たか・記録用サーバに来たか</b>で
 * 判定します。
 * </p>
 */
public class XmlEntityTest extends TestCase {

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 版面に出たら分かる、ありふれない文字列です。 */
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
	 * SVGの外部実体でサーバーのローカルファイルを読めないこと。
	 *
	 * <p>
	 * 主文書は記録用サーバから配ります。{@code file:}から配ると、
	 * 「同じ場所だから読めた」のか「制限が無いから読めた」のか区別できません。
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
	 * <b>信頼しない入力</b>の形でも、外部実体でローカルファイルを読めないこと。
	 *
	 * <p>
	 * 呼び出し側が文書そのものを押し込み、ACLは全部許し、
	 * {@code localAccessAllowed=false}にします。これが copper-mcp の
	 * {@code --untrusted} に近い形です。主文書をURLで取らせないのは、
	 * 記録用サーバ自身が{@code 127.0.0.1}にいて、その設定では
	 * <b>主文書の取得ごと止まってしまう</b>ためです(実際に一度そうなった)。
	 * </p>
	 */
	public void testExternalEntityCannotReadLocalFileWhenUntrusted() throws Exception {
		final String svg = svgWithEntity(this.secretFile.toURI().toString());
		final ConversionProbe.Result r = new ConversionProbe().include("**").localAccessAllowed(false)
				.convert(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml");
		System.out.println("[XXE 信頼しない入力] " + r.describe());
		assertFalse("信頼しない入力でもローカルファイルの中身が版面に出た: " + r.describe(), containsSecret(r));
	}

	/** SVGの外部実体で、許していないホストへ通信しないこと。 */
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

	/** HTMLのDOCTYPEでも同じであること。 */
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
	 * 版面に秘密が出ているかどうかです。
	 *
	 * <p>
	 * PDFの内容ストリームは字送りで分断されるので、<b>1文字ずつでも順に
	 * 現れたら出たとみなします</b>。素朴な{@code contains}では見逃します。
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
