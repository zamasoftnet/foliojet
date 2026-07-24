package jp.cssj.test.unit;

import java.awt.geom.AffineTransform;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

public class TestPDFVisitor extends PDFVisitor {
	private int pageNumber = 0;
	private final AbstractTestCase test;

	public TestPDFVisitor(UserAgent ua, AbstractTestCase test) {
		super(ua);
		this.test = test;
		Method[] methods = this.test.getClass().getMethods();
		for (int i = 0; i < methods.length; ++i) {
			Method method = methods[i];
			String name = method.getName();
			if (name.startsWith("check_")) {
				String id = name.substring(6);
				this.test.idToMethod.put(id, method);
			}
		}
	}

	public void nextPage(PDFGC gc) {
		super.nextPage(gc);
		++this.pageNumber;
	}

	public void visitBox(AffineTransform transform, IBox box, net.zamasoft.foliojet.layout.draw.Drawer drawer,
			double x, double y) {
		super.visitBox(transform, box, drawer, x, y);
		int pc = this.ua.getUAContext().getPassCount();
		if (pc > 1) {
			return;
		}

		// E-6増分3b-4: ソース再生されたボックスのelementはStructureToken
		// のことがある——共通契約StructureElement経由で読む(idも契約に含む)
		StructureElement ce = box.getParams().element;
		if (ce == null || ce.id() == null) {
			return;
		}
		Method method = (Method) this.test.idToMethod.get(ce.id());
		if (method == null) {
			// throw new RuntimeException("id=" + ce.id +
			// " の要素をテストするメソッドがありません");
			return;
		}
		boolean done = false;
		Throwable t = null;
		try {
			Object result = method.invoke(this.test,
					new Object[] { box, new Integer(this.pageNumber),
							new Double(x), new Double(y) });
			if (Boolean.TRUE.equals(result)) {
				done = true;
				this.test.done.add(ce.id());
			}
		} catch (InvocationTargetException e) {
			t = e.getCause();
		} catch (Exception e) {
			t = e;
		}
		if (t != null) {
			this.test.errors.add(t);
			System.err.println("test: " + method.getName() + " fail");
		} else if (done) {
			System.err.println("test: " + method.getName() + " OK");
		}
	}

}
