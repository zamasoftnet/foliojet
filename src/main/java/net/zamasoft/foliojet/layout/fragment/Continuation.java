package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;

/**
 * 改ページの継続記述です(ARCHITECTURE §5.7、C0')。
 *
 * <p>
 * 「次のフラグメンテナへ何を送るか」を型で表します。現段階(C0')は
 * 従来の残余ボックス木を {@link LegacyCarry} として丸ごと運ぶ互換
 * ラッパーで、挙動は従来の restyle と同一です。移行スライス
 * (C2=閉部分木の {@link SourceRange} 化、C3=切断段落尾部の
 * {@link TextTail} 化、C1=祖先チェーンのログ再インスタンス化)が進む
 * ごとに LegacyCarry の中身が痩せ、発火ゼロになった種別から
 * splitPageAxis の残余構築コードが死ぬ(§5.5)。
 * </p>
 *
 * @param depth  再開時に復元する祖先チェーンの深さ(flowStack の要素数)
 * @param items  次のフラグメンテナへ送る内容(文書順)
 * @param ranges 閉部分木の再生範囲(C2: 破断時に一括判定した記録。
 *               再開走行はこれを消費するだけで、ゲートを再計算しない)
 * @author MIYABE Tatsuhiko
 */
public record Continuation(int depth, List<Item> items,
		java.util.Map<net.zamasoft.foliojet.layout.box.IBox, SourceRange> ranges) {
	/**
	 * LegacyCarry の発火計測です(移行進捗の観測: これがコーパスで
	 * ゼロになった時が残余ボックス構築の死)。
	 */
	public static final AtomicLong LEGACY_CARRIES = new AtomicLong();

	/**
	 * 継続内容の1項目です。
	 */
	public sealed interface Item permits SourceRange, TextTail, LegacyCarry, ContinuationFrame {
	}

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
			List<SourceRange> prefixItems, OpenTail tail) implements Item {
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
		 * 従来の深さ規約による legacy 走行です。生の depth を読むのは
		 * この consumer 一箇所だけにする(外部レビュー #6 の adapter)。
		 * 最内の収集フレーム(木に残った moved-open ボックス・開き
		 * テキストの継続)と、収集不能な破断のルートフレームが持つ。
		 *
		 * @param depth このフレームのコンテナ走行に渡す残り深さ
		 */
		record LegacyOpenTail(int depth) implements OpenTail {
			public LegacyOpenTail {
				// ルート=flowStack 全深さ、最内=残 depth(D - chain 数)。
				// いずれも 1 以上(チェーンは flowStack[1..] の部分列)
				assert depth > 0 : depth;
			}
		}
	}

	/**
	 * チェーン断片の収集器です(C1b)。pageBreak の split カスケード中、
	 * 事前検分(pre-flight)で収集可と判定された祖先チェーンの各レベルが
	 * 自分の断片状態をここへ記録します(カスケードの再帰順=内側が先)。
	 */
	public static final class ChainCollector {
		private final java.util.List<ChainFragment> fragments = new java.util.ArrayList<>();

		public void add(final ChainFragment fragment) {
			this.fragments.add(fragment);
		}

		/**
		 * 収集された断片を外側→内側の順で返します。
		 */
		public java.util.List<ChainFragment> outerToInner() {
			final java.util.List<ChainFragment> list = new java.util.ArrayList<>(this.fragments);
			java.util.Collections.reverse(list);
			return list;
		}
	}

	/**
	 * チェーン断片の収集ドラフトです(C1b。切断が貫通した開いた祖先
	 * 1レベル分)。pageBreak が水位計算・prefix 吸収(C1c)の後に
	 * {@link ContinuationFrame} の入れ子へ組み上げる。
	 *
	 * @param recipe      断片ボックスの再構成レシピ(切断時に値キャプチャ)
	 * @param state       断片状態
	 * @param container   このレベルの残余コンテナ(閉じた先行アイテム+
	 *                    フロート。チェーン子は含まれない)
	 * @param crossExtent 切断時点の交差軸寸法
	 */
	public record ChainFragment(FragmentRecipe recipe, FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container, double crossExtent) {
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
	public record SourceRange(int serial, long fromId, long toId) implements Item {
	}

	/**
	 * 切断段落の尾部です(C3 で使用予定)。
	 *
	 * @param charOffset     再開位置のソース文字オフセット
	 * @param endIdExclusive 尾部の終端(負ならログ末尾まで)
	 */
	public record TextTail(int charOffset, long endIdExclusive) implements Item {
	}

	/**
	 * 従来の残余ボックス木の運搬です(移行期フォールバック)。
	 * 再開は従来どおり restyle(箱の再演)で行われます。
	 *
	 * @param remainder 残余ボックス木のルート
	 */
	public record LegacyCarry(IPageBreakableBox remainder) implements Item {
	}
}
