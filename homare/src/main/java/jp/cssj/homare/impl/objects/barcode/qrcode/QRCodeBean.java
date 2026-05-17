package jp.cssj.homare.impl.objects.barcode.qrcode;

import java.io.UnsupportedEncodingException;

import org.krysalis.barcode4j.impl.AbstractBarcodeBean;
import org.krysalis.barcode4j.output.CanvasProvider;

import com.swetake.util.Qrcode;

/**
 * QRコードの情報の保持と描画を行うBeanです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class QRCodeBean extends AbstractBarcodeBean {
	protected final Qrcode qrcode = new Qrcode();

	protected String charset = "MS932";

	public boolean[][] getCode(String msg) {
		try {
			byte[] data = msg.getBytes(this.charset);
			return this.qrcode.calQrcode(data);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public void generateBarcode(CanvasProvider canvas, String msg) {
		boolean[][] code = this.getCode(msg);
		int width = code[0].length;
		int height = code.length;
		double quietZone = this.quietZone;
		if (!this.doQuietZone) {
			quietZone = 0;
		}
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				if (code[x][y]) {
					canvas.deviceFillRect(x * this.moduleWidth + quietZone, y * this.moduleWidth + quietZone,
							this.moduleWidth, this.moduleWidth);
				}
			}
		}
	}

	public double getBarWidth(int arg0) {
		// QRコードではバーの幅は関係ありません。
		return 0;
	}

}
