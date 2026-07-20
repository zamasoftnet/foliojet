package net.zamasoft.foliojet.layout.box;

import java.util.Deque;

/**
 * {@code frames}(背景・境界の描画パス)の反復化(2026-07-20、drawと同じ
 * 理由)用のワークリスト単位です。{@code frames}は{@link IBox}全体に
 * 共通のメソッドではなく、{@link AbstractContainerBox}系統(ブロック・
 * テーブルセル)と、テーブル内部系統({@link AbstractInnerTableBox}の
 * 行・行グループ・列・列グループ)の2系統に独立に存在するため、
 * それぞれの系統の入口メソッドがこの型のワークリストを生成・消化する。
 */
@FunctionalInterface
public interface FramesStep {
	void run(Deque<FramesStep> worklist);
}
