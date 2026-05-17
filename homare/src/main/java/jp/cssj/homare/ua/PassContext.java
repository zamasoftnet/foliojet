package jp.cssj.homare.ua;

import jp.cssj.homare.css.CSSElement;

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
	
	public void resetExcluidePageCountes() {
		if (this.counterContext == null) {
			return;
		}
		this.counterContext.resetExcluidePageCountes();
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
