package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>同一文書を並行変換</b>して、単独では起きない失敗が出ないことを
 * 確認します(2026-07-26新設)。
 *
 * <p>
 * このエンジンはサーバ({@code copperd})で複数の変換を並行に処理する
 * 前提なので、<b>並行実行は本番相当の条件</b>です。ところが既存の検証は
 * すべて単一文書の逐次変換で、並行時にだけ壊れる欠陥は原理的に踏めません
 * でした。実際、ランダム生成の掃過を22スレッドへ並列化したところ、
 * <b>単独では成功するのに並行時だけ失敗する文書</b>が見つかりました。
 * </p>
 *
 * <p>
 * 既定は同梱のfixtureを少数回。掃過するときは
 * {@code -Dfoliojet.concurrentDoc=<path> -Dfoliojet.concurrentRuns=N
 * -Dfoliojet.concurrentThreads=T} で任意の文書を叩ける。
 * </p>
 */
public class ConcurrentConvertTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public ConcurrentConvertTest(String name) {
		super(name);
	}

	/** 既定の回帰: 表・段組・フロートを含む文書を並行変換しても壊れない。 */
	public void testConcurrentConversionIsStable() throws Exception {
		final String doc = System.getProperty("foliojet.concurrentDoc");
		final int runs = Integer.parseInt(System.getProperty("foliojet.concurrentRuns", "48"));
		final int threads = Integer.parseInt(System.getProperty("foliojet.concurrentThreads",
				String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors() - 2))));
		final File file = doc != null ? new File(doc)
				: new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + file, file.exists());

		final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		final AtomicInteger next = new AtomicInteger(0);
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			final int id = w;
			workers[w] = new Thread(() -> {
				for (;;) {
					final int i = next.getAndIncrement();
					if (i >= runs) {
						return;
					}
					try {
						convert(file, "c" + id);
					} catch (final Throwable t) {
						failures.add(t);
					}
				}
			}, "concurrent-convert-" + w);
			workers[w].setDaemon(true);
			workers[w].start();
		}
		for (final Thread t : workers) {
			t.join(180_000L);
			assertFalse("並行変換が終わらない", t.isAlive());
		}
		if (!failures.isEmpty()) {
			final StringBuilder sb = new StringBuilder();
			sb.append(file).append(" を ").append(threads).append("スレッドで").append(runs).append("回変換して ")
					.append(failures.size()).append("件失敗。最初の例外:\n");
			final Throwable first = failures.get(0);
			for (Throwable c = first; c != null; c = c.getCause()) {
				sb.append("  ").append(c).append('\n');
				for (int i = 0; i < Math.min(c.getStackTrace().length, 8); ++i) {
					sb.append("      ").append(c.getStackTrace()[i]).append('\n');
				}
				if (c.getCause() == c) {
					break;
				}
			}
			fail(sb.toString());
		}
	}

	private static void convert(final File html, final String slot) throws Exception {
		final File pdf = new File("local/concurrent/" + slot + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				// 並行変換の警告をSystem.errへ流すと、16スレッド×数百回で
				// Gradleのメッセージ経路が詰まりワーカーが終了できなくなる
				// (2026-07-26に実際に踏んだ。エンジンではなくハーネスの問題)。
				// 捨てる先はNULL_DEVICEにする——ラムダの自前ハンドラだと
				// 変換が失敗した(2026-07-26)
				// 大量の警告をSystem.errへ流すとGradleのメッセージ経路が詰まり
				// ワーカーが終了できなくなる(2026-07-26に実際に踏んだ)
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(
						new java.io.PrintStream(java.io.OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, html, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
