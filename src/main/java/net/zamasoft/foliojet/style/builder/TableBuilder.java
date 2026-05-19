package net.zamasoft.foliojet.style.builder;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;

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
