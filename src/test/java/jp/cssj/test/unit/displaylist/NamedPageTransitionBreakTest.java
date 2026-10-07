package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** Combine an explicit page break and named-page transition into one break at the same boundary. */
public class NamedPageTransitionBreakTest extends TestCase {
	public void testPaintedNamedPagesDoNotExposeDuplicateBreak() throws Exception {
		final File dir = new File("local/unittest/named-page-transition-background");
		dir.mkdirs();
		final File[] old = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		if (old != null) {
			for (final File file : old) {
				file.delete();
			}
		}
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session,
					new File("files/unittest/0520-named-page/transition-no-double-break-background.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertEquals("背景付き名前ページ間に余分な白紙を作っています", 3, pages.length);
	}
}
