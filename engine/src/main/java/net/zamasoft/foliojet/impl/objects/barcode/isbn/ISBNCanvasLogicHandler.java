/*
 * Copyright 2002-2004 Jeremias Maerki.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.zamasoft.foliojet.impl.objects.barcode.isbn;

import org.krysalis.barcode4j.BarGroup;
import org.krysalis.barcode4j.BarcodeDimension;
import org.krysalis.barcode4j.ClassicBarcodeLogicHandler;
import org.krysalis.barcode4j.HumanReadablePlacement;
import org.krysalis.barcode4j.TextAlignment;
import org.krysalis.barcode4j.impl.AbstractBarcodeBean;
import org.krysalis.barcode4j.impl.DrawingUtil;
import org.krysalis.barcode4j.output.Canvas;

/**
 * Logic Handler implementation for painting on a Canvas. This is a special
 * implementation for UPC and EAN barcodes.
 * 
 * ISBNバーコードを描画するため、EAN13のコードを少し修正しています。
 * 
 * @author Jeremias Maerki
 * @version $Id: UPCEANCanvasLogicHandler.java,v 1.2 2004/10/24 11:45:38 jmaerki
 *          Exp $
 */
public class ISBNCanvasLogicHandler implements ClassicBarcodeLogicHandler {

	private ISBNBean bcBean;

	private Canvas canvas;

	private double x = 0.0;

	private BarcodeDimension dim;

	private String msg;

	/**
	 * Main constructor.
	 * 
	 * @param bcBean
	 *            the barcode implementation class
	 * @param canvas
	 *            the canvas to paint to
	 */
	public ISBNCanvasLogicHandler(AbstractBarcodeBean bcBean, Canvas canvas) {
		if (!(bcBean instanceof ISBNBean)) {
			throw new IllegalArgumentException(
					"This LogicHandler can only be " + "used with UPC and EAN barcode implementations");
		}
		this.bcBean = (ISBNBean) bcBean;
		this.canvas = canvas;
	}

	private double getStartX() {
		if (this.bcBean.hasQuietZone()) {
			return this.bcBean.getQuietZone();
		} else {
			return 0.0;
		}
	}

	/** @see org.krysalis.barcode4j.ClassicBarcodeLogicHandler */
	public void startBarcode(String msg, String formattedMsg) {
		this.msg = msg;
		// Calculate extents
		this.dim = bcBean.calcDimensions(msg);

		this.canvas.establishDimensions(this.dim);
		this.x = getStartX();
	}

	/** @see org.krysalis.barcode4j.ClassicBarcodeLogicHandler */
	public void startBarGroup(BarGroup type, String submsg) {
		// ignore
	}

	/** @see org.krysalis.barcode4j.ClassicBarcodeLogicHandler */
	public void addBar(boolean black, int width) {
		final double w = this.bcBean.getBarWidth(width);
		if (black) {
			final double h;
			if (this.bcBean.getMsgPosition() == HumanReadablePlacement.HRP_BOTTOM) {
				h = this.bcBean.getBarHeight();
				this.canvas.drawRectWH(this.x, 0.0, w, h);
			} else if (this.bcBean.getMsgPosition() == HumanReadablePlacement.HRP_TOP) {
				h = this.bcBean.getBarHeight();
				this.canvas.drawRectWH(this.x, this.bcBean.getHeight() - h, w, h);
			} else {
				this.canvas.drawRectWH(this.x, 0, w, this.bcBean.getHeight());
			}
		}
		this.x += w;
	}

	/** @see org.krysalis.barcode4j.ClassicBarcodeLogicHandler */
	public void endBarGroup() {
		// ignore
	}

	/** @see org.krysalis.barcode4j.ClassicBarcodeLogicHandler */
	public void endBarcode() {
		if (this.bcBean.getMsgPosition() == HumanReadablePlacement.HRP_BOTTOM) {
			DrawingUtil.drawText(this.canvas, this.bcBean, this.msg, getStartX(), getStartX() + this.dim.getWidth(),
					this.bcBean.getHeight(), TextAlignment.TA_JUSTIFY);
		} else if (this.bcBean.getMsgPosition() == HumanReadablePlacement.HRP_TOP) {
			DrawingUtil.drawText(this.canvas, this.bcBean, this.msg, getStartX(), getStartX() + this.dim.getWidth(),
					this.bcBean.getHumanReadableHeight(), TextAlignment.TA_JUSTIFY);
		}
	}

}
