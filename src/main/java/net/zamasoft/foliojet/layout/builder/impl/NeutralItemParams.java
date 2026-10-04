package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;

/**
 * flex・grid の中立・匿名 item の params です。容れ物の文字の特性を引き継ぎ、枠・大きさ・整列などは中立へ戻す。
 *
 * <p>
 * FlexBuilder と GridBuilder に同じ中立化の写しがあり、grid の側にだけ後から align-content の中立化が足されて、
 * flex の匿名 item には容れ物の align-content が効いたままだった(2026-10-04、全体レビュー)。
 * </p>
 */
final class NeutralItemParams {
	private NeutralItemParams() {
	}

	static BlockParams of(final BlockParams container) {
		final BlockParams params = BlockParamsTemplate.freeze(container).materialize();
		params.frame = RectFrame.NULL_FRAME;
		params.element = null;
		params.footnoteId = -1;
		// **コンテナの実効opacityを引き継ぐ**(2026-08-18)。以前は1fへ
		// 戻していたが、visibility:hiddenはopacity 0へ写像される
		// (BoxStyleMapper.setupParams)ため、hiddenなコンテナの匿名・
		// 中立itemだけが描かれてしまう——e-Statのドロップダウンメニューが
		// 本文に重なって出た実欠陥(重なり1,462対)。authored itemは
		// 自分のstyleからvisibilityを継承するので元から正しい。
		params.opacity = container.opacity;
		params.zIndexType = Params.Z_INDEX_AUTO;
		params.zIndexValue = 0;
		params.transform = new AffineTransform();
		params.columns = Columns.NONE_COLUMNS;
		// コンテナのalign-contentを引き継がない(2026-08-29)。itemの箱は
		// コンテナのparamsから作るので、そのままではコンテナの
		// align-content: centerがitem自身の内容整列として効いてしまう。
		// itemが行の高さまで伸びるようになって表面化した(Chromeでは
		// itemは30ptへ伸びるが中身は上端のまま)
		params.blockAlignContent = BoxAlignment.NORMAL;
		// G3a追補(答申Q1): コンテナのwidth/min/max-widthがitemの固有寸法へ
		// 混入しないようsize系も中立化する
		params.size = Dimension.AUTO_DIMENSION;
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		return params;
	}
}
