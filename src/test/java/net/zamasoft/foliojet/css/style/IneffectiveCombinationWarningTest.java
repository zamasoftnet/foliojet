package net.zamasoft.foliojet.css.style;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

/**
 * Verify that <b>ineffective combinations are not silently discarded</b>
 * (2823, 2026-08-29).
 *
 * <p>
 * Notification prompted by a user report (Japan Liberal Party Kawasaki): "Writing something that
 * silently has no effect wastes the most time." Floating {@code display:flex}/{@code display:grid}
 * containers are outside the permanent subset and fall back to normal blocks.
 * Previously, their items simply stacked vertically with no notification.
 * Absolutely positioned containers became supported on 2026-09-02 (E-3), so no longer report them
 * ({@code AbsoluteGridTest} fixes their behavior).
 * </p>
 */
public class IneffectiveCombinationWarningTest extends TestCase {
	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:200pt 200pt;margin:10pt}body{margin:0}</style></head><body>";

	/** Absolutely positioned flex containers now work (E-3), so report nothing. */
	public void testAbsoluteFlexIsSilent() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"position:absolute;left:0;right:0;display:flex;justify-content:space-between\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		assertEquals("an absolutely positioned flex container works since 2026-09-02, so it must not warn: "
				+ describe(messages), 0, select(messages).size());
	}

	/** The same applies to floating grid containers. */
	public void testFloatGridIsReported() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"float:left;display:grid;grid-template-columns:1fr 1fr\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		final List<String[]> reported = select(messages);
		assertEquals("float grid must be reported once: " + describe(messages), 1, reported.size());
		assertEquals("display: grid", reported.get(0)[1]);
	}

	/** Normal-flow flex works, so report nothing: do not cry wolf. */
	public void testFlowFlexIsSilent() throws Exception {
		final List<String[]> messages = convert(HEAD
				+ "<div style=\"display:flex;justify-content:space-between\">"
				+ "<span>L</span><span>R</span></div></body></html>");
		assertEquals("a flex container in normal flow works, so it must not warn: " + describe(messages), 0,
				select(messages).size());
	}

	/** Report repeated instances of the same declaration only once: avoid flooding users with warnings. */
	public void testReportedOnlyOnce() throws Exception {
		final StringBuilder html = new StringBuilder(HEAD);
		for (int i = 0; i < 5; ++i) {
			html.append("<div style=\"float:left;clear:left;display:flex\"><span>A</span><span>B</span></div>");
		}
		final List<String[]> messages = convert(html.append("</body></html>").toString());
		assertEquals("five identical fallbacks are one message: " + describe(messages), 1, select(messages).size());
	}

	private static List<String[]> select(final List<String[]> messages) {
		final String code = Integer.toString(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION & 0xFFFF);
		return messages.stream().filter(m -> m[0].equals(code)).toList();
	}

	private static List<String[]> convert(final String html) throws Exception {
		final List<String[]> messages = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setMessageHandler((code, args, mes) -> {
				final String[] m = new String[(args == null ? 0 : args.length) + 1];
				m[0] = Integer.toString(code & 0xFFFF);
				if (args != null) {
					System.arraycopy(args, 0, m, 1, args.length);
				}
				messages.add(m);
			});
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/3080-MODERN-CSS/clip-path-circle.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return messages;
	}

	private static String describe(final List<String[]> messages) {
		final StringBuilder s = new StringBuilder();
		for (final String[] m : messages) {
			s.append(String.join(" / ", m)).append('\n');
		}
		return s.toString();
	}
}
