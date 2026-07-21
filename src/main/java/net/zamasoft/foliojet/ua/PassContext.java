package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * 現在のパスに関係するオブジェクトを保持します。
 */
public class PassContext {
	private final SectionState sectionState = new SectionState();
	private final NamedStringState namedStringState = new NamedStringState();
	/**
	 * {@code string-set}の値に{@code content()}が含まれ、build時点では
	 * 未完成のエントリです。{@code elementKey}をキーにする
	 * ({@code AbstractVisitor.visitBox}がdraw時にボックステキストで
	 * 完成させ、ここから取り除く)。
	 */
	private final Map<Long, List<PendingStringSet>> pendingStringSets = new HashMap<Long, List<PendingStringSet>>();
	private CSSElement pageSide;
	private CounterContext counterContext = null;
	private int pageNumber = 0;

	public SectionState getSectionState() {
		return this.sectionState;
	}

	public NamedStringState getNamedStringState() {
		return this.namedStringState;
	}

	public Map<Long, List<PendingStringSet>> getPendingStringSets() {
		return this.pendingStringSets;
	}

	public CSSElement getPageSide() {
		return this.pageSide;
	}

	public void setPageSide(CSSElement pageSide) {
		this.pageSide = pageSide;
	}
	
	public void resetNonPageCounters() {
		if (this.counterContext == null) {
			return;
		}
		this.counterContext.resetNonPageCounters();
	}

	public CounterScope getCounterScope(int level, boolean create) {
		if (this.counterContext == null) {
			this.counterContext = new CounterContext();
		}
		return this.counterContext.getCounterScope(level, create);
	}

	public int getPageNumber() {
		return this.pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}
}
