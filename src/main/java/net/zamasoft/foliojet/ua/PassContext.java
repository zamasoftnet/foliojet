package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * 現在のパスに関係するオブジェクトを保持します。
 */
public class PassContext {
	private final SectionState sectionState = new SectionState();
	private CSSElement pageSide;
	private CounterContext counterContext = null;
	private int pageNumber = 0;

	public SectionState getSectionState() {
		return this.sectionState;
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

	/**
	 * ページカウンタ以外のカウンタ状態の複製を返します(M6b)。
	 * セグメント再駆動の再開位置での巻き戻しに使います。
	 * ページカウンタは残余が新しいページで再評価されるべきため対象外です。
	 */
	public CounterScope[] snapshotNonPageCounters() {
		if (this.counterContext == null) {
			return null;
		}
		return this.counterContext.snapshotNonPageCounters();
	}

	/**
	 * snapshotNonPageCounters の結果を書き戻します。
	 */
	public void restoreNonPageCounters(final CounterScope[] snapshot) {
		if (this.counterContext == null) {
			if (snapshot == null) {
				return;
			}
			this.counterContext = new CounterContext();
		}
		this.counterContext.restoreNonPageCounters(snapshot);
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
