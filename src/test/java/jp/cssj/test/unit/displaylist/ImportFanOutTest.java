package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The number of style sheets a document imports with {@code @import} is limited in total. Only the depth (ten
 * levels) and loops through an ancestor were checked, and a sheet imported several times is read every time: eleven
 * sheets that each import the next one three times asked for 3<sup>10</sup> readings of the last, a few bytes of
 * style sheet for an endless conversion.
 */
public class ImportFanOutTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	public void testImportsAreLimited() throws Exception {
		final File dir = Files.createTempDirectory("import-fan-out").toFile();
		for (int i = 0; i < 10; ++i) {
			Files.writeString(new File(dir, "s" + i + ".css").toPath(),
					("@import \"s" + (i + 1) + ".css\";\n").repeat(3), StandardCharsets.UTF_8);
		}
		Files.writeString(new File(dir, "s10.css").toPath(), "p.h{display:none}\n", StandardCharsets.UTF_8);
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(),
				"<!DOCTYPE html><html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"s0.css\"></head>"
						+ "<body><p class=\"h\">HIDDEN</p><p>KEEP</p><p>AFTER</p></body></html>",
				StandardCharsets.UTF_8);

		final List<String> messages = new ArrayList<>();
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setMessageHandler((code, args, mes) -> messages.add(args == null ? "" : String.join(" ", args)));
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> texts = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				texts.add(m.group(1));
			}
		}
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
		assertFalse("the sheets imported before the limit apply: " + texts, texts.contains("HIDDEN"));
		int reports = 0;
		for (final String message : messages) {
			if (message.contains("@import ignored")) {
				++reports;
			}
		}
		assertEquals("the limit is reported, once: " + messages, 1, reports);
	}
}
