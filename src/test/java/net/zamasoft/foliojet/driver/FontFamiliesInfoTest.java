package net.zamasoft.foliojet.driver;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;

/**
 * <b>フォント一覧の軽量版</b>(B-4、2026-08-29)。
 *
 * <p>
 * 利用者報告(日本自由党川崎)より。{@code ctip/fonts}は1書体ごとに別名まで
 * 並べるので、書体選択のUIには重すぎる。{@code ctip/fonts/families}は
 * 利用者が{@code font-family}へ書ける名前ごとに1件へ畳む。
 * </p>
 */
public class FontFamiliesInfoTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static String info(final DirectSession session, final String uri) throws Exception {
		try (InputStream in = session.getServerInfo(URI.create(uri))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** ファミリ単位に畳まれ、素の一覧より小さいこと。 */
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
		// 同じ名前が2件出ない(畳めていない兆候)
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<family name=\"([^\"]+)\"")
				.matcher(families);
		final java.util.Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		while (m.find()) {
			assertTrue("同じ名前が2件出ています: " + m.group(1), seen.add(m.group(1)));
		}
		assertFalse("名前が1つも取れていません", seen.isEmpty());
	}

	/**
	 * 面ごとの名前ではなく<b>別名(ファミリ名)</b>で畳むこと。
	 * コア14の斜体・太字はすべて "Courier"・"Helvetica"・"Times" 等へ集まる。
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

	/** CSSへ書けない名前(版数の文字列など)は軽量版から落とすこと。 */
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
