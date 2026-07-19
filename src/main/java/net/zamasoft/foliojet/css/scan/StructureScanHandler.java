package net.zamasoft.foliojet.css.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;

import net.zamasoft.foliojet.ua.SelectorFacts;
import net.zamasoft.foliojet.xml.XMLHandler;

/**
 * {@code STRUCTURE_SCAN}パス(実レイアウトを組まない軽量な事前走査)の
 * 終端ハンドラです。{@code :last-child}/{@code :only-child}/{@code :empty}/
 * {@code :nth-last-child()}/{@code :nth-last-of-type()}はいずれもCSS
 * カスケードを一切必要としない、DOM構造(親子・兄弟関係と要素名)だけで
 * 決まる疑似クラスのため、{@link net.zamasoft.foliojet.css.CSSProcessor}
 * (スタイル解決・ボックス構築)を経由せず、この軽量な専用walkerだけで
 * 解決する(docs/PLAN.md「2パス制御モード」の設計確定 v3参照)。
 * <p>
 * {@code display:none}の部分木も対象に含める(セレクタは要素にマッチし、
 * ボックスにはマッチしないため)。バッファするのは「1つの親の直接の子の
 * 列(ElementKeyと要素名のみ)」で、親が閉じた時点で末尾からの位置を確定
 * させてから捨てる——内容(テキスト・属性値等)は一切保持しないため、
 * 保持量は「1つの親の子の数」に比例するだけで、部分木のサイズには
 * 比例しない。
 * </p>
 * <p>
 * ElementKeyの採番は{@code CSSProcessor}と同じ規約(文書順に0始まりで
 * 漏れなく増分、擬似要素は対象外)で行い、両者が同じ入力を同じ順序で
 * 走査する限り値が一致する({@code CSSProcessor}側もdisplay:none/
 * インラインオブジェクト内部の要素についてキー空間だけは消費するよう
 * 修正済み)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class StructureScanHandler extends DefaultHandler2 implements XMLHandler {
	private final SelectorFacts facts;

	private long nextElementKey = 0;

	private Frame current = null;

	private static final class ChildEntry {
		final long elementKey;
		final String uri;
		final String lName;

		ChildEntry(long elementKey, String uri, String lName) {
			this.elementKey = elementKey;
			this.uri = uri;
			this.lName = lName;
		}
	}

	private static final class Frame {
		final Frame parent;
		final long elementKey;
		final List<ChildEntry> children = new ArrayList<ChildEntry>();
		boolean sawContent = false;

		Frame(Frame parent, long elementKey) {
			this.parent = parent;
			this.elementKey = elementKey;
		}
	}

	public StructureScanHandler(SelectorFacts facts) {
		this.facts = facts;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) {
		long key = this.nextElementKey++;
		if (this.current != null) {
			this.current.sawContent = true;
			this.current.children.add(new ChildEntry(key, uri, lName));
		}
		this.current = new Frame(this.current, key);
	}

	public void endElement(String uri, String lName, String qName) {
		Frame frame = this.current;
		if (frame == null) {
			// 開始タグより先に終了タグが来ることは無い想定だが、
			// 防御的にガードしておく
			return;
		}
		if (!frame.sawContent) {
			this.facts.setEmpty(frame.elementKey);
		}

		int n = frame.children.size();
		Map<String, List<ChildEntry>> byType = new HashMap<String, List<ChildEntry>>();
		for (int i = 0; i < n; ++i) {
			ChildEntry child = frame.children.get(i);
			int positionFromEnd = n - i;
			this.facts.setPositionFromEnd(child.elementKey, positionFromEnd);
			if (positionFromEnd == 1) {
				this.facts.setLastChild(child.elementKey);
			}
			String typeKey = (child.uri == null ? "" : child.uri) + "|" + child.lName;
			List<ChildEntry> sameType = byType.get(typeKey);
			if (sameType == null) {
				sameType = new ArrayList<ChildEntry>();
				byType.put(typeKey, sameType);
			}
			sameType.add(child);
		}
		for (List<ChildEntry> sameType : byType.values()) {
			int m = sameType.size();
			for (int i = 0; i < m; ++i) {
				ChildEntry child = sameType.get(i);
				int typePositionFromEnd = m - i;
				this.facts.setTypePositionFromEnd(child.elementKey, typePositionFromEnd);
				if (typePositionFromEnd == 1) {
					this.facts.setLastOfType(child.elementKey);
				}
			}
		}

		this.current = frame.parent;
	}

	public void characters(char[] ch, int start, int length) {
		if (this.current == null || this.current.sawContent) {
			return;
		}
		for (int i = 0; i < length; ++i) {
			if (!Character.isWhitespace(ch[start + i])) {
				this.current.sawContent = true;
				return;
			}
		}
	}
}
