package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * <b>開いたままの箱を救済分割しない</b>ことの回帰テストです(2026-09-16、seed 2003409 の縮小形)。
 *
 * <p>
 * `page-break-inside: avoid` の中に直交フローの部分木と表が同居し、版面が小さいと、
 * {@code FlowCutter.MoveResolution.RelaxInside} が「末尾のモノリシックな箱を幾何分割する」
 * つもりで<b>開き鎖のメンバー</b>({@code div[page-break-inside:avoid]})を救済分割していた。
 * 救済残余は再開時に {@code addRescueBound} で<b>閉じた箱</b>として戻るので
 * {@code startFlowBlock} が呼ばれず、{@code flowStack} が積み直されない。一方で継続は
 * その段がまだ開いていると記述しているため、
 * {@code RootBuilder.pageBreak} の不変条件「flowStack深さ≠継続深さ」で変換が失敗していた。
 * </p>
 *
 * <p>
 * これは掃過で最多の欠陥(seed 2,000,000〜5,249,999 の 640 万文書で STRICT 2,459 件・
 * WILD 1,570 件)で、生成器 v2 の当初から到達可能な既存欠陥だった
 * ({@code RandomDocumentFuzzTest} の javadoc に 2026-07-26 から記録がある)。
 * 直し方は「開き鎖のメンバーは救済分割せず、境界の avoid を緩和して内側で切る
 * (=継続フレームを作る)」。<b>avoid はこの箱では定義上履行できない</b>——破断点は
 * 既にその内側にあるため。
 * </p>
 */
public class RescueSplitOpenChainMemberTest extends TestCase {
	public RescueSplitOpenChainMemberTest(final String name) {
		super(name);
	}

	/** STRICT の全不変条件(内容保存・紙面内・読み順)を通る。 */
	public void testOpenChainMemberIsNotRescueSplit() throws Exception {
		final File fixture = new File("files/fuzz-repro/rescue-split-open-chain-member.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/rescue-split-open-chain-member.html");
		final File displayList = new File("build/fuzz-regressions/rescue-split-open-chain-member-dl");
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true,
				"rescue-split-open-chain-member-regression");
	}
}
