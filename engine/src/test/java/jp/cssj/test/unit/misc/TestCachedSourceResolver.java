package jp.cssj.test.unit.misc;

import java.net.URI;

import net.zamasoft.zstream.resolver.cache.CachedSourceResolver;
import junit.framework.TestCase;

public class TestCachedSourceResolver extends TestCase {
	public void testEquals1() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=%3f&a=?"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=?&a=%3f"));
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testEquals2() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3fx=?&a=?"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?&a=%3f"));
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testEquals3() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(%78%29%3fx=?&a=?#kkk"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?&a=%3f#kkk"));
		assertTrue(a + "," + b, a.equals(b));
	}

	public void testNotEquals1() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=%3f&a=?"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)?x=?&a%3D%3f"));
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals2() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3fx=?&a=?"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3FX%3D?&a=%3f"));
		assertFalse(a + "," + b, a.equals(b));
	}

	public void testNotEquals3() throws Exception {
		String a = CachedSourceResolver.toKey(URI
				.create("http://test.com/(%78%29%3fx=?&a=?#kkk"));
		String b = CachedSourceResolver.toKey(URI
				.create("http://test.com/(x)%3Fx%3D?%26a=%3f#kkk"));
		assertFalse(a + "," + b, a.equals(b));
	}
}
