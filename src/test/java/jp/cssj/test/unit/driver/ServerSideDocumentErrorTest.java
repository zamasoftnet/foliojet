package jp.cssj.test.unit.driver;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.progress.ProgressListener;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Verify that <b>failure to obtain the main document on the server aborts with a message</b>
 * (2026-09-14, TECH-20260911-008).
 *
 * <p>
 * Previously, a {@code SecurityException} rejecting a server-internal destination for a remote user,
 * or an {@code IOException} such as connection refusal, escaped directly from
 * {@code DirectSession.transcode(URI)}, disconnecting the CTIP server.
 * The user received only "EOF within CTIP response".
 * </p>
 */
public class ServerSideDocumentErrorTest extends TestCase {
	/** A user without local-network access requests a loopback destination → 3810 (not permitted). */
	public void testForbiddenLocalNetworkIsReportedAsMessage() throws Exception {
		final TranscoderException e = transcode(false);
		assertEquals(MessageCodes.ERROR_FORBIDDEN_SERVERSIDE_DOCUMENT, e.getCode());
		assertEquals(TranscoderException.STATE_BROKEN, e.getState());
		assertEquals("http://127.0.0.1:9/", e.getArgs()[0]);
	}

	/** An authorized user gets connection refusal (port 9 is discard, assumed closed) → 3811 (cannot fetch). */
	public void testConnectionRefusedIsReportedAsMessage() throws Exception {
		final TranscoderException e = transcode(true);
		assertEquals(MessageCodes.ERROR_UNREACHABLE_SERVERSIDE_DOCUMENT, e.getCode());
		assertEquals(TranscoderException.STATE_BROKEN, e.getState());
		assertEquals(2, e.getArgs().length);
	}

	private static TranscoderException transcode(final boolean localAccess) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setLocalAccessAllowed(localAccess);
			// Receive progress as the CTIP server does (the main document is opened on this path).
			session.setProgressListener(new ProgressListener() {
				public void sourceLength(long length) {
				}

				public void progress(long read) {
				}
			});
			session.transcode(URI.create("http://127.0.0.1:9/"));
			fail("中断されませんでした");
			return null;
		} catch (TranscoderException e) {
			return e;
		} finally {
			session.close();
		}
	}
}
