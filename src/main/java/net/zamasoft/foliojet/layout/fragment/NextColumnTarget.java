package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * COLUMN継続のowner(anchor)を識別する情報です(2026-07-21新設、
 * M6b Phase B B4、未配線・型のみ)。{@code commitPreparedColumn()}直前の
 * identity検証にのみ使う——commit後のexecutorはowner identityを再検査
 * しない(COLUMN resume中にPAGE breakが起き、B3aによりowner自体が新しい
 * page fragmentへ置き換わり得るため。ChatGPT Pro相談、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。
 *
 * @param owner                     段組owner box
 * @param expectedOwnerContainer    prepare時点のowner.containerの同一性検証用
 * @param expectedActualColumnCount prepare時点の実体化済み段数
 * @param configuredColumnCount     CSS指定の段数
 * @param resumePageAxis            再開時のpage軸位置
 * @param resumeLineAxis            再開時のline軸位置
 * @param ownerSignature            fragment identity照合用の署名
 */
public record NextColumnTarget(AbstractContainerBox owner, Container expectedOwnerContainer,
		int expectedActualColumnCount, int configuredColumnCount, double resumePageAxis, double resumeLineAxis,
		OpenPathSnapshot.FragmentSignature ownerSignature) {
}
