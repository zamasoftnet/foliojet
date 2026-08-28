package net.zamasoft.foliojet.ua;

import java.io.IOException;
import java.util.Map;

import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * UAのプロファイルです。
 * デバイス既定値の読み取りは {@link DeviceStyle}、ページ出力は {@link PageOutput} が担います。
 *
 * @author MIYABE Tatsuhiko
 */
public interface UserAgent extends SourceResolver, MessageHandler, DeviceStyle, PageOutput {
	/**
	 * 処理段階を準備します。
	 */
	public void prepare(PrepareMode mode);

	public UAContext getUAContext();

	public PassContext getPassContext();

	public DocumentContext getDocumentContext();

	/**
	 * プロパティを返します。
	 */
	public String getProperty(String name);

	/**
	 * プロパティを設定します。
	 */
	public void setProperty(String name, String value);

	public void setProperties(Map<String, String> props);

	public void setSourceResolver(SourceResolver resolver);

	public SourceResolver getSourceResolver();

	public void setMessageHandler(jp.cssj.cti2.message.MessageHandler messageHandler);

	/**
	 * 処理を中断します。modeはCTISessionのABORT_*値です。
	 */
	public void abort(byte mode);

	/**
	 * <b>協調的な中断点</b>。{@link #abort(byte)}が呼ばれていれば
	 * {@link AbortException}を投げます。
	 *
	 * <p>
	 * <b>なぜ要るか。</b> 変換を外から止める手段は{@code abort()}しか
	 * ないが、それは<b>旗を立てるだけ</b>で、エンジンがその旗を読む場所が
	 * なければ何も起きない。従来は読む場所がページの境目だけだったので、
	 * <b>1ページの処理が終わらない文書は永久に止められなかった</b>
	 * (2026-07-27、10万文書の掃過が停止して発覚)。
	 * </p>
	 *
	 * <p>
	 * 長く走るループの先頭で呼ぶこと。粒度は行・表の行・ページ程度に
	 * 粗く保つ——コストはvolatile 1個の読み取りだが、グリフ単位に置けば
	 * 積もる。
	 * </p>
	 */
	public void checkAbort(byte mode);

	/**
	 * <b>実際に仕事が1単位進んだ</b>ことを記録します。締切はこれを基準に
	 * 「詰まっているか」を測ります。
	 *
	 * <p>
	 * <b>「コードが動いた」ではなく「仕事が終わった」場所で呼ぶこと。</b>
	 * 空回りするループから呼ぶと、進捗を偽装して締切を無効にしてしまいます。
	 * 現在の呼び出し元: ページの出力・画像の読み込み完了・表の行の確定。
	 * </p>
	 */
	public void noteProgress();

	/**
	 * 画像を取得します。
	 */
	public Image getImage(Source source) throws IOException;

	/**
	 * 記録済みの画像寸法を、<b>資源を解決する前に</b>返します。
	 *
	 * <p>
	 * 寸法しか要らないパスで既に測った画像なら、{@link #resolve(java.net.URI)}を
	 * 呼ばずに済みます。解決そのものが取得を伴う経路(CTIPでクライアントへ
	 * 資源を要求する場合)では、先にこれを引かないと転送が起きてしまいます。
	 * </p>
	 *
	 * @return 記録があればその寸法、無ければ{@code null}。
	 */
	public default Image getImageMetrics(java.net.URI uri) {
		return null;
	}

	/**
	 * 画像を取得し、寸法しか要らないパスなら<b>要求時のURIで</b>寸法を記録します。
	 * 相対URIのまま記録するので、同じEPUBを別の基底から与えても当たります。
	 */
	public default Image getImage(java.net.URI uri, Source source) throws IOException {
		return this.getImage(source);
	}

	public boolean isMeasurePass();

	/**
	 * 現在STRUCTURE_SCANパス(実レイアウトを組まない軽量な事前走査、
	 * PrepareMode.STRUCTURE_SCAN)中かを返します。
	 */
	public boolean isStructureScanPass();

	/**
	 * 現在最終パス(PrepareMode.LAST_PASS)中かを返します。
	 * target-counter()系の収束性チェック(最終パスまでに参照先が
	 * 確定したか)に使います。
	 */
	public boolean isLastPass();

	/**
	 * 取得した画像の<b>元のバイト列</b>を使えるかを返します(2026-08-28)。
	 *
	 * <p>
	 * ページ分割SVGのように、画像を「ブラウザが読む資源」として外へ出す
	 * 出力はこれを{@code true}にします。JPEGを復号してPNGへ焼き直すのは
	 * 時間も容量も損で、実測ではWikipedia1記事で資源が8MB相当から30.5MBへ
	 * 膨らんでいました。PDFのように自前の画像表現へ変換する出力は
	 * {@code false}のままにします。
	 * </p>
	 *
	 * <p>
	 * <b>出力プロパティで判定しないこと。</b> かつては
	 * {@code output.type}の文字列比較で決めていたが、画像を読み込む時点の
	 * UAでは{@code application/pdf}が返るため一度も成立していなかった
	 * (実測で判明)。能力はUA自身に訊く。
	 * </p>
	 */
	public default boolean keepsEncodedImages() {
		return false;
	}
}
