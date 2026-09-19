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

	/**
	 * seed 8471349(2026-09-19、仕切り直した掃過の strict): 60pt の用紙(`vertical-lr`)でページ先頭側の
	 * 浮動体(grid、min-width 104pt)が分割不能ではみ出したまま置かれ、同じページの末尾側の浮動体には
	 * 毎ページ 1pt しか残らないので、207pt の浮動体が 1pt ずつ 207 ページに分割された(有限だが 301 ページ)。
	 * 循環の正体は avoid の押し戻し: 浮動体の後ろの ul(UA 既定 {@code page-break-before:avoid})が次ページへ
	 * 送られるとき、直前の(内容が空の枠付き)div と一緒に押し戻され、div の枠の中(1pt)に切断線が引かれて
	 * 浮動体もそこで切られていた。押し戻すとページに本文が一つも残らない(連鎖の先頭がページ先頭で、
	 * 切断線より前は枠だけ)ときは、それより前に改ページできる位置が無いので avoid を無視して ul だけを送る
	 * ({@code FlowContainer.hasInFlowContentBefore})。8 ページになり、語は全部残る。
	 */
	public void testFloatSplitIntoSliversStops() throws Exception {
		check("float-sliver-progress");
		final File[] pages = new File("build/fuzz-regressions/float-sliver-progress-dl")
				.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		assertTrue("ページ数 " + pages.length + " (薄片の分割が止まっていない)", pages.length <= 20);
		// WILD の検査は語を見ないので、6 語が(救済スライスの artifact を除いて)1 回ずつ描かれることを固定する
		final java.util.Map<String, Integer> draws = new java.util.TreeMap<>();
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
		assertEquals(draws.toString(), java.util.Set.of("T7", "T9", "T13", "T17", "T20", "T25"), draws.keySet());
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
