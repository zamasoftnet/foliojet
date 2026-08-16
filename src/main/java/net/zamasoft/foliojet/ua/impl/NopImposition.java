package net.zamasoft.foliojet.ua.impl;

import jp.cssj.cti2.CTISession;
import net.zamasoft.foliojet.layout.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class NopImposition extends AbstractImposition {
	public NopImposition(UserAgent ua) {
		super(ua);
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		this.ua.checkAbort(CTISession.ABORT_FORCE);
		this.ua.noteProgress();
		return null;
	}

	public void closePage() throws GraphicsException {
		// ignore
	}

}
