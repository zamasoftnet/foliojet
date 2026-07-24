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
 * これに加えて<b>論理identity</b>が契約に含まれる: 同じ論理要素の
 * ボックスは同じ要素を指すと識別できること(Tagged PDFの構造タグ
 * 二重開き防止——{@code PageBox.beginStruct})。実要素
 * ({@code elementKey >= 0})は{@code elementKey}(文書順の通し番号)で
 * 識別する——E-6増分4b(2026-07-24)でliveの祖先({@code CSSElement})と
 * range再生された子孫({@code StructureToken})が同じ論理要素を指す
 * ケース(例: {@code <li>}のprincipal boxとmarker box)が生まれ、参照
 * identityでは再生境界をまたぐ共有を表現できなくなったため。
 * 擬似要素・匿名要素({@code elementKey < 0})はstatic singletonの参照
 * identityのまま。再生セッション内のintern({@code SegmentExecutor})は
 * インスタンス数の抑制として引き続き行う。
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
