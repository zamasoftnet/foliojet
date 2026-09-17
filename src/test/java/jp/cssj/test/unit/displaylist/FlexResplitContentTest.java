package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * <b>分割済みの flex/grid の頭がもう一度分割されても内容が複製されない</b>ことの回帰です
 * (2026-09-17、掃過 seed 2010872・2477193 の縮小形)。
 *
 * <p>
 * {@code FlexBox.split}/{@code GridBox.split} は分割後も頭側の行記録
 * ({@code lines}/{@code lineItems}、{@code rows}/{@code rowItems})を切り詰めず、
 * 次断片へ移送済みの行と item を指し続けていた。多段の均衡のように同じ頭がもう一度
 * 分割されると、古い記録から境界行を選び、既に移送した item をもう一度分割して
 * 残余を作る——同じ内容が 2 つの断片に入る。seed 2010872 では「T106」が同じ頁の
 * 2 つの段に描かれ(内容の複製)、seed 2477193 では後ろの語が前の頁に出た(読み順の逆転)。
 * </p>
 *
 * <p>
 * どちらの文書も成分を 1 つ外すと再現しない(15 変種で確認)ほど条件が狭いが、
 * 複製と読み順の崩れは絶対要件の違反なので固定する。
 * </p>
 */
public class FlexResplitContentTest extends TestCase {
	public FlexResplitContentTest(final String name) {
		super(name);
	}

	public void testResplitDoesNotDuplicateContent() throws Exception {
		check("flex-resplit-duplicates-content");
	}

	public void testResplitKeepsReadingOrder() throws Exception {
		check("flex-resplit-reorders-content");
	}

	private static void check(final String name) throws Exception {
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true, name + "-regression");
	}
}
