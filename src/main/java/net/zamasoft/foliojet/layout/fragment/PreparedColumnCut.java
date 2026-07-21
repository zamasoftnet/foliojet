package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * {@code AbstractContainerBox.prepareColumnCut()}が返す、まだownerへ
 * commitしていない切断結果です(2026-07-21新設、M6b Phase B B4)。
 *
 * <p>
 * ここでいう「prepared」は、完全に副作用のないdry-runという意味では
 * ない——{@code ownerContainer.splitPageAxis()}による元active columnの
 * 切断は既に行われている。正確な意味は「ownerへの新column追加と
 * builder resume開始をまだcommitしていない切断結果」である(ChatGPT Pro
 * 相談で確認、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
 * 参照)。PAGE経路と同様、split後の構造検証失敗は変換全体を中断すべきで
 * あり、旧経路へrollbackして再実行してはいけない。
 * </p>
 *
 * @param owner                    段組owner box(commit時にthisと同一か検証)
 * @param expectedOwnerContainer   prepare時点のowner.container(commit時の同一性検証用)
 * @param expectedActiveColumn     prepare時点のactive column
 * @param expectedActualColumnCount prepare時点の実体化済み段数
 * @param newPageExtent            commit時にownerへ設定する新しいpage軸寸法
 * @param ownerRemainder           owner直下の残余コンテナ(次columnへ運ぶ内容)
 * @param childFrame               貫通した場合の継続フレーム(貫通しなければnull)
 */
public record PreparedColumnCut(AbstractContainerBox owner, Container expectedOwnerContainer,
		Container expectedActiveColumn, int expectedActualColumnCount, double newPageExtent, Container ownerRemainder,
		Continuation.ContinuationFrame childFrame) {
}
