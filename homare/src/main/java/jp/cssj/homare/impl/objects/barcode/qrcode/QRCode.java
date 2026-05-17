package jp.cssj.homare.impl.objects.barcode.qrcode;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.krysalis.barcode4j.BarcodeDimension;
import org.krysalis.barcode4j.impl.ConfigurableBarcodeGenerator;
import org.krysalis.barcode4j.tools.Length;

/**
 * QRコードの設定を行います。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class QRCode extends ConfigurableBarcodeGenerator {
	public QRCode() {
		this.bean = new QRCodeBean();
		this.bean.setModuleWidth(.25);
	}

	public void configure(Configuration config) throws ConfigurationException {
		QRCodeBean bean = (QRCodeBean) this.bean;
		String mws = config.getChild("module-width").getValue(null);
		if (mws != null) {
			Length mw = new Length(mws, "mm");
			bean.setModuleWidth(mw.getValueAsMillimeter());
		}

		// Quiet zone
		bean.doQuietZone(config.getChild("quiet-zone").getAttributeAsBoolean("enabled", true));
		String qzs = config.getChild("quiet-zone").getValue(null);
		if (qzs != null) {
			Length qz = new Length(qzs, "mw");
			if (qz.getUnit().equalsIgnoreCase("mw")) {
				bean.setQuietZone(qz.getValue() * bean.getModuleWidth());
			} else {
				bean.setQuietZone(qz.getValueAsMillimeter());
			}
		} else {
			bean.setQuietZone(bean.getModuleWidth());
		}

		Configuration version = config.getChild("version", false);
		Configuration encMode = config.getChild("encmode", false);
		Configuration ecc = config.getChild("ecc", false);
		Configuration charset = config.getChild("charset", false);
		if (version != null) {
			bean.qrcode.setQrcodeVersion(Integer.parseInt(version.getValue()));
		}
		if (encMode != null) {
			bean.qrcode.setQrcodeEncodeMode(encMode.getValue().charAt(0));
		}
		if (ecc != null) {
			bean.qrcode.setQrcodeErrorCorrect(ecc.getValue().charAt(0));
		}
		if (charset != null) {
			bean.charset = charset.getValue();
		}
	}

	public BarcodeDimension calcDimensions(String msg) {
		QRCodeBean bean = (QRCodeBean) this.bean;
		boolean[][] code = bean.getCode(msg);
		double width = code[0].length;
		double height = code.length;
		width *= bean.getModuleWidth();
		height *= bean.getModuleWidth();
		if (bean.hasQuietZone()) {
			width += bean.getQuietZone() * 2;
			height += bean.getQuietZone() * 2;
		}
		return new BarcodeDimension(width, height);
	}
}
