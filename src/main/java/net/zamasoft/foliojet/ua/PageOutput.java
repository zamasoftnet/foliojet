package net.zamasoft.foliojet.ua;

import java.io.IOException;

import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;

/**
 * ページ結果の出力面です。
 */
public interface PageOutput {
	/**
	 * 次のページのグラフィックコンテキストを返します。
	 *
	 * @param width  ポイント単位の幅。
	 * @param height ポイント単位の高さ。
	 */
	public GC nextPage(double width, double height);

	/**
	 * ページへの描画を完了します。
	 */
	public void closePage(GC gc) throws IOException;

	public Visitor getVisitor(GC gc);

	/**
	 * 結果文書を構築します。
	 */
	public void finish() throws BrokenResultException, IOException;

	/**
	 * UAを破棄して、リソースを解放します。
	 */
	public void dispose();

	/**
	 * 文書のメタ情報を設定します。
	 */
	public void meta(String name, String content);

	public void setBoundSide(BoundSide boundSide);

	public BoundSide getBoundSide();
}
