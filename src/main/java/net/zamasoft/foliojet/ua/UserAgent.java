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
