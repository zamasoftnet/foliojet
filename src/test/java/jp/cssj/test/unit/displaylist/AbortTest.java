package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies that <b>conversion can be stopped externally</b> (added on 2026-07-27).
 *
 * <p>
 * <b>Why this is needed.</b> {@code abort()} <b>only sets a flag</b>.
 * Nothing happens unless the engine reads it
 * ({@link net.zamasoft.foliojet.ua.UserAgent#checkAbort}).
 * Previously, reads occurred only at page boundaries,
 * so <b>a document that never finished processing one page could never be stopped</b>.
 * </p>
 *
 * <p>
 * This was discovered through measurement: a 100,000-document sweep stalled for seven hours,
 * and thread dumps showed conversion threads surviving the watchdog piling up (2026-07-27).
 * For server use, this meant one runaway conversion could take down the entire process.
 * </p>
 */
public class AbortTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public AbortTest(String name) {
		super(name);
	}

	/** How long the engine may go on after the abort reached it before it reads the abort. */
	private static final long READ_WITHIN_NANOS = 2_000_000_000L;

	/**
	 * An abort request must end conversion <b>without waiting for a page boundary</b>.
	 *
	 * <p>
	 * Place a long table on a tiny page. It keeps laying out rows within one page,
	 * so abort points only at page boundaries cannot stop it.
	 * </p>
	 *
	 * <p>
	 * Judged by how long the engine goes on after the abort reached it before an abort point reads it, and by where
	 * (2026-10-31). Judged by a 30 s wait, the test passed whenever the conversion got to an abort point in time: an
	 * auto-layout table was read and measured with none (RetainedTableBuilder), so it was green on an idle machine and
	 * red only under load. Not judged by when the conversion ends: releasing what the table held takes 0.5 s more for
	 * 200,000 rows.
	 * </p>
	 */
	public void testAbortStopsConversionMidPage() throws Exception {
		final File html = writeLongDocument();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		final Throwable[] failure = new Throwable[1];
		// When the abort reached the engine, when an abort point read it, and when the conversion ended. The first
		// INFO_ABORT comes from abort() before it sets the flag, the next from the abort point that throws
		final AtomicLong acceptedAt = new AtomicLong(), readAt = new AtomicLong(), endedAt = new AtomicLong();
		final AtomicBoolean readInTable = new AtomicBoolean();

		final Thread worker = new Thread(() -> {
			try (OutputStream out = new FileOutputStream(new File("local/abort-test.pdf"))) {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler((code, args, mes) -> {
					if (code == CTIMessageCodes.INFO_ABORT && !acceptedAt.compareAndSet(0L, System.nanoTime())
							&& readAt.compareAndSet(0L, System.nanoTime())) {
						readInTable.set(in(Thread.currentThread().getStackTrace(), "RetainedTableBuilder"));
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, html, "text/html", null);
			} catch (final Throwable t) {
				// Abort is propagated as an exception. Only completion matters here.
				failure[0] = t;
			} finally {
				endedAt.set(System.nanoTime());
			}
		}, "abort-test-conversion");
		worker.setDaemon(true);
		worker.start();

		// Abort once the table is being read, its cells built by TwoPassBlockBuilder (2026-10-31, codex). After a fixed
		// wait the request could come before the session gave the user agent its message handler, losing the first
		// notification, or before the table, where a block's abort point read it and the test passed without the table's.
		// Past the limit, keep waiting to report how long it really took, and show where the conversion runs without
		// reading the abort flag: on the layout thread, which the worker only joins (DirectSession.runOnLargeStack)
		final long deadline = System.nanoTime() + 60_000_000_000L;
		while (worker.isAlive() && System.nanoTime() < deadline
				&& !in(layoutThreadStack(), "RetainedTableBuilder", "TwoPassBlockBuilder")) {
			Thread.sleep(10L);
		}
		String where = "";
		while (worker.isAlive() && System.nanoTime() < deadline) {
			session.abort(CTISession.ABORT_FORCE);
			worker.join(200L);
			final long accepted = acceptedAt.get();
			if (where.isEmpty() && accepted != 0L && readAt.get() == 0L
					&& System.nanoTime() - accepted > READ_WITHIN_NANOS) {
				where = "\n" + Arrays.stream(layoutThreadStack()).map(f -> "\tat " + f).collect(Collectors.joining("\n"));
			}
		}
		final long accepted = acceptedAt.get(), read = readAt.get();
		if (accepted == 0L) {
			fail(worker.isAlive() ? "60 秒たっても中断要求が組版に届かない" : "中断要求が組版に届かないまま変換が終わった");
		}
		final long readMillis = ((read != 0L ? read : System.nanoTime()) - accepted) / 1_000_000L;
		System.out.println("[abort] " + (read != 0L ? "read " : "not read in ") + readMillis
				+ " ms after the abort reached the engine" + (readInTable.get() ? " in the table" : "")
				+ (worker.isAlive() ? ", still running" : ", ended after " + (endedAt.get() - accepted) / 1_000_000L + " ms"));
		if (read == 0L || readMillis * 1_000_000L > READ_WITHIN_NANOS) {
			fail("中断要求が届いてから " + readMillis + " ms 読まれなかった" + (read == 0L ? "(まだ読まれていない)" : "")
					+ "。checkAbort() を呼ぶ場所が足りていない可能性がある" + where);
		}
		assertTrue("表を読んでいる途中の中断を表の外で読んだ(行ごとの確かめを通っていない)", readInTable.get());
		assertFalse("中断を読んだのに 60 秒たっても変換が終わらない", worker.isAlive());
		assertNotNull("変換が中断されずに最後まで組まれた", failure[0]);
	}

	/** The layout thread's stack, empty when there is none. */
	private static StackTraceElement[] layoutThreadStack() {
		for (final var e : Thread.getAllStackTraces().entrySet()) {
			if (e.getKey().getName().equals("foliojet-layout")) return e.getValue();
		}
		return new StackTraceElement[0];
	}

	private static boolean in(final StackTraceElement[] stack, final String... simpleNames) {
		for (final StackTraceElement frame : stack) {
			final String name = frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1);
			if (Arrays.asList(simpleNames).contains(name)) return true;
		}
		return false;
	}

	/**
	 * A long table on a tiny page. Row placement continues for a long time within one page. 200,000 rows (2026-10-31), so
	 * that reading the table outlasts the limit even on an idle machine: with 40,000 rows an abort waited for it only 1 s.
	 */
	private static File writeLongDocument() throws Exception {
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<style>@page{margin:5pt}body{margin:0;font:normal 8pt/1.2 serif}\n");
		s.append("td{border:1pt solid black}</style></head><body>\n<table>\n");
		for (int i = 0; i < 200000; ++i) {
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
	 * <b>Do not report abort as an I/O error</b>
	 * (2026-09-21, discovered while expanding the driver matrix).
	 *
	 * <p>
	 * If {@code abort()} arrives while the body is being sent, the body input (and read-ahead buffer)
	 * is closed. The parser sees this as an ordinary {@link java.io.IOException}, so it was previously
	 * wrapped and reported as {@code ERROR_IO}. On the actual server, the client received an abort as
	 * "I/O error. I/O error. prefetch read-ahead terminated"; wrapping the typed
	 * {@code TranscoderException} again duplicated the prefix.
	 * </p>
	 *
	 * <p>
	 * Verifies two requirements: the reported code is {@code INFO_ABORT} (abort),
	 * and the body contains neither a duplicated prefix nor internal read-ahead terminology.
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
		// Use the same path as the actual server (read the body through a read-ahead buffer).
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
			// close performs cleanup after abort. Inspect the body-side report here.
		}

		// Also save observations to a file so they remain readable when Gradle output is restricted.
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
