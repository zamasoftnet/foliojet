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

/** 縮小作業用: -Dfoliojet.convertOne=<path> の文書を1件変換する。 */
public class ConvertOneTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public ConvertOneTest(String name) {
		super(name);
	}

	public void testConvert() throws Exception {
		final String path = System.getProperty("foliojet.convertOne");
		if (path == null) {
			return;
		}
		final boolean checkBlank = System.getProperty("foliojet.convertOneBlank") != null;
		final File outDir = new File("local/convert-one-dl");
		outDir.mkdirs();
		for (final File f : outDir.listFiles()) {
			f.delete();
		}
		if (checkBlank) {
			System.setProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		}
		final File pdf = new File("local/convert-one.pdf");
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver()
					.getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, new File(path), "text/html", null);
			} finally {
				session.close();
			}
		} finally {
			System.clearProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY);
		}
		if (!checkBlank) {
			return;
		}
		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(pages);
		java.util.Arrays.sort(pages);
		final java.util.List<Integer> blanks = new java.util.ArrayList<>();
		for (int i = 0; i < pages.length; ++i) {
			boolean drew = false;
			for (final String line : java.nio.file.Files.readAllLines(pages[i].toPath())) {
				final String t = line.trim();
				if (!t.isEmpty() && !t.startsWith("drawer")) {
					drew = true;
				}
			}
			if (!drew) {
				blanks.add(i + 1);
			}
		}
		assertTrue("白紙ページ " + blanks, blanks.isEmpty());
	}
}
