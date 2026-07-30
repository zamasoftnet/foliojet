package net.zamasoft.foliojet.layout.box.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.zamasoft.pdfg2d.pdf.StructureRef;

/**
 * タグ付きPDFの構造要素のページ横断レジストリです(欠陥②の修正、
 * 2026-07-30——codex相談
 * copperpdf4/docs/consultations/consult-codex-2026-07-30-structelem-split.txt)。
 *
 * <p>
 * <b>何の欠陥か</b>: StructElemの重複抑止は従来{@code PageBox}単位
 * (ページごとに新しいインスタンス)だったため、同じ論理要素の継続断片が
 * 次ページで描かれると未宣言と判断され、{@code declareStructElement}が
 * 再実行されて<b>1要素が複数のStructElemに分裂</b>していた。pdfg2dの
 * {@code StructureTreeBuilder}は1つのElemに複数ページのMCID
 * ({@code /Type /MCR /Pg})を保持・出力できるため、初出時に宣言した
 * {@link StructureRef}をページを跨いで再利用すれば1要素=1 StructElemになる。
 * </p>
 *
 * <p>
 * <b>寿命</b>: {@code PageSequence}が文書(=PDF writer)単位で1つ保持し、
 * 各ページの{@code PageBox}へ{@code setStructOutput}で渡す。エントリは
 * ページ末({@link #endPage})に「そのページで宣言または再利用された
 * もの」だけを残して破棄する——継続断片は常に直後のページに現れるため
 * 2ページ分の窓で足り、巨大文書でも保持量はページ内要素数に有界。
 * </p>
 *
 * <p>
 * <b>キー</b>: {@code StructureElement.elementKey() >= 0}(文書順の通し
 * 番号=論理identity)のみ。負値は匿名・擬似要素でオブジェクトidentity
 * 比較が契約のため、レジストリの対象外(従来どおりページ内管理)。
 * </p>
 */
public final class TaggedStructureContext {

	/**
	 * 1論理要素ぶんの宣言済み参照の束です。{@code LI}だけは
	 * {@code LBody}を伴う2段のため、スタックへ積む参照列
	 * ({@code refs})と描画先({@code contentRef}=末尾)を分けて持つ。
	 * hit時の同一性検証用にrole・scope・親も保持する。
	 */
	record Binding(StructureRef[] refs, String role, String scope, StructureRef parent) {
		StructureRef contentRef() {
			return this.refs[this.refs.length - 1];
		}
	}

	private final Map<Long, Binding> byKey = new HashMap<>();

	/** このページで宣言または再利用されたキー(endPageの生存判定)。 */
	private final Set<Long> touched = new HashSet<>();

	/**
	 * 既存の宣言を返します(なければnull)。返した場合そのキーはこの
	 * ページの生存対象になる。
	 */
	Binding lookup(final long elementKey) {
		final Binding binding = this.byKey.get(elementKey);
		if (binding != null) {
			this.touched.add(elementKey);
		}
		return binding;
	}

	/** 初出宣言を登録します。 */
	void register(final long elementKey, final Binding binding) {
		this.byKey.put(elementKey, binding);
		this.touched.add(elementKey);
	}

	/**
	 * ページ境界の清算です({@code PageSequence.drawPage}の末尾)。
	 * このページで触れられなかったエントリ(=次ページに継続断片が
	 * 現れない要素)を破棄し、保持量を有界に保つ。
	 */
	public void endPage() {
		this.byKey.keySet().retainAll(this.touched);
		this.touched.clear();
	}
}
