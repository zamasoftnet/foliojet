package jp.cssj.test.unit;

import net.zamasoft.foliojet.impl.ua.pdf.PDFUserAgent;

public class TestPDFUserAgent extends PDFUserAgent {
	private final AbstractTestCase test;

	public TestPDFUserAgent(AbstractTestCase test) {
		this.test = test;
	}

	public void prepare(byte mode) {
		super.prepare(mode);
		this.visitor = new TestPDFVisitor(this, this.test);
	}
}
