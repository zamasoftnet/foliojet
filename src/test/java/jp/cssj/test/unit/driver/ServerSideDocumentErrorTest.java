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
 * <b>サーバー側のメインドキュメントが取れないときはメッセージ付きの中断になる</b>ことを固定します
 * (2026-09-14、TECH-20260911-008)。
 *
 * <p>
 * 遠隔の利用者にサーバーの内側の宛先を拒む {@code SecurityException} と、接続拒否などの
 * {@code IOException} が、以前は {@code DirectSession.transcode(URI)} からそのまま抜けて
 * CTIP サーバーの接続が切れ、利用者には "EOF within CTIP response" しか届かなかった。
 * </p>
 */
public class ServerSideDocumentErrorTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** ローカル網を許していない利用者がループバック宛てを頼む → 3810(許可していない)。 */
	public void testForbiddenLocalNetworkIsReportedAsMessage() throws Exception {
		final TranscoderException e = transcode(false);
		assertEquals(MessageCodes.ERROR_FORBIDDEN_SERVERSIDE_DOCUMENT, e.getCode());
		assertEquals(TranscoderException.STATE_BROKEN, e.getState());
		assertEquals("http://127.0.0.1:9/", e.getArgs()[0]);
	}

	/** 許している利用者だが接続を拒まれる(ポート 9 は discard、閉じている前提)→ 3811(取得できない)。 */
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
			// CTIP サーバーと同じく進捗を受ける(主文書の open がこの経路で起きる)
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
