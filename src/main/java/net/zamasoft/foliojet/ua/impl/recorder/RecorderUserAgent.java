package net.zamasoft.foliojet.ua.impl.recorder;

import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;

public class RecorderUserAgent extends AbstractUserAgent {
	private FontManagerImpl fontManager;

	public FontManager getFontManager() {
		if (this.fontManager == null) {
			this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
		}
		return this.fontManager;
	}

	public void meta(String name, String content) {
		// ignore
	}

	public GC nextPage() {
		final GC gc = new RecorderGC(this.fontManager);
		return gc;
	}

	public Visitor getVisitor(GC gc) {
		return new NopVisitor(this);
	}
}
