package net.zamasoft.foliojet.ua;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * Verifies the fix for {@link PageRef}'s mixed-generation bug: when duplicate id occurrences decreased
 * across passes, stale old Fragments remained in {@link PageRef#getFragments(URI)}.
 * Fixed when reusing PageRef to implement target-counter()/target-counters().
 */
public class PageRefGenerationTest extends TestCase {
	private static Counter[] counters(int page) {
		return new Counter[] { new Counter("page", page) };
	}

	/** Fragments from the preceding pass remain readable (intentional forward references). */
	public void testOnePassBehindFragmentIsKept() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#x");

		pageRef.reset(); // Start pass 1.
		pageRef.addFragment(uri, counters(3));

		pageRef.reset(); // Start pass 2; #x has not yet been visited.
		PageRef.Fragment frag = pageRef.getFragment(uri);
		assertNotNull(frag);
		assertEquals(3, frag.getCounterValue("page"));
	}

	/** Fragments from two or more passes ago are pruned as stale. */
	public void testTwoPassesStaleFragmentIsPruned() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#x");

		pageRef.reset(); // Pass 1.
		pageRef.addFragment(uri, counters(3));

		pageRef.reset(); // Pass 2; #x remains unvisited (only the value from pass 1).
		pageRef.reset(); // Pass 3; still unvisited -> the pass 1 value is two generations old and stale.

		PageRef.Fragment frag = pageRef.getFragment(uri);
		assertNull(frag);
	}

	/**
	 * Verifies that when duplicate id occurrences decrease across passes, excess old Fragments do not
	 * contaminate {@link PageRef#getFragments(URI)} (the dedup path for target-counters()).
	 */
	public void testShrinkingDuplicateIdCountPrunesOrphans() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#d");

		pageRef.reset(); // Pass 1: #d occurs five times.
		for (int i = 1; i <= 5; ++i) {
			pageRef.addFragment(uri, counters(i));
		}

		pageRef.reset(); // Pass 2: #d occurs only three times (simulating a structural change).
		for (int i = 1; i <= 3; ++i) {
			pageRef.addFragment(uri, counters(i * 10));
		}
		// At this point, uid4,5 are indistinguishable from entries not yet visited in this pass,
		// so they remain (the same pattern as intentional forward references). Only upon entering pass 3
		// are they confirmed to be two generations old and eligible for pruning.
		pageRef.reset(); // Pass 3.

		Collection<?> frags = pageRef.getFragments(uri);
		assertNotNull(frags);
		assertEquals("2世代以上前のuid4,5は除去され、今回の3件のみ残るはず", 3, frags.size());
		for (Iterator<?> i = frags.iterator(); i.hasNext();) {
			PageRef.Fragment f = (PageRef.Fragment) i.next();
			assertTrue("stale(1桁台)な値が残っていないこと", f.getCounterValue("page") >= 10);
		}
	}
}
