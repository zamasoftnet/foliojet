package net.zamasoft.foliojet.layout.fragment;

import java.util.Map;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * COLUMN継続の正本トークンです(2026-07-24新設、E-3増分5。旧
 * {@code ColumnResumeProgram}+{@code RootBuilder.CompiledColumn}を置換
 * した小さなドメイントークン。docs/consultations/consult-e3-single-source
 * -codex.md §1)。
 *
 * <p>
 * PAGE継続({@link Continuation})との本質的な非対称性——ownerボックス
 * 自体はfragment再構成されず、同一インスタンスへ新columnをcommitする
 * だけ——のため、PAGEと同じレコードへは統合しない。owner直下の残余は
 * {@link #anchor()}が、ownerより内側で貫通した子孫チェーンは
 * {@link #childFrame()}が表す(貫通しなければnull——owner直下に開いた
 * 子孫が全くない、または切断が貫通しなかった正規のケース)。
 * </p>
 *
 * <p>
 * {@code ranges}は意図的に{@code Map.copyOf}しない——consume-once用の
 * mutableなマップであり、{@code RootBuilder.replayFromSource()}が消費時に
 * 直接{@code remove()}する(read-onlyにすると本番で
 * {@code UnsupportedOperationException}になる。旧{@code CompiledColumn}
 * の規約の踏襲)。
 * </p>
 *
 * @param snapshot   破断時の相対open pathスナップショット
 *                   (index 0 = COLUMN_OWNER anchor)
 * @param anchor     owner直下の残余
 * @param childFrame owner直下で貫通した場合の継続フレーム(貫通しなければ
 *                   null)
 * @param ranges     閉部分木の再生範囲(consume-once、mutable)
 * @param pathShape  {@link ContinuationValidator#validateColumn}が返した
 *                   検証済みopen path形(tail policy導出・終端OpenShapeの
 *                   正本)
 */
public record ColumnContinuation(OpenPathSnapshot snapshot, ColumnAnchor anchor,
		Continuation.ContinuationFrame childFrame, Map<IBox, Continuation.SourceRange> ranges,
		ContinuationValidator.PathShape pathShape) {
}
