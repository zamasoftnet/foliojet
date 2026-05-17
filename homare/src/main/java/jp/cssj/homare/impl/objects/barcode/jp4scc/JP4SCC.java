package jp.cssj.homare.impl.objects.barcode.jp4scc;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.krysalis.barcode4j.BarcodeDimension;
import org.krysalis.barcode4j.impl.ConfigurableBarcodeGenerator;
import org.krysalis.barcode4j.tools.Length;

/**
 * Japanese Post 4-State Customer Codeの設定を行います。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class JP4SCC extends ConfigurableBarcodeGenerator {
	public JP4SCC() {
		this.bean = new JP4SCCBean();
		this.bean.setModuleWidth(.6);
	}

	public void configure(Configuration cfg) throws ConfigurationException {
		JP4SCCBean bean = (JP4SCCBean) this.bean;
		String mws = cfg.getChild("module-width").getValue(null);
		if (mws != null) {
			Length mw = new Length(mws, "mm");
			bean.setModuleWidth(mw.getValueAsMillimeter());
		}
	}

	public BarcodeDimension calcDimensions(String msg) {
		return new BarcodeDimension(this.bean.getModuleWidth() * 133, this.bean.getModuleWidth() * 6);
	}
}
