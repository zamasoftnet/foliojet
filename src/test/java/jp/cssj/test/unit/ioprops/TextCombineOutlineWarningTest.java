package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 縦中横の字面計測ではCID-keyedフォントの輪郭欠落を警告しません。 */
public class TextCombineOutlineWarningTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testCidKeyedTextCombineDoesNotWarnAboutBackgroundClip() throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:100pt 100pt;margin:10pt}body{margin:0;writing-mode:vertical-rl}"
				+ "span{text-combine-upright:all}</style></head><body><span>12</span></body></html>";
		final List<Short> messages = new ArrayList<>();
		final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setMessageHandler((code, args, mes) -> messages.add(code));
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///text-combine-outline-warning.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertFalse("PDFが生成されていません", pdf.size() == 0);
		assertFalse("縦中横の計測だけで2820が出ています: " + messages,
				messages.contains(MessageCodes.WARN_MISSING_FONT_OUTLINE));
	}
}
