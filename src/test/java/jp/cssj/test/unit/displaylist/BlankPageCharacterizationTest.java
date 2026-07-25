package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

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
 * 白紙ページの特性テストです(2026-07-25新設、ユーザー明示の絶対要件
 * 「意図せず白紙ページができないこと」の安全網)。
 *
 * <p>
 * <b>切り分け</b>: 作者が意図した白紙(非表示オブジェクト・強制改ページ・
 * 空の{@code @page}等)は正常。<b>エンジンの都合で生じる白紙は不具合</b>。
 * 自動判別はできないため、<b>「白紙ページを持つ文書とその枚数」を特性値
 * として固定</b>し、増減したらテストが落ちる形にする——増えたなら
 * エンジンが余分な白紙を作った疑い、減ったなら意図した白紙が消えた疑いで、
 * どちらも人間の確認を要求する。
 * </p>
 *
 * <p>
 * 判定は表示リストが空(描画命令ゼロ)であること。ページ背景・枠のみの
 * ページは「描画あり」として扱う——CSSで背景を指定した空ページは
 * 作者の意図とみなせるため。
 * </p>
 *
 * <p>
 * 期待値の更新は<b>差分を目視確認してから</b>行うこと。安易な再基準化は
 * この安全網を無意味にする。
 * </p>
 */
public class BlankPageCharacterizationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 特性値の正本(文書ごとの白紙ページ番号)。 */
	private static final Path EXPECTED = Path.of("files/unittest/blank-page-characterization.txt");

	/** 変換が例外で終わった文書の特性値。 */
	private static final String ERROR = "!conversion-failed";

	/** 1ページも生成されなかった文書の特性値。 */
	private static final String NO_PAGES = "!no-pages";

	public BlankPageCharacterizationTest(String name) {
		super(name);
	}

	public void testBlankPagesAreCharacterized() throws Exception {
		final List<Path> docs = new ArrayList<>();
		try (var s = Files.walk(Path.of("files/unittest"))) {
			s.filter(p -> {
				final String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".html") || n.endsWith(".xhtml");
			}).sorted().forEach(docs::add);
		}

		final TreeMap<String, String> actual = new TreeMap<>();
		for (final Path doc : docs) {
			final String blanks = this.blankPagesOf(doc);
			if (!blanks.isEmpty()) {
				actual.put(Path.of("files/unittest").relativize(doc).toString().replace('\\', '/'), blanks);
			}
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("# 白紙ページ(表示リストが空)の特性値。\n");
		sb.append("# 増えた=エンジンが余分な白紙を作った疑い、減った=意図した白紙が消えた疑い。\n");
		sb.append("# " + ERROR + "=変換が例外で終わる文書、" + NO_PAGES + "=1ページも出ない文書。\n");
		sb.append("# 更新は差分を目視確認してから行うこと。\n");
		for (final var e : actual.entrySet()) {
			sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
		}
		final String actualText = sb.toString();

		if (!Files.exists(EXPECTED)) {
			Files.writeString(EXPECTED, actualText, StandardCharsets.UTF_8);
			fail("特性値を生成しました。内容を確認してコミットしてください: " + EXPECTED);
		}
		final String expectedText = Files.readString(EXPECTED, StandardCharsets.UTF_8);
		if (!expectedText.equals(actualText)) {
			final Path actualPath = Path.of("local/blank-page-characterization-actual.txt");
			actualPath.getParent().toFile().mkdirs();
			Files.writeString(actualPath, actualText, StandardCharsets.UTF_8);
			fail("白紙ページの構成が変わりました。差分を目視確認してから期待値を更新してください。\n" + "expected=" + EXPECTED
					+ "\nactual=" + actualPath);
		}
	}

	/**
	 * 文書を変換し、表示リストが空のページ番号をカンマ区切りで返します
	 * (空ページなしなら空文字列)。
	 *
	 * <p>
	 * 変換に失敗した文書・1ページも生成されなかった文書は、空文字列では
	 * なく{@link #ERROR}/{@link #NO_PAGES}を返して<b>特性値に載せます</b>
	 * (2026-07-25、独立レビュー指摘)。従来はどちらも空文字列を返しており、
	 * 「変換が壊れた文書ほど白紙ゼロとして緑になる」という、この安全網の
	 * 目的と正反対の偽陰性になっていた。特性値へ載せておけば、新しく
     * 落ちるようになった文書も、落ちなくなった文書も差分として現れる。
	 * </p>
	 */
	private String blankPagesOf(final Path doc) {
		// 同名ファイルが別階層にあるため、相対パス全体をディレクトリ名にする
		// (ファイル名だけだと衝突して別文書の結果を読んでしまう)
		final String key = Path.of("files/unittest").relativize(doc).toString().replaceAll("[^A-Za-z0-9._-]", "_");
		final File outDir = new File("local/unittest/blank-page/" + key);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File("local/unittest/blank-page/out.pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, doc.toFile(), "text/html", null);
				} finally {
					session.close();
				}
			}
		} catch (final Exception | AssertionError e) {
			return ERROR;
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (pages == null || pages.length == 0) {
			return NO_PAGES;
		}
		java.util.Arrays.sort(pages);
		final StringBuilder blanks = new StringBuilder();
		for (final File page : pages) {
			final String text;
			try {
				text = Files.readString(page.toPath(), StandardCharsets.UTF_8);
			} catch (final Exception e) {
				continue;
			}
			// 表示リストは "drawer z=..." の行だけなら描画命令ゼロ
			boolean hasContent = false;
			for (final String line : text.split("\n")) {
				final String t = line.trim();
				if (t.isEmpty() || t.startsWith("drawer")) {
					continue;
				}
				hasContent = true;
				break;
			}
			if (!hasContent) {
				if (blanks.length() > 0) {
					blanks.append(',');
				}
				blanks.append(page.getName().replaceAll("\\D+", ""));
			}
		}
		return blanks.toString();
	}
}
