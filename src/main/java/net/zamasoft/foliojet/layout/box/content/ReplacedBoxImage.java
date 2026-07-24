package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * レイアウト中のボックスから状態を受け取る画像です(実装は
 * {@link Image}でもあること)。{@code AbstractReplacedBox.calculateSize()}
 * がレイアウトのたびに{@link #setReplacedBox}を呼ぶため、実装は
 * 「共有不可の変異する状態」を持つ——通常の(URL等から読み込んだ)
 * 不変・再入可能な画像とは扱いが異なり、凍結経路
 * ({@code ReplacedParamsTemplate.freeze})は記録時に{@link #duplicate}の
 * 独立複製を凍結し、materializeごとにさらに複製を配ることで
 * live・再生間および再生同士の共有状態を切る(E-6増分3b-6)。
 */
public interface ReplacedBoxImage {
	public void setReplacedBox(AbstractReplacedBox box, double width, double height);

	/**
	 * 独立した複製を返します(E-6増分3b-3導入・3b-6でfreeze経路の正規
	 * 部品化、2026-07-24)。ソース再生はライブのボックス木に触れない
	 * 新品のボックスを作るが、画像インスタンスを共有すると
	 * {@link #setReplacedBox}のback-referenceを再生ボックスが奪い、
	 * ライブ側の描画状態を破壊しうる。複製は描画内容(不変部分)を
	 * 共有してよいが、{@link #setReplacedBox}で受け取る状態は複製ごとに
	 * 独立であること。<b>複製自身も{@link ReplacedBoxImage}(かつ
	 * {@link Image})を実装すること</b>——凍結済み複製からmaterializeごとに
	 * さらに複製を配るため({@code ReplacedParamsTemplate.materialize})。
	 */
	public Image duplicate();
}
