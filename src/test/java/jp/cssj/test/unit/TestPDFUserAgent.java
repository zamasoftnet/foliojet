package jp.cssj.test.unit;

import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.PrepareMode;

public class TestPDFUserAgent extends PDFUserAgent {
	private final AbstractTestCase test;

	public TestPDFUserAgent(AbstractTestCase test) {
		this.test = test;
	}

	public void prepare(PrepareMode mode) {
		super.prepare(mode);
		this.visitor = new TestPDFVisitor(this, this.test);
	}
}
