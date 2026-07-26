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
	 * 画像を取得します。
	 */
	public Image getImage(Source source) throws IOException;

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
}
