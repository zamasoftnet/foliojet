package net.zamasoft.foliojet.impl.objects.mathml;

import java.io.StringReader;
import java.lang.reflect.Proxy;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.pdfg2d.gc.image.Image;

public class MathMLInlineObjectTest extends TestCase {
	public MathMLInlineObjectTest(String name) {
		super(name);
	}

	public void testCreatesImageFromMathML() throws Exception {
		MathMLInlineObject object = new MathMLInlineObject();
		XMLReader reader = XMLParsers.createXMLReader();
		reader.setContentHandler(object);
		reader.parse(new InputSource(new StringReader(
				"<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mrow><mi>x</mi><mo>=</mo><mn>1</mn></mrow></math>")));

		Image image = object.getImage(defaultUserAgent());
		assertNotNull(image);
		assertTrue("width", image.getWidth() > 0);
		assertTrue("height", image.getHeight() > 0);
	}

	public void testFactoryMatchesMathMLNamespace() {
		MathMLInlineObjectFactory factory = new MathMLInlineObjectFactory();
		assertTrue(factory.match(new CSSElement(MathMLInlineObjectFactory.URI, "math", null, null, null, null, null,
				null, null, -1)));
		assertFalse(factory.match(new CSSElement("http://www.w3.org/1999/xhtml", "math", null, null, null, null, null,
				null, null, -1)));
	}

	public void testFactoryCreatesInlineObject() {
		InlineObject object = new MathMLInlineObjectFactory().createInlineObject();
		assertNotNull(object);
		assertTrue(object instanceof MathMLInlineObject);
	}

	private static UserAgent defaultUserAgent() {
		return (UserAgent) Proxy.newProxyInstance(MathMLInlineObjectTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getFontMagnification".equals(method.getName())) {
						return 1.0;
					}
					if ("toString".equals(method.getName())) {
						return "MathMLInlineObjectTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}
}
