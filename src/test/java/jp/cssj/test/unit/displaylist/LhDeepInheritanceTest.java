package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
 * Termination regression for deep inheritance chains of lh units (2026-08-27, independent review).
 *
 * <p>
 * In a deep {@code display:contents} chain with {@code line-height:1lh} at every level,
 * naive parent recursion to resolve the lh reference (inherited line-height) adds one stack frame
 * per ancestor and crashes the entire conversion with {@code StackOverflowError}.
 * {@code LineHeight.inheritedLineHeight} finalizes computed values from the root downward
 * to bound recursion depth; verify this contract at depth 4,000.
 * </p>
 */
public class LhDeepInheritanceTest extends TestCase {
	public void testDeepContentsChainWithLhLineHeight() throws Exception {
		final int depth = 4000;
		final StringBuilder html = new StringBuilder(depth * 32 + 512);
		html.append("""
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8" />
				<style>.c { display: contents; line-height: 1lh; }</style>
				</head><body>
				""");
		for (int i = 0; i < depth; ++i) {
			html.append("<div class=\"c\">");
		}
		html.append("<span>x</span>");
		for (int i = 0; i < depth; ++i) {
			html.append("</div>");
		}
		html.append("</body></html>");

		final File pdf = new File("local/unittest/lh-deep-inheritance.pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeStream(session,
						new java.io.ByteArrayInputStream(html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
						URI.create("."), "text/html", "UTF-8");
			} finally {
				session.close();
			}
		}
		assertTrue("変換結果が出力されていません", pdf.length() > 0);
	}
}
