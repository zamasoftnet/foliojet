package jp.cssj.test.unit.misc;

import java.net.URI;

import net.zamasoft.zstream.resolver.restricted.RestrictedSourceResolver;
import junit.framework.TestCase;

public class TestRestrictedSourceResolver extends TestCase {
	public void testEquals1() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x%2A)?x=%3f&a=?"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x*)?x=?&a=%3f"));
		b = b.replaceAll("\\*", "%2A");
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testEquals2() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3fx=?&a=?%2a"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?&a=%3f*"));
		b = b.replaceAll("\\*", "%2A");
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testEquals3() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(%78%29%3fx=?&a=?#k%2Akk"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?&a=%3f#k*kk"));
		b = b.replaceAll("\\*", "%2A");
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testNotEquals1() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=%3f&a=?"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=?&a%3D%3f"));
		b = b.replaceAll("\\*", "%2A");
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals2() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3fx=?&a=?"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3FX%3D?&a=%3f"));
		b = b.replaceAll("\\*", "%2A");
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals3() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(%78%29%3fx=?&a=?#kkk"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?%26a=%3f#kkk"));
		b = b.replaceAll("\\*", "%2A");
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals4() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(*%78%29%3fx=?&a=?#k%2Akk"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(*x)%3Fx%3D?&a=%3f#k*kk"));
		b = b.replaceAll("\\*", "%2A");
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals5() throws Exception {
		String a = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(*%78%29%3fx=?&a=?#k%2Akk"));
		String b = RestrictedSourceResolver.toKey(URI
				.create("http://test.com/(%2Ax)%3Fx%3D?&a=%3f#k*kk"));
		b = b.replaceAll("\\*", "%2A");
		assertFalse(a + "," + b, a.equals(b));
	}
}
