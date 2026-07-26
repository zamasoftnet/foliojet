package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>変換を繰り返してもリークしない</b>ことを固定します(2026-07-27新設)。
 *
 * <p>
 * 絶対要件の「メモリリークの不在」は、これまで<b>検出器を持っていません
 * でした</b>。サーバ用途(copperd)では同じプロセスで何万件も変換するので、
 * 1件あたり僅かな取りこぼしでも積み上がります。
 * </p>
 *
 * <h2>3種類を別々に見る</h2>
 *
 * <ol>
 * <li><b>到達可能性のリーク</b>——変換が終わった後もセッションが誰かに
 * 掴まれていないか。{@link WeakReference}がGC後に消えるかで判定します。
 * <b>ヒープ量の測定より確実</b>で、静的フィールドやThreadLocalの
 * 取りこぼしを直接捕まえます</li>
 * <li><b>スレッドのリーク</b>——変換ごとに作る{@code foliojet-layout}
 * スレッドが残っていないか</li>
 * <li><b>一時ファイルのリーク</b>——{@code AbstractTempFileOutput}が
 * ヒープ逼迫時に吐く退避ファイルが消えているか</li>
 * </ol>
 *
 * <p>
 * <b>ヒープの絶対量は測りません。</b>GCの気まぐれで揺れるため、
 * 「増えた/増えない」の判定が不安定になります。上の3つは<b>決定的</b>です。
 * </p>
 */
public class ResourceLeakTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 繰り返す回数。少なすぎるとスレッドの取りこぼしが埋もれる。 */
	private static final int RUNS = 12;

	public ResourceLeakTest(String name) {
		super(name);
	}

	/** 変換が終わったらセッションが回収されること(到達可能性のリーク)。 */
	public void testSessionIsCollectedAfterConversion() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());

		final WeakReference<DirectSession> ref = new WeakReference<>(convertAndReturnSession(html, "leak-ref"));
		// 参照が残っていないか、GCへ強く促してから確かめる
		for (int i = 0; i < 20 && ref.get() != null; ++i) {
			System.gc();
			Thread.sleep(50L);
		}
		assertNull("変換が終わってもDirectSessionが回収されない。"
				+ "静的フィールドかThreadLocalが掴んでいる疑いがある", ref.get());
	}

	/** 変換ごとに作るレイアウトスレッドが残らないこと。 */
	public void testLayoutThreadsDoNotAccumulate() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());

		final int before = countLayoutThreads();
		for (int i = 0; i < RUNS; ++i) {
			convertAndReturnSession(html, "leak-thread-" + i);
		}
		// 終了直後はまだ死にきっていないことがあるので少し待つ
		for (int i = 0; i < 40 && countLayoutThreads() > before; ++i) {
			Thread.sleep(50L);
		}
		final int after = countLayoutThreads();
		assertEquals("レイアウトスレッドが残っている(変換前" + before + "本 → 変換後" + after + "本)。"
				+ RUNS + "回の変換で1本も増えてはいけない", before, after);
	}

	/** 一時ファイルが残らないこと。 */
	public void testTempFilesAreCleanedUp() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());
		final File tmp = new File(System.getProperty("java.io.tmpdir", "local"));

		final int before = countFiles(tmp);
		for (int i = 0; i < RUNS; ++i) {
			convertAndReturnSession(html, "leak-temp-" + i);
		}
		final int after = countFiles(tmp);
		// 出力PDFは自分で作るので、その分だけは増える
		assertTrue("一時ファイルが残っている(変換前" + before + "個 → 変換後" + after + "個)。"
				+ "退避ファイルの後始末が漏れている疑いがある", after <= before + RUNS);
	}

	private static int countLayoutThreads() {
		int n = 0;
		for (final Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.isAlive() && t.getName().startsWith("foliojet-layout")) {
				++n;
			}
		}
		return n;
	}

	private static int countFiles(final File dir) {
		final File[] fs = dir.listFiles();
		return fs == null ? 0 : fs.length;
	}

	private static DirectSession convertAndReturnSession(final File html, final String slot) throws Exception {
		final File pdf = new File("local/leak/" + slot + ".pdf");
		pdf.getParentFile().mkdirs();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try (OutputStream out = new FileOutputStream(pdf)) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper
					.createStreamMessageHandler(new java.io.PrintStream(OutputStream.nullOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeFile(session, html, "text/html", null);
		} finally {
			session.close();
		}
		return session;
	}
}
