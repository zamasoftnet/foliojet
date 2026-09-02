package net.zamasoft.foliojet.ua;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 複数の文書(EPUBのspine項目)を<b>独立した単位</b>として受け取れる出力です
 * (2026-09-02)。
 *
 * <p>
 * 項目1つは「単一の文書を変換したときの出力そのもの」になり、項目ごとに
 * 子のUAを作って組む。子は互いに独立なので並列に組めるし、逐次で組んでも
 * 出力は1バイトも変わらない。結果は<b>呼び出し順(spine順)に</b>解放される
 * ——先頭の項目は組み上がるそばから流れ、後続は先頭が終わるまで控える。
 * 設計の全文は{@code docs/epub-paged-svg-design.md}。
 * </p>
 *
 * <p>
 * これを実装しないUAへEPUBを渡すと、従来どおり1つのUAへ全項目を順に
 * 流す(PDF・画像出力)。
 * </p>
 */
public interface MultiDocumentOutput extends UserAgent {
	/**
	 * 文書の単位(spine項目)の記述。
	 *
	 * @param index    spine内の位置(1起点)。除外された項目も番号を消費する
	 * @param idref    OPFの{@code itemref/@idref}
	 * @param uri      項目のパス(EPUB内の絶対パス、{@code OEBPS/ch1.xhtml})
	 * @param included この変換で組む項目か({@code input.epub.spine}で絞ると偽が混じる)
	 */
	record DocumentUnit(int index, String idref, URI uri, boolean included) {
	}

	/** 目次の1項目。{@code uri}は項目のパス、{@code fragment}はその中の位置。 */
	record TocEntry(String label, URI uri, String fragment, List<TocEntry> children) {
	}

	/**
	 * 全体の記述。項目を開く前に1回渡す。
	 *
	 * @param units                    spine順の全項目(除外された項目を含む)
	 * @param pageProgressionDirection {@code ltr}/{@code rtl}/{@code default}
	 * @param metadata                 題名・著者など
	 * @param toc                      目次(無ければ空)
	 */
	record DocumentSet(List<DocumentUnit> units, String pageProgressionDirection, Map<String, String> metadata,
			List<TocEntry> toc) {
	}

	/** 全体を記述します。{@link #openDocument}より先に、1回だけ呼ぶこと。 */
	void describeDocuments(DocumentSet documents);

	/**
	 * 項目を組む子のUAを返します。
	 *
	 * <p>
	 * <b>呼び出し順が解放順になる。</b>spine順に呼ぶこと。返ったUAは
	 * 呼び出し側が自分のパス駆動({@code prepare}→組版→{@code finish})を回し、
	 * 別のスレッドで走らせてよい。{@code finish()}で項目の完了を親へ伝える。
	 * </p>
	 */
	UserAgent openDocument(DocumentUnit unit);
}
