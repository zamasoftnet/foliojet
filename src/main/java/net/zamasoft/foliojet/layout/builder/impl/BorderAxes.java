package net.zamasoft.foliojet.layout.builder.impl;

import java.util.function.Function;

import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

/**
 * つぶし境界の辺選択です(P2-3: §5.2b 表ビルダー統一)。物理境界
 * (RectBorder)から、グリッドの H境界(行進行に直交)の始端/終端・
 * V境界の始端/終端に採る辺への4射影。縦書きと横書きの collapse 本文は
 * この射影だけが異なる(TwoPass の一括適用・OnePass のストリーミング
 * 蓄積の両方で共有)。
 */
record BorderAxes(Function<RectBorder, Border> hStart, Function<RectBorder, Border> hEnd,
		Function<RectBorder, Border> vStart, Function<RectBorder, Border> vEnd) {
	static final BorderAxes VERTICAL = new BorderAxes(RectBorder::getRight, RectBorder::getLeft, RectBorder::getTop,
			RectBorder::getBottom);
	static final BorderAxes HORIZONTAL = new BorderAxes(RectBorder::getTop, RectBorder::getBottom, RectBorder::getLeft,
			RectBorder::getRight);
}
