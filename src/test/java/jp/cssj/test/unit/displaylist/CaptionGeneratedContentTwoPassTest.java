package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * When elements inside a table caption had generated content (::before/::after content),
 * pending glyphs arrived after the run closed on the TwoPass glyph path, stopping conversion with an NPE
 * (cti.li report repro-7, 2026-09-05).
 * The fixture is the report's minimal example (a table between body paragraphs).
 */
public class CaptionGeneratedContentTwoPassTest extends TestCase {
	public CaptionGeneratedContentTwoPassTest(final String name) {
		super(name);
	}

	public void testCaptionWithGeneratedContentConverts() throws Exception {
		final File fixture = new File("files/unittest/1000-TABLE/caption-generated-content.xhtml");
		final File dir = new File("local/unittest/caption-generated-content");
		dir.mkdirs();
		final File[] old = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (old != null) {
			for (final File file : old) {
				file.delete();
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeFile(session, fixture, "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		assertTrue("PDF が出ていない", out.size() > 1000);
		final StringBuilder all = new StringBuilder();
		for (final File page : dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"))) {
			all.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		assertTrue("caption の生成内容 [ が描かれていない:\n" + all, all.toString().contains("Text[\"["));
		assertTrue("caption の生成内容 ] が描かれていない:\n" + all, all.toString().contains("Text[\"]"));
		assertTrue("caption の番号 14 が描かれていない", all.toString().contains("14"));
	}
}
