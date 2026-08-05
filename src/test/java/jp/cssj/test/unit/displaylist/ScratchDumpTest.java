package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 使い捨ての表示リストダンプです(調査用。コミットしない)。
 *
 * <pre>./gradlew test --tests '*ScratchDumpTest' -Dfoliojet.scratchDoc=files/...html</pre>
 */
public class ScratchDumpTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testDump() throws Exception {
		String doc = System.getProperty("foliojet.scratchDoc");
		if (doc == null) {
			return;
		}
		for (String one : doc.split(",")) {
			dumpOne(one.trim());
		}
	}

	private void dumpOne(String doc) throws Exception {
		File outDir = new File("local/unittest/scratch");
		outDir.mkdirs();
		File[] old = outDir.listFiles();
		if (old != null) {
			for (File f : old) {
				f.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try (OutputStream out = new FileOutputStream(new File("local/unittest/scratch.pdf"))) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, new File(doc), "text/html", null);
			} finally {
				session.close();
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(pages);
		StringBuilder sb = new StringBuilder();
		for (File p : pages) {
			sb.append("===== ").append(p.getName()).append(String.valueOf((char) 10)).append(Files.readString(p.toPath()));
		}
		Files.writeString(new File("local/unittest/" + new File(doc).getName() + ".dump").toPath(), sb.toString());
	}
}
