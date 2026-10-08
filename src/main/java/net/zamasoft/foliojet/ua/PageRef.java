package net.zamasoft.foliojet.ua;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.vocab.XHTML;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * Data for page references.
 *
 * @author MIYABE Tatsuhiko
 */
public class PageRef {

	private final List<Section> sectionStack = new ArrayList<Section>();

	private final Map<URI, List<Fragment>> uriToFragments = new HashMap<URI, List<Fragment>>();

	private final Map<URI, int[]> uriToSeq = new HashMap<URI, int[]>();

	/**
	 * Generation number incremented by {@link #reset()} at each LAYOUT pass boundary.
	 * Distinguishes a target id not yet visited in this pass (an intentional forward reference
	 * reading the preceding pass's value) from stale values left in {@code uriToFragments}
	 * from two or more passes ago (e.g., when the number of duplicate id occurrences decreases
	 * between passes). See {@link #getFragments}.
	 */
	private int generation = 0;

	public PageRef() {
		this.sectionStack.add(new Section(null, null, null));
	}

	/**
	 * Whether the document is laid out more than once ({@code processing.pass-count} of 2 or more, or a pass the
	 * application marks with {@code processing.middle-pass}). Then the counters of elements with an id are collected
	 * without {@code processing.page-references}, so {@code target-counter()} reads the previous pass (2026-10-08).
	 */
	public static boolean laidOutMoreThanOnce(final UserAgent ua) {
		return net.zamasoft.foliojet.ua.props.UAProps.PROCESSING_PASS_COUNT.getInteger(ua) >= 2
				|| net.zamasoft.foliojet.ua.props.UAProps.PROCESSING_MIDDLE_PASS.getBoolean(ua);
	}

	/**
	 * The key of the element with {@code id} in the document whose base URI is {@code base}.
	 *
	 * <p>
	 * The key and {@link #targetURI} build the fragment the same way (2026-10-08): an id with spaces or other characters
	 * that are illegal in a URI ({@code ix_PC 遠隔操作事件} in an index made by a book tool) is quoted the same on both sides.
	 * Previously the reference side parsed the raw {@code href}, failed with "Invalid link URI", and the page number
	 * stayed empty. Both sides resolve with {@link URIHelper#resolve}, so a valid reference keeps its former key.
	 * </p>
	 */
	public static URI elementURI(final String encoding, final URI base, final String id) throws URISyntaxException {
		return resolve(encoding, base, "#" + new URI(null, null, id).getRawFragment());
	}

	/**
	 * The key a reference ({@code href} of {@code target-counter()} and friends) points to: the reference resolved
	 * against {@code base}, with its fragment percent-decoded and quoted as in {@link #elementURI}.
	 */
	public static URI targetURI(final String encoding, final URI base, final String ref) throws URISyntaxException {
		final int hash = ref.indexOf('#');
		if (hash < 0) {
			return resolve(encoding, base, ref);
		}
		String fragment = ref.substring(hash + 1);
		try {
			fragment = URIHelper.decode(fragment);
		} catch (final IllegalArgumentException e) {
			// A stray '%' ("#50%"): take the fragment as written
		}
		return resolve(encoding, base, ref.substring(0, hash) + "#" + new URI(null, null, fragment).getRawFragment());
	}

	private static URI resolve(final String encoding, final URI base, final String href) throws URISyntaxException {
		return base == null ? new URI(href) : URIHelper.resolve(encoding, base, href);
	}

	public void reset() {
		++this.generation;
		this.unconverged = false;
		while (this.sectionStack.size() > 1) {
			this.sectionStack.remove(this.sectionStack.size() - 1);
		}
		Section section = (Section) this.sectionStack.get(0);
		section.reset();
		this.uriToSeq.clear();
	}

	/**
	 * Adds a fragment.
	 *
	 * @param uri
	 * @param counters
	 */
	public void addFragment(URI uri, Counter[] counters) {
		this.addFragment(uri, counters, null);
	}

	/**
	 * Flag detecting that the final pass <b>has not actually converged</b> (2026-08-02).
	 * "A forward reference read the preceding pass's value" alone does not mean nonconvergence:
	 * if the value matches the preceding pass, the result is correct.
	 * Only when the value read <b>changes</b> within the same pass does the reference output
	 * an incorrect value.
	 */
	private boolean unconverged = false;

	/**
	 * Whether a value read by a forward reference in the final pass changes within that pass
	 * (= whether pass-count should be increased).
	 */
	public boolean isUnconverged() {
		return this.unconverged;
	}

