package net.zamasoft.foliojet.impl.ua;

import net.zamasoft.foliojet.style.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
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
