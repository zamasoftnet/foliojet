package net.zamasoft.foliojet.css;

import org.xml.sax.Attributes;

/**
 * レイアウト産物({@code Params.element})が保持するソース要素の
 * 読み取り契約です(2026-07-24新設、E-6増分3b-4)。
 *
 * <p>
 * live構築では{@link CSSElement}が、ソース再生(BoxRecipeの
 * materialize)では{@code StructureToken}(layout.segment)がこの契約を
 * 実装する。スタイル適用(セレクタ照合)は記録前に完了しているため、
 * レイアウト後の読み手が必要とするのは次の4つだけである(2026-07-24の
 * 全読み手実査——E-6増分3b-4):
 * </p>
 *
 * <ul>
 * <li>{@link #elementKey()} — string-set(GCPM)の保留解決キー
 * ({@code AbstractVisitor.visitBox})</li>
 * <li>{@link #lName()} — Tagged PDFの構造ロール
 * ({@code TaggedPdf.blockRole})・フォーム部品種別</li>
 * <li>{@link #id()} — 幾何テストハーネス({@code TestPDFVisitor})の
 * 要素特定</li>
 * <li>{@link #atts()} — ハイパーリンク(xlink:href)・イメージマップ・
 * フラグメント(id)・ブックマーク(header)・フォーム属性・
 * {@code <th scope>}({@code TaggedPdf.headerScope})。{@code null}は
 * 擬似要素・匿名要素(注釈系の読み手は早期リターンする)</li>
 * </ul>
 *
 * <p>
 * これに加えて<b>参照identity</b>が契約に含まれる: 同じ論理要素の
 * ボックスは同じインスタンスを共有する(Tagged PDFの構造タグ二重開き
 * 防止——{@code PageBox.beginStruct}のidentity set)。live側は同一
 * {@code CSSElement}の共有で、再生側は再生セッション内のintern
 * ({@code SegmentExecutor})でこれを保つ。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface StructureElement {

	/** 文書順の通し番号です(擬似要素・匿名要素は-1)。 */
	long elementKey();

	/** XML/HTML要素のローカル名です。 */
	String lName();

	/** CSS IDセレクタに対応するIDです(なければ{@code null})。 */
	String id();

	/** XML/HTML属性です(擬似要素・匿名要素は{@code null})。 */
	Attributes atts();
}
