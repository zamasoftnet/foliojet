package jp.cssj.homare.impl.objects.barcode.isbn;

import org.krysalis.barcode4j.ClassicBarcodeLogicHandler;
import org.krysalis.barcode4j.HumanReadablePlacement;
import org.krysalis.barcode4j.impl.upcean.EAN13LogicImpl;
import org.krysalis.barcode4j.impl.upcean.UPCEANBean;
import org.krysalis.barcode4j.impl.upcean.UPCEANLogicImpl;
import org.krysalis.barcode4j.output.Canvas;
import org.krysalis.barcode4j.output.CanvasProvider;

/**
 * ISBNコードの情報を保持します。実質はEAN13と同じです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class ISBNBean extends UPCEANBean {
	public ISBNBean() {
		this.setHeight(14);
		this.setModuleWidth(.33);
		this.setQuietZone(5);
		this.setFontSize(3.7);
		this.setMsgPosition(HumanReadablePlacement.HRP_BOTTOM);
	}

	public UPCEANLogicImpl createLogicImpl() {
		return new EAN13LogicImpl(getChecksumMode());
	}

	public void generateBarcode(CanvasProvider canvas, String msg) {
		if ((msg == null) || (msg.length() == 0)) {
			throw new NullPointerException("Parameter msg must not be empty");
		}

		ClassicBarcodeLogicHandler handler = new ISBNCanvasLogicHandler(this, new Canvas(canvas));

		UPCEANLogicImpl impl = createLogicImpl();
		impl.generateBarcodeLogic(handler, msg);
	}
}