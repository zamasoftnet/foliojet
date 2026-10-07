package jp.cssj.test.unit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import junit.framework.TestCase;

public abstract class AbstractTestCase extends TestCase implements
		MessageHandler {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	protected File file;

	protected DirectSession session;

	protected TestPDFUserAgent ua;

	protected FragmentedOutput builder;

	protected String fail = null;
	
	protected final Map<String, Method> idToMethod = new HashMap<String, Method>();
	protected final Set<String> done = new HashSet<String>();
	protected final List<Throwable> errors = new ArrayList<Throwable>();

	protected AbstractTestCase(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		this.file = new File("local/unittest/pdf/" + this.getClass().getName()
				+ ".pdf");
		OutputStream out = new FileOutputStream(this.file);
		this.builder = new StreamFragmentedOutput(out);
		this.session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		this.ua = new TestPDFUserAgent(this);
		this.session.setUserAgent(this.ua);
		this.session.setResults(new SingleResult(this.builder));
		this.session.setMessageHandler(this);
		this.session.setSourceResolver(CompositeSourceResolver
				.createGenericCompositeSourceResolver());
		this.session.property("input.include", "**");
		this.session.property("input.property-pi", "true");
	}

	protected void tearDown() throws Exception {
		this.session.close();
	}

	public void message(short code, String[] args, String mes) {
		if (CTIMessageHelper.getLevel(code) == CTIMessageHelper.FATAL) {
			fail = MessageCodeUtils.toString(code, args);
			System.err.println(fail);
		}
	}

	public void testDocument() throws Exception {
		this.transcode();
		// 2026-07-25: Do not overwrite an exception under inspection with a "not executed" message.
		// If check_* throws, its id is not added to done. Previously, this always left
		// only "Test x was not executed.", losing the actual cause (such as an assertion's
		// expected-value mismatch). This was why all seven quarantined tests failed
		// with the same message.
		final StringBuilder reason = new StringBuilder();
		if (!this.errors.isEmpty()) {
			StringWriter o = new StringWriter();
			PrintWriter w = new PrintWriter(o);
			for (int i = 0; i < this.errors.size(); ++i) {
				Throwable t = (Throwable) this.errors.get(i);
				t.printStackTrace(w);
			}
			reason.append(o.toString());
		}
		for (Iterator<String> i = this.idToMethod.keySet().iterator(); i.hasNext();) {
			String id = (String) i.next();
			if (!this.done.contains(id)) {
				if (reason.length() > 0) {
					reason.append('\n');
				}
				reason.append("Test ").append(id).append(" was not executed.");
			}
		}
		if (reason.length() > 0) {
			fail = (fail == null ? "" : fail + "\n") + reason;
		}
		if (fail != null) {
			fail(fail);
		}
	}

	protected abstract void transcode() throws Exception;
}
