package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * 浮動体まわりの<b>停止性と排除域の契約</b>の回帰です(2026-09-17、掃過 wild の縮小形)。
 *
 * <ul>
 * <li>{@code float-split-without-progress}(seed 2264275): ページ先頭の浮動体を分割しても、
 * 中身がページ軸に切れない(直交フローのセル+明示寸法)ため残余が次ページでも同じ
 * 63.25pt に組み直され、60pt の用紙で<b>白紙を出し続けた</b>。release の CLI では
 * 27,820 ページ出して OutOfMemoryError で落ちた(本番は上限が無い)。前回のページ先頭
 * 分割から寸法が縮んでいなければ分割不能として扱い、救済分割か「はみ出したまま置く」へ
 * 落とす。</li>
 * <li>{@code top-float-exclusion-after-limit-shrinks}(seed 2266831): 同じページ世代の
 * 中で脚注の予約が入り fragmentLimit が縮んだあと、高さ 0 の top 浮動体を積むと排除域の
 * 終端が始端より手前になり、{@code ExclusionSpace.copyOfSorted} の並び順の契約を破った。
 * 本番では assert が無効なので、崩れた並びのまま排除域が使われていた。</li>
 * </ul>
 */
public class PageFloatTerminationTest extends TestCase {
	public PageFloatTerminationTest(final String name) {
		super(name);
	}

	public void testFloatSplitWithoutProgressTerminates() throws Exception {
		check("float-split-without-progress");
	}

	public void testTopFloatExclusionStaysSorted() throws Exception {
		check("top-float-exclusion-after-limit-shrinks");
	}

	/**
	 * seed 3361501: ページフロートの排除で 1 行目が版面に入らず {@code requireTextBlock()} が改ページすると、
	 * 再開が直前の完結したテキストブロックを(深さ規約どおり)開いたまま戻す。後始末が無く
	 * 「ブロック境界でテキストビルダーが開いたまま」で変換が失敗していた。
	 */
	public void testFirstLineBreakClosesReopenedText() throws Exception {
		check("first-line-break-reopens-closed-text");
		// WILD の検査は「落ちない・止まる」までなので、この修正が語を二重に出していない
		// ことを表示リストで固定する(救済スライスの続きは artifact として描かれるので除く)
		final java.util.Map<String, Integer> draws = new java.util.TreeMap<>();
		final File[] pages = new File("build/fuzz-regressions/first-line-break-reopens-closed-text-dl")
				.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("表示リストが出ていない", pages.length > 0);
		final java.util.regex.Pattern token = java.util.regex.Pattern.compile("Text\\[\"(T\\d+)\"");
		for (final File page : pages) {
			for (final String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8)) {
				if (line.contains(" artifact ")) {
					continue;
				}
				final java.util.regex.Matcher m = token.matcher(line);
				while (m.find()) {
					draws.merge(m.group(1), 1, Integer::sum);
				}
			}
		}
		assertFalse("語が 1 つも描かれていない", draws.isEmpty());
		draws.forEach((k, v) -> assertEquals("語 " + k + " が複数回描かれた", 1, v.intValue()));
	}

	private static void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		// WILD の不変条件(落ちない・止まる・ページ数が過大でない)で検査する
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, false, name + "-regression");
	}
}
