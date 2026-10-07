package net.zamasoft.foliojet.driver;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;

/**
 * <b>A lightweight font list</b> (B-4, 2026-08-29).
 *
 * <p>
 * Based on a user report (Japan Liberal Party Kawasaki). {@code ctip/fonts} lists each font face
 * with its aliases, making it too heavy for a font-selection UI.
 * {@code ctip/fonts/families} consolidates entries by the names users can put in {@code font-family}.
 * </p>
 */
public class FontFamiliesInfoTest extends TestCase {
	private static String info(final DirectSession session, final String uri) throws Exception {
		try (InputStream in = session.getServerInfo(URI.create(uri))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Entries are consolidated by family and the list is smaller than the raw list. */
	public void testFamiliesAreFoldedAndSmaller() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		final String families, fonts;
		try {
			families = info(session, "http://www.cssj.jp/ns/ctip/fonts/families");
			fonts = info(session, "http://www.cssj.jp/ns/ctip/fonts");
		} finally {
			session.close();
		}
		assertTrue("根要素がfont-familiesではありません: " + families.substring(0, Math.min(120, families.length())),
				families.contains("<font-families"));
		assertTrue("familyが1件も無い", families.contains("<family "));
		assertFalse("軽量版にaliasを並べてはならない", families.contains("<alias"));
		assertTrue("ウェイトの一覧が無い", families.contains("weights=\""));
		assertTrue("素の一覧より小さいこと: families=" + families.length() + " fonts=" + fonts.length(),
				families.length() < fonts.length());
		// No duplicate names (which would indicate failure to consolidate).
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<family name=\"([^\"]+)\"")
				.matcher(families);
		final java.util.Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		while (m.find()) {
			assertTrue("同じ名前が2件出ています: " + m.group(1), seen.add(m.group(1)));
		}
		assertFalse("名前が1つも取れていません", seen.isEmpty());
	}

	/**
	 * Consolidate by <b>alias (family name)</b>, not individual face name.
	 * Core 14 italic/bold faces all group under "Courier", "Helvetica", "Times", etc.
	 */
	public void testFoldedByAliasNotByFaceName() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		final String families;
		try {
			families = info(session, "http://www.cssj.jp/ns/ctip/fonts/families");
		} finally {
			session.close();
		}
		assertTrue("別名(ファミリ名)で畳めていません: " + families, families.contains("name=\"Courier\""));
		assertFalse("面ごとの名前がファミリとして並んでいます",
				families.contains("name=\"Courier-BoldOblique\""));
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("<family name=\"Courier\"[^/]*weights=\"([^\"]+)\"").matcher(families);
		assertTrue("Courierのファミリが見つかりません", m.find());
		assertTrue("太さが畳まれていません: " + m.group(1), m.group(1).contains(" "));
	}

	/** Exclude names unusable in CSS (such as version strings) from the lightweight list. */
	public void testUnwritableNamesAreDropped() throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		final String families;
		try {
			families = info(session, "http://www.cssj.jp/ns/ctip/fonts/families");
		} finally {
			session.close();
		}
		assertFalse("font-familyへ書けない名前が残っています", families.contains("name=\"0.000;"));
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<family name=\"([^\"]*;[^\"]*)\"")
				.matcher(families);
		assertFalse("セミコロンを含む名前が残っています: " + (m.find() ? m.group(1) : ""), m.reset().find());
	}
}
