package jp.cssj.homare.impl.ua;

import jp.cssj.homare.style.imposition.AbstractImposition;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class NopImposition extends AbstractImposition {
	public NopImposition(UserAgent ua) {
		super(ua);
	}

	public GC nextPage() throws GraphicsException {
		// ignore
		return null;
	}

	public void closePage() throws GraphicsException {
		// ignore
	}

}
