package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;

/**
 * 行内の分割機会です(M3a)。従来 TextBuilder が2つのカーソル
 * (textUnitElementCount/textUnitGlyphCount)と描画時の instanceof 再判定で
 * 暗黙に追跡していた「最後に収まった分割可能点」を型に昇格したものです。
 *
 * <ul>
 * <li>elementCount — バッファ内でこの機会までに含まれる要素数</li>
 * <li>glyphCount — 機会が組み立て中テキストの内部にある場合のグリフ位置
 * (テキスト境界なら 0 または全グリフ数)</li>
 * <li>hyphen — 機会がソフトハイフンによる場合、切断時に実体化する対象</li>
 * </ul>
 *
 * <p>
 * 注意(M3b/M3c への設計メモ): 現行 greedy は「記録時に収まっていた機会」
 * のみを保持する。利用可能行幅はフロートにより行ごとに変わる
 * (locateLine)ため、機会の実行可能性は記録時と選択時で異なり得る。
 * 全機会のリスト化と選択時評価への移行は、M6 の BreakToken 再開意味論と
 * 同時に設計する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record BreakOpportunity(int elementCount, int glyphCount, SoftHyphen hyphen) {
	/** 分割機会なし(バッファ先頭)。 */
	public static final BreakOpportunity NONE = new BreakOpportunity(0, 0, null);
}
