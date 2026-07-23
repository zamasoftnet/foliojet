package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;

/**
 * 新規floatの配置計画です(2026-07-23新設、排除域P1増分3——
 * `docs/consultations/consult-exclusion-p1-design-codex.txt`の設計)。
 *
 * <p>
 * {@code BlockBuilder.tryFloatPlacement}が副作用なしで算出する値で、
 * commitするまでレイアウト状態は一切変わらない——試行して捨てるだけで
 * rollbackになる(undo log・トランザクションは作らない、という設計
 * 判断)。座標はすべてformatting context内の絶対物理位置。短命な値で
 * あり、同じbuilderで他のレイアウト操作を挟まずcommitする契約
 * (Flow owner・世代・rollback closureは持たせない)。
 * </p>
 */
record FloatPlacementDelta(IFloatBox box, FloatSide side, AxisSpan lineSpan, AxisSpan pageSpan,
		FloatCommitKind kind) {
}
