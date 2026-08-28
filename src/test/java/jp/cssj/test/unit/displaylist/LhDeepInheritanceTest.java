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
 * lh単位の深い継承連鎖の停止性回帰です(2026-08-27、独立レビュー指摘)。
 *
 * <p>
 * 各層が{@code line-height:1lh}を持つ深い{@code display:contents}連鎖では、
 * lhの基準(継承line-height)の解決が素朴な親再帰だと祖先の数だけスタックを
 * 積み、{@code StackOverflowError}で変換ごと落ちる。
 * {@code LineHeight.inheritedLineHeight}はルート側から計算値を確定させて
 * 再帰深度を抑える——この契約を深さ4,000で固定する。
 * </p>
 */
public class LhDeepInheritanceTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
