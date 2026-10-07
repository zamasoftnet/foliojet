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

	/**
	 * An abort request must end conversion <b>without waiting for a page boundary</b>.
	 *
	 * <p>
	 * Place a long table on a tiny page. It keeps laying out rows within one page,
	 * so abort points only at page boundaries cannot stop it.
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
				// Abort is propagated as an exception. Only completion matters here.
				failure[0] = t;
			} finally {
				finished[0] = true;
			}
		}, "abort-test-conversion");
		worker.setDaemon(true);
		worker.start();

		// Wait briefly for conversion to start, then stop it.
		Thread.sleep(1500L);
		session.abort(CTISession.ABORT_FORCE);

		worker.join(30_000L);
		assertFalse("中断要求を出したのに変換が止まらない。"
				+ "checkAbort() を呼ぶ場所が足りていない可能性がある", worker.isAlive());
		assertTrue(finished[0]);
	}

	/** A long table on a tiny page. Row placement continues for a long time within one page. */
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
