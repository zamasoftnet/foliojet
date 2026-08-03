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
 * 取得の制限({@code input.include} / {@code input.exclude})の契約です
 * (2026-08-03新設)。
 *
 * <p>
 * <b>ローカルファイルに効いていなかった。</b> 埋め込み側が
 * {@code setSourceResolver} で独自の取得手段を差し込むと、{@code file:}
 * 資源はその手段が先に解決し、制限を通らなかった。コマンドラインも
 * ウェブアプリも汎用リゾルバを差し込むので実運用でも同じで、信頼できない
 * HTMLを変換するサーバー用途では<b>ローカルファイルの読み出しを止められ
 * なかった</b>(2026-08-02に入出力プロパティの網羅テストで判明、
 * 2026-08-03にオーナー裁定で安全側へ変更)。
 * </p>
 */
public class AccessRestrictionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
	 * <b>除外したローカルファイルが読まれないこと。</b>
	 *
	 * <p>
	 * 埋め込み側のリゾルバ(汎用リゾルバ)を差し込んだうえで検査する
	 * ——差し込まれていないときは元から制限が効くため、それでは
	 * 何も確かめたことにならない。
	 * </p>
	 */
	public void testExcludedLocalFileIsNotRead() throws Exception {
		// **先に書いたものが勝つ**ので、除外を先に置く
		final List<Short> codes = this.convert(props("input.exclude", "**/secret.css", "input.include", "**"));
		assertTrue("除外した資源が読めなかったと通知されること: " + codes, hasWarning(codes));
	}

	/** 許可したローカルファイルは読めること(締めすぎていないこと)。 */
	public void testIncludedLocalFileIsRead() throws Exception {
		final List<Short> codes = this.convert(props("input.include", "**"));
		assertFalse("許可した資源で警告が出ないこと: " + codes, hasWarning(codes));
	}

	/**
	 * 制限を設定していなければ、差し込まれたリゾルバがこれまで通り使えること。
	 *
	 * <p>
	 * 制限の既定は「一致するものが無ければ拒否」なので、設定の有無に
	 * かかわらず制限を先に出すと、設定していない利用者の取得が全部止まる。
	 * </p>
	 */
	public void testNoRestrictionKeepsEmbeddedResolver() throws Exception {
		final List<Short> codes = this.convert(props());
		assertFalse("制限が無ければ資源が読めること: " + codes, hasWarning(codes));
	}

	/** CSSが読めなかったこと(2803)か、資源の警告が来ているか。 */
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
				// **埋め込み側のリゾルバを差し込む**(CLI・ウェブアプリと同じ形)
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
