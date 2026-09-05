package net.zamasoft.foliojet.css.style.running;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.ua.PageAssignmentState;
import net.zamasoft.foliojet.ua.PendingStringSet;

/**
 * runningとstring-setの配置待ちアンカー、およびrunningの三候補を所有します。
 * ログ上のtokenはorderだけを持ち、確定した代入のpayloadはここから解放します。
 */
public final class RunningRegistry {
	private static final class Pending {
		final long order;
		RunningTemplate template;
		List<PendingStringSet> strings = List.of();
		List<String> clears = List.of();
		java.util.function.Consumer<StringBuilder> stringSource;
		boolean before = true;

		Pending(final long order) {
			this.order = order;
		}
	}

	/** ページビルダーが確定した所属頁・先頭の事実をvisitorへ渡すメタデータです。 */
	public record Placement(long order, RunningTemplate template, List<PendingStringSet> strings,
			IBox box, boolean beginsPage, String sourceText, List<String> clears) {
		public Placement {
			strings = List.copyOf(strings);
			clears = List.copyOf(clears);
		}

		public Placement(final long order, final RunningTemplate template, final List<PendingStringSet> strings,
				final IBox box, final boolean beginsPage, final String sourceText) {
			this(order, template, strings, box, beginsPage, sourceText, List.of());
		}

		public Placement(final long order, final RunningTemplate template, final List<PendingStringSet> strings,
				final IBox box, final boolean beginsPage) {
			this(order, template, strings, box, beginsPage, null);
		}
	}

	private record Step(IBox box, boolean outsideFlow, boolean exit, int offset, int count) {
	}

	private final PageAssignmentState<RunningTemplate> state = new PageAssignmentState<RunningTemplate>();
	private final Map<Long, Pending> pending = new HashMap<Long, Pending>();
	private final Map<Long, List<Long>> boxes = new HashMap<Long, List<Long>>();
	private final TreeMap<Integer, List<Long>> characters = new TreeMap<Integer, List<Long>>();
	private long order;
	private long assigned;
	private long rejected;

	public PageAssignmentState<RunningTemplate> state() {
		return this.state;
	}

	/** パスを通じて単調増加する、疑似要素やEPUB章境界にも衝突しない文書順です。 */
	public long nextOrder() {
		return this.order++;
	}

	public void complete(final long order, final RunningTemplate template) {
		this.pending.computeIfAbsent(order, Pending::new).template = template;
	}

	public void reject(final long order) {
		this.pending.remove(order);
		++this.rejected;
	}

	public void strings(final long order, final List<PendingStringSet> strings) {
		this.pending.computeIfAbsent(order, Pending::new).strings = List.copyOf(strings);
	}

	/** 配置された頁で削除する名前を、通常の代入と同じアンカーへ載せます。 */
	public void clear(final long order, final List<String> names) {
		this.pending.computeIfAbsent(order, Pending::new).clears = List.copyOf(names);
	}

	/** 代入元のテキストと配置先を分離します。参照はpendingの寿命内だけ保持します。 */
	public void strings(final long order, final List<PendingStringSet> strings,
			final java.util.function.Consumer<StringBuilder> source) {
		this.strings(order, strings);
		this.pending.get(order).stringSource = source;
	}

	public void bindBox(final long order, final long sourceAnchor) {
		this.pending.computeIfAbsent(order, Pending::new);
		this.boxes.computeIfAbsent(sourceAnchor, key -> new ArrayList<Long>()).add(order);
	}

	public void bindCharacters(final long order, final int offset, final boolean before) {
		this.pending.computeIfAbsent(order, Pending::new).before = before;
		this.characters.computeIfAbsent(offset, key -> new ArrayList<Long>()).add(order);
	}

	/**
	 * 切断・移送後のページ木を論理順で走査し、座標を参照せず配置をcommitします。
	 * 継続断片は新しい開始アンカーを持たず、absolute/fixedとその子は先頭になりません。
	 * 文書のラッパー(html/body)と匿名箱は先頭判定を消費しません。
	 */
	public List<Placement> commitPage(final IBox page) {
		return this.placements(page, true);
	}

	/** 白紙判定用。pendingや文字範囲を消費せず、配置された代入だけを返します。 */
	public List<Placement> previewPage(final IBox page) {
		return this.placements(page, false);
	}

