package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;

/**
 * 内部マーカー(list-style-position: inside)です。
 *
 * <p>
 * 実体はインラインブロックですが、マーカー文字列は末尾空白を含み、
 * その advance がマーカーと本文の隙間を作る(旧セマンティクス)ため、
 * 実レイアウト計測(MeasuredIntrinsics — 行末空白を正しく落とす)の
 * 対象外として型で区別します。明示ギャップによるマーカー配置の再設計
 * (css-lists ::marker)までこの区別を維持します。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class InsideMarkerBox extends InlineBlockBox {
	public InsideMarkerBox(final BlockParams params, final InlinePos pos) {
		super(params, pos);
	}
}
