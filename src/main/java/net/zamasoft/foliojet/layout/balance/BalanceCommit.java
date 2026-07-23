package net.zamasoft.foliojet.layout.balance;

import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * M6cバランスプローブのwinnerをownerへ接続するためのcommit切符です
 * (2026-07-24新設、排除域P2のM6c-4——codex設計§1.2/1.7)。
 *
 * <p>
 * {@code PreparedColumnCut}と同様、ownerの旧container identityを保持し、
 * commit直前に一致を確認する({@code MulticolumnBlockBox.commitBalance})。
 * 複雑な世代番号は持たない——「同じbuilderで他操作を挟まずcommitする」
 * 短命の契約である。commitは一回だけ({@link BalanceCandidate#takeContainer()}
 * の一回制で保証)。
 * </p>
 *
 * @param expectedOwnerContainer プローブ開始時のownerのcontainer
 *                               (commit直前のidentity検証用)
 * @param winner                 最小の成功候補
 */
public record BalanceCommit(Container expectedOwnerContainer, BalanceCandidate winner) {
}
