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

/**
 * {@code text-emphasis} gives one mark to each character, not to each UTF-16 unit. A character outside the Basic
 * Multilingual Plane (here U+20B9F, a kanji in everyday Japanese use) was cut into its two surrogates, each in a box
 * of its own with a mark of its own: the character was lost and two marks were drawn. A mark given as a string was
 * cut the same way after its first unit.
 *
 * <p>
 * The texts are read from the display list. With a lone surrogate in it the dump of the page cannot be written as
 * UTF-8 at all, so a document that still cuts a character has no page here.
 * </p>
 */
public class TextEmphasisCodePointTest extends TestCase {
	/** U+20B9F, which is two UTF-16 units. */
	private static final String KANJI = new String(Character.toChars(0x20B9F));

	/** The sesame mark, U+FE45. */
	private static final String SESAME = String.valueOf((char) 0xFE45);

	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** The reported document: one character outside the BMP, one mark. */
	public void testSupplementaryCharacterGetsOneMark() throws Exception {
		final List<String> texts = texts(".em { text-emphasis: filled sesame }", "<p class=\"em\">" + KANJI + "</p>");
		assertEquals("the character, whole and once, in " + texts, 1, count(texts, KANJI));
		assertEquals("marks in " + texts, 1, count(texts, SESAME));
	}

	/** Between characters of the BMP: three characters, three marks, each character whole. */
	public void testSupplementaryCharacterAmongOthers() throws Exception {
		final List<String> texts = texts(".em { text-emphasis: filled sesame }",
				"<p class=\"em\">A" + KANJI + "B</p>");
		assertEquals("A in " + texts, 1, count(texts, "A"));
		assertEquals("the character, whole and once, in " + texts, 1, count(texts, KANJI));
		assertEquals("B in " + texts, 1, count(texts, "B"));
		assertEquals("marks in " + texts, 3, count(texts, SESAME));
	}

	/** A mark outside the BMP given as a string is drawn whole. */
	public void testSupplementaryCharacterAsMark() throws Exception {
		final List<String> texts = texts(".em { text-emphasis-style: \"" + KANJI + "\" }", "<p class=\"em\">A</p>");
		assertEquals("A in " + texts, 1, count(texts, "A"));
		assertEquals("the mark, whole and once, in " + texts, 1, count(texts, KANJI));
	}

	private static int count(final List<String> texts, final String text) {
		int count = 0;
		for (final String s : texts) {
			if (s.equals(text)) {
				++count;
			}
		}
		return count;
	}

	/** Converts the document and returns the texts of its first page, in the order of the display list. */
	private static List<String> texts(final String style, final String body) throws Exception {
		final File dir = Files.createTempDirectory("text-emphasis-code-point").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 400pt; margin: 0 } body { margin: 0; font: 24pt/48pt serif } p { margin: 0 } "
				+ style + "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File page = new File(dir, "page-0001.txt");
		assertTrue("no display list of the first page: a character was cut into lone surrogates", page.exists());
		final List<String> texts = new ArrayList<>();
		final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		while (m.find()) {
			texts.add(m.group(1));
		}
		return texts;
	}
}
