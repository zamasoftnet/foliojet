package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * A closed details element shows only its summary whatever display the author gives its content, important or not;
 * the open attribute opens it (2026-10-09). Expectations measured in Chrome: S1 S2 S3 OPENSHOWN S4.
 *
 * <p>
 * html-ua.css hides the content with an important UA declaration, and the cascade lets the UA sheet's important
 * declarations outrank the author's. Text directly inside a closed details element (not wrapped in an element)
 * is still shown; Chrome hides it too (recorded as a separate difference).
 * </p>
 */
public class DetailsClosedTest extends TestCase {
	public void testAuthorDisplayDoesNotOpen() throws Exception {
		final String page = convert("""
				<!DOCTYPE html>
				<html><head><meta charset="utf-8"/><style>
				@page { size: 300pt 300pt; margin: 10pt }
				body { margin: 0; font: 10pt/15pt serif }
				.g > div { display: grid }
				.i > div { display: block !important }
				</style></head><body>
				<details class="g"><summary>S1</summary><div>GRIDHIDDEN</div></details>
				<details class="i"><summary>S2</summary><div>IMPHIDDEN</div></details>
				<details class="g" open="open"><summary>S3</summary><div>OPENSHOWN</div></details>
				<details><summary>S4</summary><p style="display: flex">FLEXHIDDEN</p></details>
				</body></html>
				""");
		final List<String> texts = new ArrayList<>();
		final Matcher m = Pattern.compile("Text\\[\"([^\"]*)\"").matcher(page);
		while (m.find()) {
			texts.add(m.group(1));
		}
		assertEquals("見出しと開いた中身だけ:\n" + page, List.of("S1", "S2", "S3", "OPENSHOWN", "S4"), texts);
	}

	private static String convert(final String html) throws Exception {
		final File dir = new File("local/details-closed/author-display");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
				AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} finally {
				session.close();
			}
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
