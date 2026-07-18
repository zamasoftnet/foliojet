package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

/**
 * 改ページの継続記述です(ARCHITECTURE §5.7)。
 *
 * <p>
 * 「次のフラグメンテナへ何を送るか」を型で表します。内容はルート
 * フレーム({@link ContinuationFrame})の入れ子: 各フレームがレシピ+
 * 断片状態+残余コンテナ+吸収済み再生範囲(prefixItems)を持ち、
 * 開いた子孫を tail で表します。C0' 期の互換ラッパー(残余ボックス木を
 * 丸ごと運ぶ LegacyCarry)は C1 完了で撤去済み — 残余ボックス木の
 * 運搬という概念は、プレーンブロックチェーンについては存在しません。
 * </p>
 *
 * @param depth  再開後に検証する祖先チェーンの深さ(flowStack の要素数。
 *               走行自体は tail の型が駆動する — C3 完了後に削除予定)
 * @param root   ルートフレーム
 * @param ranges 閉部分木の再生範囲(C2: 破断時に一括判定した記録。
 *               再開走行はこれを消費するだけで、ゲートを再計算しない。
 *               box フォールバック付きのネスト部分木用 — C4 で縮小)
 * @author MIYABE Tatsuhiko
 */
public record Continuation(int depth, ContinuationFrame root,
		java.util.Map<net.zamasoft.foliojet.layout.box.IBox, SourceRange> ranges) {
	/**
	 * 継続断片のフレームです(C1d-A: ルート断片とチェーン断片の統一形)。
	 * 断片ボックスは split では構築されず、resume がレシピと断片状態から
	 * 再構成します(C1d-B: 旧ボックスを factory として保持しない)。
	 * 開いた子孫は tail の入れ子で表します。
	 *
	 * @param recipe      断片ボックスの再構成レシピ(切断時に値キャプチャ)
	 * @param state       断片状態
	 * @param container   このレベルの残余コンテナ(チェーン子は含まれない)
	 * @param crossExtent 切断時点の交差軸寸法
	 * @param prefixItems コンテナから吸収された閉部分木の再生範囲(C1c)
	 * @param tail        開いた続きの表現
	 */
	public record ContinuationFrame(FragmentRecipe recipe, FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container, double crossExtent,
			List<SourceRange> prefixItems, OpenTail tail) {
	}

	/**
	 * フレームの開いた続きの表現です(C1d-A)。
	 */
	public sealed interface OpenTail {
		/**
		 * 切断が貫通した次の(内側の)フレームです。
		 */
		record Child(ContinuationFrame frame) implements OpenTail {
		}

		/**
		 * 継続末尾の開き形です(M3b Phase 3c で旧 int depth 規約を型化)。
		 * 最内の収集フレーム(木に残った moved-open ボックス・開き
		 * テキストの継続)と、収集不能な破断のルートフレームが持つ。
		 *
		 * @param shape このフレームのコンテナ走行に渡す末尾の開き形
		 */
		record OpenTailShape(OpenShape shape) implements OpenTail {
			public OpenTailShape {
				// ルート=flowStack 全深さ、最内=残 depth(D - chain 数)。
				// いずれも開いている(チェーンは flowStack[1..] の部分列)。
				// 形は M3b Phase 3c で型化(旧 int depth 規約)
				assert !(shape instanceof OpenShape.Closed) : shape;
			}
		}
	}

	/**
	 * 丸ごと移動する閉部分木のソース範囲です(C2 で記録、C1c で
	 * prefixItems として運搬)。
	 *
	 * @param serial 元のコンテナ内での合流順(BoxHolder の serial。
	 *               フロートとの相対順を保存する。ranges マップ用途では -1)
	 * @param fromId 部分木の StartBlock の EventId
	 * @param toId   対応する EndBlock の EventId
	 */
	public record SourceRange(int serial, long fromId, long toId) {
	}
}
