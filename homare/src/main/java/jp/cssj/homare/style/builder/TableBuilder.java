package jp.cssj.homare.style.builder;

import jp.cssj.homare.style.box.AbstractContainerBox;
import jp.cssj.homare.style.box.AbstractInnerTableBox;
import jp.cssj.homare.style.box.impl.TableBox;

/**
 * テーブルを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface TableBuilder {
	public TableBox getTableBox();

	public void startInnerTable(AbstractInnerTableBox box);

	public void endInnerTable();

	public Builder newContext(AbstractContainerBox box);

	public boolean isOnePass();
}
