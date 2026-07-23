package net.zamasoft.foliojet.layout.balance;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;

/**
 * live owner(バランス対象の段組ボックス)の「解決済み物理形状」の
 * 不変スナップショットです(2026-07-24新設、排除域P2のM6c-2——
 * {@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §1.2/1.3)。
 *
 * <p>
 * M6cバランスプローブの各候補shellは、raw CSSから親レイアウトを再現する
 * のではなく、live構築時に既に解決済みの物理形状(行方向内寸・計算済み
 * マージン/パディング・{@code specifiedPageAxis}・解決済み整列)を
 * ここからコピーして作られる。ページ方向の寸法だけは候補ごとの試行容量で
 * 上書きされる({@link
 * net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox#newBalanceProbeShell})。
 * </p>
 *
 * <p>
 * {@code params}/{@code pos}/{@code size}/{@code minSize}/
 * {@code frameSpec}は記録後不変の共有参照(ARCHITECTURE §5.7 前提(ii)
 * ——ボックス再インスタンス化と同じ扱い)、{@code margin}/{@code padding}
 * はスナップショット時の値コピーである。
 * </p>
 *
 * @param params            計算済みブロックパラメータ(共有参照、record後不変)
 * @param pos               フロー配置(共有参照、record後不変)
 * @param size              指定寸法
 * @param minSize           最小寸法
 * @param frameSpec         枠の指定({@code AbsoluteRectFrame.frame})
 * @param margin            解決済み絶対マージン(値コピー)
 * @param padding           解決済み絶対パディング(値コピー)
 * @param width             解決済み幅(横書きなら行方向内寸)
 * @param height            解決済み高さ(縦書きなら行方向内寸)
 * @param minPageAxis       ページ方向の最小寸法
 * @param specifiedPageAxis ページ方向寸法が明示されているか
 * @param resolvedAlign     解決済み整列
 */
public record BalanceBoxSnapshot(BlockParams params, FlowPos pos, Dimension size, Dimension minSize,
		RectFrame frameSpec, AbsoluteInsets margin, AbsoluteInsets padding, double width, double height,
		double minPageAxis, boolean specifiedPageAxis, Align resolvedAlign) {
}
