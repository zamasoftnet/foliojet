package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>変換を外から止められる</b>ことを固定します(2026-07-27新設)。
 *
 * <p>
 * <b>なぜ要るか。</b>{@code abort()}は<b>旗を立てるだけ</b>で、エンジンが
 * その旗を読む場所({@link net.zamasoft.foliojet.ua.UserAgent#checkAbort})が
 * なければ何も起きません。従来は読む場所がページの境目だけだったので、
 * <b>1ページの処理が終わらない文書は永久に止められませんでした</b>。
 * </p>
 *
 * <p>
 * これは実測で発覚しました——10万文書の掃過が7時間止まり、スレッドダンプに
 * watchdogを超えて生き残った変換スレッドが積み上がっていた(2026-07-27)。
 * サーバ用途では、暴走した変換1件がプロセス全体を巻き添えにできる状態
 * だったことになります。
 * </p>
 */
public class AbortTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public AbortTest(String name) {
		super(name);
	}

	/**
	 * 中断要求を出したら、<b>ページの境目を待たずに</b>変換が終わること。
	 *
	 * <p>
	 * 極小の紙面に長い表を置く。1ページの中で行を延々と配置し続けるので、
	 * 中断点がページの境目にしかなければ止まりません。
	 * </p>
	 */
	public void testAbortStopsConversionMidPage() throws Exception {
		final File html = writeLongDocument();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		final Throwable[] failure = new Throwable[1];
		final boolean[] finished = { false };

		final Thread worker = new Thread(() -> {
			try (OutputStream out = new FileOutputStream(new File("local/abort-test.pdf"))) {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper
						.createStreamMessageHandler(new java.io.PrintStream(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, html, "text/html", null);
			} catch (final Throwable t) {
				// 中断は例外で伝わる。ここでは「終わったこと」だけが重要
				failure[0] = t;
			} finally {
				finished[0] = true;
			}
		}, "abort-test-conversion");
		worker.setDaemon(true);
		worker.start();

		// 変換が走り出すのを少し待ってから止める
		Thread.sleep(1500L);
		session.abort(CTISession.ABORT_FORCE);

		worker.join(30_000L);
		assertFalse("中断要求を出したのに変換が止まらない。"
				+ "checkAbort() を呼ぶ場所が足りていない可能性がある", worker.isAlive());
		assertTrue(finished[0]);
	}

	/** 極小の紙面に長い表。1ページの中で行の配置が長く続く。 */
	private static File writeLongDocument() throws Exception {
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<style>@page{margin:5pt}body{margin:0;font:normal 8pt/1.2 serif}\n");
		s.append("td{border:1pt solid black}</style></head><body>\n<table>\n");
		for (int i = 0; i < 40000; ++i) {
			s.append("<tr><td>R").append(i).append("</td><td>C").append(i).append("</td></tr>\n");
		}
		s.append("</table>\n</body></html>\n");
		final File f = new File("local/abort-test.html");
		f.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
			w.write(s.toString());
		}
		return f;
	}

	/**
	 * <b>中断は入出力エラーとして報告しない</b>(2026-09-21、ドライバのマトリクス拡張で発覚)。
	 *
	 * <p>
	 * 本文を送っている最中に {@code abort()} が来ると、本文の受け口(と先読みバッファ)が
	 * 畳まれる。パーサ側にはそれが普通の {@link java.io.IOException} として見えるので、
	 * 従来は {@code ERROR_IO} で包んで報告していた。実サーバーでは中断が
	 * 「I/O error. I/O error. prefetch read-ahead terminated」として client に届いており、
	 * 型のついた {@code TranscoderException} をさらに包んだぶん前置きが二重になっていた。
	 * </p>
	 *
	 * <p>
	 * 固定するのは 2 点。報告される符号が {@code INFO_ABORT}(中断)であること、
	 * そして本文に前置きの二重や先読みの内部語が出ないこと。
	 * </p>
	 */
	public void testAbortIsReportedAsAbortNotIoError() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		final java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
		final Throwable[] bodyFailure = new Throwable[1];

		session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
		session.setMessageHandler((code, args, mes) -> messages.add(code + " " + (mes == null ? "" : mes)));
		session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		session.property("input.include", "**");
		session.property("input.property-pi", "true");
		// 実サーバーと同じ経路にする(本文を先読みバッファ越しに読む)
		session.property("input.prefetch", "true");

		final OutputStream body = session.transcode(new jp.cssj.cti2.helpers.DefaultMetaSource(
				java.net.URI.create("http://example.invalid/abort.html"), "text/html", "UTF-8"));
		final Thread writer = new Thread(() -> {
			try {
				body.write(longDocumentHead().getBytes(StandardCharsets.UTF_8));
				body.flush();
				for (int i = 0; i < 200000; ++i) {
					body.write(("<tr><td>R" + i + "</td><td>C" + i + "</td></tr>\n").getBytes(StandardCharsets.UTF_8));
				}
				body.write("</table></body></html>".getBytes(StandardCharsets.UTF_8));
				body.close();
			} catch (final Throwable t) {
				bodyFailure[0] = t;
			}
		}, "abort-report-body");
		writer.setDaemon(true);
		writer.start();

		Thread.sleep(1500L);
		session.abort(CTISession.ABORT_FORCE);
		writer.join(60_000L);
		try {
			session.close();
		} catch (final Throwable t) {
			// close は中断後の後始末。ここでは本文側の報告を見る
		}

		// 観測結果をファイルにも残す(Gradle の出力が絞られても読めるように)
		final StringBuilder seen = new StringBuilder();
		seen.append("bodyFailure=").append(bodyFailure[0] == null ? "(なし)"
				: bodyFailure[0].getClass().getName() + ": " + bodyFailure[0].getMessage()).append('\n');
		if (bodyFailure[0] instanceof jp.cssj.cti2.TranscoderException) {
			final jp.cssj.cti2.TranscoderException te = (jp.cssj.cti2.TranscoderException) bodyFailure[0];
			seen.append("code=0x").append(Integer.toHexString(te.getCode() & 0xFFFF))
					.append(" state=").append(te.getState()).append('\n');
		}
		for (final String m : messages) {
			seen.append("message: ").append(m).append('\n');
		}
		final File report = new File("local/abort-report.txt");
		report.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(report), StandardCharsets.UTF_8)) {
			w.write(seen.toString());
		}

		assertNotNull("中断したのに本文側が何の失敗も受け取っていない", bodyFailure[0]);
		final String mes = String.valueOf(bodyFailure[0].getMessage());
		assertFalse("前置きが二重になっている: " + mes, mes.contains("I/O error. I/O error."));
		assertFalse("中断が先読みの入出力エラーとして報告された: " + mes, mes.contains("prefetch read-ahead terminated"));
		if (bodyFailure[0] instanceof jp.cssj.cti2.TranscoderException) {
			final jp.cssj.cti2.TranscoderException te = (jp.cssj.cti2.TranscoderException) bodyFailure[0];
			assertEquals("中断の符号で報告されていない: " + mes, CTIMessageCodes.INFO_ABORT, te.getCode());
		}
	}

	private static String longDocumentHead() {
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<style>@page{margin:5pt}body{margin:0;font:normal 8pt/1.2 serif}\n");
		s.append("td{border:1pt solid black}</style></head><body>\n<table>\n");
		return s.toString();
	}
}