	private List<Placement> placements(final IBox page, final boolean commit) {
		if (this.pending.isEmpty()) {
			if (commit) {
				this.boxes.clear();
				this.characters.clear();
			}
			return List.of();
		}
		final List<Placement> placements = new ArrayList<Placement>();
		final Deque<Step> work = new ArrayDeque<Step>();
		work.push(new Step(page, false, false, -1, -1));
		boolean content = false;
		while (!work.isEmpty()) {
			final Step step = work.pop();
			final IBox box = step.box();
			final boolean outside = step.outsideFlow() || box instanceof IAbsoluteBox;
			if (step.count() >= 0) {
				// この箱に実在するText/Controlだけを回収する。子や別配置の穴を跨がない。
				if (step.offset() >= 0 && step.count() > 0) {
					final var range = this.characters.subMap(step.offset(), true,
							step.offset() + step.count(), false);
					for (final var entry : range.entrySet()) {
						this.place(entry.getValue(), box,
								!outside && !content && entry.getKey() == step.offset(), placements, commit);
					}
					if (commit) {
						range.clear();
					}
				}
				if (!outside && step.count() > 0) {
					content = true;
				}
				continue;
			}
			final var element = box.getParams().element;
			final boolean markup = element != null && element.elementKey() >= 0
					&& !"html".equals(element.lName()) && !"body".equals(element.lName());
			if (step.exit()) {
				if (!outside && markup) {
					content = true;
				}
				continue;
			}
			this.place(commit ? this.boxes.remove(box.getAssignmentAnchor()) : this.boxes.get(box.getAssignmentAnchor()),
					box, !outside && !content, placements, commit);
			work.push(new Step(box, outside, true, -1, -1));
			if (box instanceof AbstractTextBox text) {
				final List<Object> contents = text.getLogicalContents();
				for (int i = contents.size() - 1; i >= 0; --i) {
					final Object item = contents.get(i);
					if (item instanceof net.zamasoft.pdfg2d.gc.text.Text run) {
						work.push(new Step(box, outside, false, run.getCharOffset(), run.getCharCount()));
					} else if (item instanceof net.zamasoft.pdfg2d.gc.text.layout.control.Control control) {
						// 縮退した空白や未実体化のsoft hyphenは頁先頭の内容を消費しない。
						if (control.getControlChar() == '\n' || control.getAdvance() != 0) {
							work.push(new Step(box, outside, false, control.getCharOffset(), 1));
						}
					} else if (item instanceof AbstractTextBox.Inline inline) {
						work.push(new Step(inline.box, outside, false, -1, -1));
					} else if (item instanceof IAbsoluteBox absolute) {
						work.push(new Step(absolute, true, false, -1, -1));
					}
				}
				continue;
			}
			final List<IBox> children = new ArrayList<IBox>();
			box.forEachAssignmentChild(children::add);
			for (int i = children.size() - 1; i >= 0; --i) {
				final IBox child = children.get(i);
				work.push(new Step(child, outside, false, -1, -1));
			}
		}
		placements.sort(java.util.Comparator.comparingLong(Placement::order));
		return List.copyOf(placements);
	}

	private void place(final List<Long> orders, final IBox box, final boolean beginsPage,
			final List<Placement> placements, final boolean commit) {
		if (orders == null) {
			return;
		}
		for (final long order : orders) {
			final Pending value = commit ? this.pending.remove(order) : this.pending.get(order);
			if (value != null && (value.template != null || !value.strings.isEmpty() || !value.clears.isEmpty())) {
				String sourceText = null;
				if (commit && value.stringSource != null) {
					final StringBuilder text = new StringBuilder();
					value.stringSource.accept(text);
					sourceText = text.toString();
				}
				placements.add(new Placement(order, value.template, value.strings, box,
						beginsPage && value.before, sourceText, value.clears));
			}
		}
	}

	/** visitorから、配置を確定したテンプレートを登録します。 */
	public void assign(final Placement placement) {
		for (final String name : placement.clears()) {
			this.state.clear(name, placement.order(), placement.beginsPage());
		}
		if (placement.template() != null) {
			this.state.assign(placement.template().name(), placement.template(),
					placement.order(), placement.beginsPage());
			++this.assigned;
		}
	}

	/** 診断用: 指定名で保持するentry/first/lastの候補数です(最大3)。 */
	public int retainedCandidateCount(final String name) {
		final var snapshot = this.state.snapshot(name);
		return (snapshot.entry() == null ? 0 : 1) + (snapshot.first() == null ? 0 : 1)
				+ (snapshot.last() == null ? 0 : 1);
	}

	public int pendingCount() {
		return this.pending.size();
	}

	public long assignedCount() {
		return this.assigned;
	}

	public long rejectedCount() {
		return this.rejected;
	}

	public void endPage() {
		this.state.endPage();
	}

	/** 入力終了/失敗時、出力頁を持たなかったアンカーを解放します。 */
	public void discardPending() {
		this.pending.clear();
		this.boxes.clear();
		this.characters.clear();
	}
}
