package net.zamasoft.foliojet.impl.objects.barcode.isbn;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.krysalis.barcode4j.ChecksumMode;
import org.krysalis.barcode4j.HumanReadablePlacement;
import org.krysalis.barcode4j.impl.upcean.UPCEAN;
import org.krysalis.barcode4j.tools.Length;

/**
 * ISBNバーコードの設定を行います。実質はEAN13と同じです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class ISBN extends UPCEAN {

	/** Create a new instance. */
	public ISBN() {
		this.bean = new ISBNBean();
	}

	public void configure(Configuration cfg) throws ConfigurationException {
		// Module width (MUST ALWAYS BE FIRST BECAUSE QUIET ZONE MAY DEPEND ON
		// IT)
		Configuration c = cfg.getChild("module-width", false);
		if (c != null) {
			Length h = new Length(c.getValue(), "mm");
			getBean().setModuleWidth(h.getValueAsMillimeter());
		}

		// Height (must be evaluated after the font size because of setHeight())
		c = cfg.getChild("height", false);
		if (c != null) {
			Length h = new Length(c.getValue(), "mm");
			getBean().setHeight(h.getValueAsMillimeter());
		}

		// Quiet zone
		getBean().doQuietZone(cfg.getChild("quiet-zone").getAttributeAsBoolean("enabled", true));
		String qzs = cfg.getChild("quiet-zone").getValue(null);
		if (qzs != null) {
			Length qz = new Length(qzs, "mw");
			if (qz.getUnit().equalsIgnoreCase("mw")) {
				getBean().setQuietZone(qz.getValue() * getBean().getModuleWidth());
			} else {
				getBean().setQuietZone(qz.getValueAsMillimeter());
			}
		}

		// Vertical quiet zone
		String qzvs = cfg.getChild("vertical-quiet-zone").getValue(null);
		if (qzvs != null) {
			Length qz = new Length(qzvs, Length.INCH);
			if (qz.getUnit().equalsIgnoreCase("mw")) {
				getBean().setVerticalQuietZone(qz.getValue() * getBean().getModuleWidth());
			} else {
				getBean().setVerticalQuietZone(qz.getValueAsMillimeter());
			}
		}

		Configuration hr = cfg.getChild("human-readable", false);
		if ((hr != null) && (hr.getChildren().length > 0)) {
			// Human-readable placement
			String v = hr.getChild("placement").getValue(null);
			if (v != null) {
				getBean().setMsgPosition(HumanReadablePlacement.byName(v));
			}

			c = hr.getChild("font-size", false);
			if (c != null) {
				Length fs = new Length(c.getValue());
				getBean().setFontSize(fs.getValueAsMillimeter());
			}

			getBean().setFontName(hr.getChild("font-name").getValue("OCRB"));

			getBean().setPattern(hr.getChild("pattern").getValue(""));
		}

		// Checksum mode
		getUPCEANBean().setChecksumMode(
				ChecksumMode.byName(cfg.getChild("checksum").getValue(ChecksumMode.CP_AUTO.getName())));
	}
}