	/**
	 * Whether <b>what a forward reference read</b> changes (convergence check, 2026-10-04).
	 * Compares only counters that were read, and body text only when {@code target-text()} read it.
	 * Previously compared all counters and body text, so a change between passes to a counter
	 * not read by the reference, such as total pages, reported nonconvergence even for a simple
	 * table of contents.
	 */
	private static boolean changed(final Fragment f, final Counter[] after, final String textAfter) {
		if (f.staleText && !java.util.Objects.equals(f.text, textAfter)) {
			return true;
		}
		if (f.staleCounters != null) {
			for (final String name : f.staleCounters) {
				if (counterValue(f.counters, name) != counterValue(after, name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int counterValue(final Counter[] counters, final String name) {
		if (counters != null) {
			for (final Counter counter : counters) {
				if (counter.name.equalsIgnoreCase(name)) {
					return counter.value;
				}
			}
		}
		return 0;
	}

	/**
	 * Adds a fragment.
	 *
	 * @param uri
	 * @param counters
	 * @param text rendered text of the target element (for {@code target-text()}; {@code null} if absent)
	 */
	public void addFragment(URI uri, Counter[] counters, String text) {
		int[] seq = (int[]) this.uriToSeq.get(uri);
		if (seq == null) {
			seq = new int[] { 1 };
			this.uriToSeq.put(uri, seq);
		} else {
			seq[0]++;
		}
		List<Fragment> list = this.uriToFragments.computeIfAbsent(uri, k -> new ArrayList<Fragment>());
		for (Iterator<Fragment> i = list.iterator(); i.hasNext();) {
			Fragment f = i.next();
			if (f.uid == seq[0]) {
				if (f.isStale() && changed(f, counters, text)) {
					// The preceding pass's value has already been read in this pass, and the value read
					// has changed = that reference outputs an incorrect value
					this.unconverged = true;
				}
				f.clearStale();
				f.counters = counters;
				f.text = text;
				f.generation = this.generation;
				return;
			}
		}
		Fragment fragment = new Fragment(seq[0], uri, counters);
		fragment.text = text;
		fragment.generation = this.generation;
		list.add(fragment);
	}

	/**
	 * Starts a section.
	 *
	 * @param uri
	 * @param title
	 * @param counters
	 */
	public void startSection(URI uri, String title, Counter[] counters) {
		Section section = (Section) this.sectionStack.get(this.sectionStack.size() - 1);
		Section newEntry = section.add(uri, title, counters);
		this.sectionStack.add(newEntry);
	}

	/**
	 * Ends a section.
	 */
	public void endSection() {
		assert this.sectionStack.size() > 1;
		this.sectionStack.remove(this.sectionStack.size() - 1);
	}

	/**
	 * Returns the first added fragment.
	 *
	 * @param uri
	 * @return
	 */
	public Fragment getFragment(URI uri) {
		Collection<?> col = this.getFragments(uri);
		if (col == null || col.isEmpty()) {
			return null;
		}
		return (Fragment) col.iterator().next();
	}

	/**
	 * Read view for repeated content. Does not delete old references; records only final-pass
	 * convergence checks. Does not expose mutable Fragment or Counter objects to callers.
	 */
	public CounterView counterView(final boolean lastPass) {
		return (uri, name, all) -> {
			final List<Integer> values = new ArrayList<Integer>();
			final List<Fragment> fragments = this.uriToFragments.get(uri);
			if (fragments != null) {
				for (final Fragment fragment : fragments) {
					if (fragment.generation < this.generation - 1) {
						continue;
					}
					if (lastPass && fragment.generation < this.generation) {
						fragment.markStaleCounter(name);
					}
					values.add(fragment.getCounterValue(name));
					if (!all) {
						break;
					}
				}
			}
			return List.copyOf(values);
		};
	}

	/** Reads only counter values from the first reference or all references. */
	@FunctionalInterface
	public interface CounterView {
		List<Integer> counters(URI uri, String name, boolean all);
	}

	/**
	 * Returns all added fragments. Lazily prunes fragments from two or more generations ago
	 * (stale, e.g., because the number of duplicate id occurrences decreased between passes).
	 *
	 * @param uri
	 * @return
	 */
	public Collection<?> getFragments(URI uri) {
		List<Fragment> list = this.uriToFragments.get(uri);
		if (list != null && !list.isEmpty()) {
			list.removeIf(f -> f.generation < this.generation - 1);
		}
		return list;
	}

	/**
	 * Current generation number (increases by one on each {@link #reset()}).
	 * Used to check convergence (whether target-counter values are finalized by the final pass).
	 */
	public int getGeneration() {
		return this.generation;
	}

	/**
	 * Outputs the table of contents as XML.
	 *
	 * @param handler
	 * @param counter
	 * @param type
	 * @throws SAXException
	 */
	public void toSAX(ContentHandler handler, String counter, short type) throws SAXException {
		AttributesImpl attsi = new AttributesImpl();
		attsi.addAttribute(XHTML.CLASS_ATTR.uri, XHTML.CLASS_ATTR.lName, XHTML.CLASS_ATTR.qName, "CDATA", "cssj-toc");
		toSAX(handler, counter, type, attsi, (Section) this.sectionStack.get(0));
	}

	private static void toSAX(ContentHandler handler, String counter, short type, AttributesImpl attsi, Section entry)
			throws SAXException {
		List<Section> children = entry.getChildren();
		if (children == null) {
			return;
		}
		handler.startElement(XHTML.UL_ELEM.uri, XHTML.UL_ELEM.lName, XHTML.UL_ELEM.qName, attsi);
		attsi.clear();
		for (int i = 0; i < children.size(); ++i) {
			Section child = (Section) children.get(i);
			if (child.title != null) {
				handler.startElement(XHTML.LI_ELEM.uri, XHTML.LI_ELEM.lName, XHTML.LI_ELEM.qName, attsi);
				XHTML.HREF_ATTR.addValue(attsi, child.uri.toString());
				Constants.XLINK_HREF_ATTR.addValue(attsi, child.uri.toString());
				handler.startElement(XHTML.A_ELEM.uri, XHTML.A_ELEM.lName, XHTML.A_ELEM.qName, attsi);
				attsi.clear();

				XHTML.CLASS_ATTR.addValue(attsi, "cssj-title");
				handler.startElement(XHTML.SPAN_ELEM.uri, XHTML.SPAN_ELEM.lName, XHTML.SPAN_ELEM.qName, attsi);
				attsi.clear();
				char[] title = child.title.toCharArray();
				handler.characters(title, 0, title.length);
				handler.endElement(XHTML.SPAN_ELEM.uri, XHTML.SPAN_ELEM.lName, XHTML.SPAN_ELEM.qName);

				XHTML.CLASS_ATTR.addValue(attsi, "cssj-page");
				handler.startElement(XHTML.SPAN_ELEM.uri, XHTML.SPAN_ELEM.lName, XHTML.SPAN_ELEM.qName, attsi);
				attsi.clear();
				char[] page = GeneratedValueUtils.format(child.getCounterValue(counter), type).toCharArray();
				handler.characters(page, 0, page.length);
				handler.endElement(XHTML.SPAN_ELEM.uri, XHTML.SPAN_ELEM.lName, XHTML.SPAN_ELEM.qName);
				handler.endElement(XHTML.A_ELEM.uri, XHTML.A_ELEM.lName, XHTML.A_ELEM.qName);

				handler.endElement(XHTML.LI_ELEM.uri, XHTML.LI_ELEM.lName, XHTML.LI_ELEM.qName);
			}
			toSAX(handler, counter, type, attsi, child);
		}
		handler.endElement(XHTML.UL_ELEM.uri, XHTML.UL_ELEM.lName, XHTML.UL_ELEM.qName);
	}

	/**
	 * Fragment.
	 *
	 * @author MIYABE Tatsuhiko
	 */
	public static class Fragment {
		public int uid;

		public URI uri;

		public Counter[] counters;

		/** Rendered text of the target element (for {@code target-text()}). {@code null} if absent. */
		public String text;

		/** Generation number of {@link PageRef} when this fragment was written. */
		public int generation;

		/**
		 * Names (lowercase) of counters read in the final pass before this pass's values were written
		 * (= forward references), and whether body text was read.
		 * Used by {@link PageRef#isUnconverged()}.
		 */
		private java.util.Set<String> staleCounters;

		private boolean staleText;

		/** Records that a forward reference read this counter. */
		public void markStaleCounter(final String name) {
			if (this.staleCounters == null) {
				this.staleCounters = new java.util.HashSet<String>(2);
			}
			this.staleCounters.add(name.toLowerCase(java.util.Locale.ROOT));
		}

		/** Records that a forward reference read body text ({@code target-text()}). */
		public void markStaleText() {
			this.staleText = true;
		}

		/** Whether marked as read by a forward reference. */
		public boolean isStale() {
			return this.staleText || this.staleCounters != null;
		}

		void clearStale() {
			this.staleCounters = null;
			this.staleText = false;
		}

		protected Fragment(int uid, URI uri, Counter[] counters) {
			this.uid = uid;
			this.counters = counters;
			this.uri = uri;
		}

		public int getCounterValue(String name) {
			if (this.counters == null) {
				return 0;
			}
			for (int i = 0; i < this.counters.length; ++i) {
				Counter counter = this.counters[i];
				if (counter.name.equalsIgnoreCase(name)) {
					return counter.value;
				}
			}
			return 0;
		}
	}

	/**
	 * Section.
	 *
	 * @author MIYABE Tatsuhiko
	 */
	static class Section extends Fragment {
		public String title;

		private List<Section> children = null;

		private int position = 0;

		Section(URI uri, String title, Counter[] counters) {
			super(-1, uri, counters);
			this.title = title;
		}

		public Section add(URI uri, String title, Counter[] counters) {
			if (this.children == null) {
				this.children = new ArrayList<Section>();
			}
			Section section;
			if (this.position < this.children.size()) {
				section = (Section) this.children.get(this.position);
				section.uri = uri;
				section.title = title;
				section.counters = counters;
			} else {
				section = new Section(uri, title, counters);
				this.children.add(section);
			}
			++this.position;
			return section;
		}

		public void reset() {
			this.position = 0;
			if (this.children != null) {
				for (int i = 0; i < this.children.size(); ++i) {
					((Section) this.children.get(i)).reset();
				}
			}
		}

		public List<Section> getChildren() {
			return this.children;
		}
	}
}
