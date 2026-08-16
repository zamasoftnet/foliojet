package net.zamasoft.foliojet.layout.part;

import java.lang.reflect.Method;

import junit.framework.TestCase;

/** 分割後の表で行数が元より増える場合にも、罫線配列のコピーを有界に保つ。 */
public class TableCollapsedBordersCopyTest extends TestCase {

	public TableCollapsedBordersCopyTest(final String name) {
		super(name);
	}

	public void testCopyHeadLeavesExpandedTailEmpty() throws Exception {
		final Object[] source = { "a", "b" };
		final Object[] destination = new Object[3];
		copy("copyHead", source, destination);
		assertEquals("a", destination[0]);
		assertEquals("b", destination[1]);
		assertNull(destination[2]);
	}

	public void testCopyTailLeavesExpandedHeadEmpty() throws Exception {
		final Object[] source = { "a", "b" };
		final Object[] destination = new Object[3];
		copy("copyTail", source, destination);
		assertNull(destination[0]);
		assertEquals("a", destination[1]);
		assertEquals("b", destination[2]);
	}

	private static void copy(final String name, final Object source, final Object destination) throws Exception {
		final Method method = TableCollapsedBorders.class.getDeclaredMethod(name, Object.class, Object.class);
		method.setAccessible(true);
		method.invoke(null, source, destination);
	}
}
