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
public record Continuation(int depth, List<Item> items, java.util.Map<net.zamasoft.foliojet.layout.box.IBox, SourceRange> ranges) {
	/**
	 * LegacyCarry の発火計測です(移行進捗の観測: これがコーパスで
	 * ゼロになった時が残余ボックス構築の死)。
	 */
	public static final AtomicLong LEGACY_CARRIES = new AtomicLong();

	/**
	 * 継続内容の1項目です。
	 */
	public sealed interface Item permits SourceRange, TextTail, LegacyCarry, RootFragment {
	}

	/**
	 * ルート断片の継続です(C1a)。断片ボックスは split では構築されず、
	 * 再開時に prev の params/pos と断片状態から再構成されます。
	 *
	 * @param prev        前断片(切りつめ済み)
	 * @param state       断片状態
	 * @param container   継続断片の内容
	 * @param crossExtent 切断時点の交差軸寸法
	 */
	public record RootFragment(net.zamasoft.foliojet.layout.box.AbstractBlockBox prev, FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container, double crossExtent) implements Item {
	}

	/**
	 * 丸ごと移動する閉部分木のソース範囲です(C2 で使用予定)。
	 *
	 * @param fromId 部分木の StartBlock の EventId
	 * @param toId   対応する EndBlock の EventId
	 */
	public record SourceRange(long fromId, long toId) implements Item {
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
