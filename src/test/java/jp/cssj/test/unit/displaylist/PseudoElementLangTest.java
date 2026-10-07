package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Verify that pseudo-element text uses the element's language (2026-10-07).
 * {@code ::before}/{@code ::after} used shared CSSElement instances without language, falling back
 * to defaults for language-specific generic-family chains, kinsoku (line-breaking rules), and hyphenation.
 * Check through language-dependent hyphenation (only the lang=en {@code content} word failed to break
 * and overflowed the type area).
 */
public class PseudoElementLangTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	public PseudoElementLangTest(final String name) {
		super(name);
	}

	public void testPseudoElementIsHyphenatedInTheElementLanguage() throws Exception {
		final String dump = convert("""
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="en"><head><meta charset="UTF-8"/>
				<style>@page{size:60mm 60mm;margin:5mm} p{margin:0;width:22mm;hyphens:auto;font-size:10pt;font-family:serif}
				span::before{content:"internationalization "}</style></head>
				<body><p><span>internationalization</span></p></body></html>
				""");
		final List<String> texts = new ArrayList<>();
		final Matcher m = TEXT.matcher(dump);
		while (m.find()) {
			texts.add(m.group(1));
		}
		// Pseudo-element words hyphenate like the element's words (otherwise they remain a single word).
		assertFalse(texts.toString(), texts.contains("internationalization"));
		assertEquals(texts.toString(), 2, texts.stream().filter(t -> t.endsWith("-")).count());
	}

	/** Convert and return concatenated display lists for all pages. */
	private static String convert(final String html) throws Exception {
		final File dir = new File("local/pseudo-element-lang/" + Integer.toHexString(html.hashCode()));
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
				CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
			} finally {
				session.close();
			}
		}
		final StringBuilder all = new StringBuilder();
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない", pages);
		java.util.Arrays.sort(pages);
		for (final File page : pages) {
			all.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return all.toString();
	}
}
