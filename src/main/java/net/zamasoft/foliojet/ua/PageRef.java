package net.zamasoft.foliojet.ua;

import java.net.URI;
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

/**
 * ページ参照用のデータです。
 * 
 * @author MIYABE Tatsuhiko
 */
public class PageRef {

	private final List<Section> sectionStack = new ArrayList<Section>();

	private final Map<URI, List<Fragment>> uriToFragments = new HashMap<URI, List<Fragment>>();

	private final Map<URI, int[]> uriToSeq = new HashMap<URI, int[]>();

	/**
	 * LAYOUTパスを跨ぐたびに{@link #reset()}でインクリメントする世代番号。
	 * 参照先idが今回パスでまだ訪問されていない(意図的な、1パス前の値を
	 * 読む forward-reference)場合と、2パス以上前の値が{@code
	 * uriToFragments}に取り残された(重複idの出現数がパスをまたいで
	 * 減った等の)stale状態を区別するために使う({@link #getFragments}
	 * 参照)。
	 */
	private int generation = 0;

	public PageRef() {
		this.sectionStack.add(new Section(null, null, null));
	}

	public void reset() {
		++this.generation;
		while (this.sectionStack.size() > 1) {
			this.sectionStack.remove(this.sectionStack.size() - 1);
		}
		Section section = (Section) this.sectionStack.get(0);
		section.reset();
		this.uriToSeq.clear();
	}

	/**
	 * フラグメントを追加します。
	 *
	 * @param uri
	 * @param counters
	 */
	public void addFragment(URI uri, Counter[] counters) {
		this.addFragment(uri, counters, null);
	}

	/**
	 * フラグメントを追加します。
	 *
	 * @param uri
	 * @param counters
	 * @param text 参照先要素の描画テキスト({@code target-text()}用、無ければ{@code null})
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
	 * セクションを開始します。
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
	 * セクションを終了します。
	 */
	public void endSection() {
		assert this.sectionStack.size() > 1;
		this.sectionStack.remove(this.sectionStack.size() - 1);
	}

	/**
	 * 追加済みの最初のフラグメントを返します。
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
	 * 追加済みの全てのフラグメントを返します。2世代以上前の(重複idの
	 * 出現数がパスをまたいで減った等でstaleになった)フラグメントは
	 * ここで遅延プルーニングして除去する。
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
	 * 現在の世代番号({@link #reset()}のたびに1増加)。収束性チェック
	 * (target-counter系が最終パスまでに確定したか)に使う。
	 */
	public int getGeneration() {
		return this.generation;
	}

	/**
	 * 目次をXML形式で出力します。
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
				handler.endElement(XHTML.LI_ELEM.uri, XHTML.LI_ELEM.lName, XHTML.LI_ELEM.qName);

				handler.endElement(XHTML.A_ELEM.uri, XHTML.A_ELEM.lName, XHTML.A_ELEM.qName);
			}
			toSAX(handler, counter, type, attsi, child);
		}
		handler.endElement(XHTML.UL_ELEM.uri, XHTML.UL_ELEM.lName, XHTML.UL_ELEM.qName);
	}

	/**
	 * フラグメントです。
	 * 
	 * @author MIYABE Tatsuhiko
	 */
	public static class Fragment {
		public int uid;

		public URI uri;

		public Counter[] counters;

		/** 参照先要素の描画テキスト({@code target-text()}用)。無ければ{@code null}。 */
		public String text;

		/** このフラグメントが書き込まれた{@link PageRef}の世代番号。 */
		public int generation;

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
	 * セクションです。
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
