package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.params.PageBreakMode;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.IBox;

/**
 * ブロックの分割モードです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class BreakMode {
	/**
	 * 指定線上での自動改ページです。
	 */
	public static class AutoBreakMode extends BreakMode {
		public final IBox box;

		public AutoBreakMode(IBox box) {
			assert box != null;
			this.box = box;
		}

		private AutoBreakMode() {
			this.box = null;
		}

		public String toString() {
			if (this.box == null) {
				return "AUTO_BREAK_MODE";
			}
			return "AUTO_BREAK_MODE/" + this.box.getParams().element;
		}
	};

	public static AutoBreakMode DEFAULT_BREAK_MODE = new AutoBreakMode();

	/**
	 * 特定の場所での強制改ページです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class ForceBreakMode extends BreakMode {
		public final IBox box;

		public final PageBreakMode breakType;

		public ForceBreakMode(IBox box, PageBreakMode breakType) {
			assert breakType == PageBreakMode.PAGE || breakType == PageBreakMode.COLUMN
					|| breakType == PageBreakMode.VERSO || breakType == PageBreakMode.RECTO;
			this.box = box;
			this.breakType = breakType;
		}

		public String toString() {
			switch (this.breakType) {
			case PageBreakMode.PAGE:
				return "FORCE_BREAK_MODE ALWAYS";
			case PageBreakMode.COLUMN:
				return "FORCE_BREAK_MODE COLUMN";
			case PageBreakMode.VERSO:
				return "FORCE_BREAK_MODE LEFT";
			case PageBreakMode.RECTO:
				return "FORCE_BREAK_MODE RIGHT";
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * テーブル内での強制改ページです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class TableForceBreakMode extends ForceBreakMode {
		public final int rowGroup, row;

		public TableForceBreakMode(AbstractInnerTableBox box, PageBreakMode breakType, int rowGroup, int row) {
			super(box, breakType);
			assert row == -1 || box.getType() == BoxType.TABLE_ROW;
			assert row != -1 || box.getType() == BoxType.TABLE_ROW_GROUP;
			this.rowGroup = rowGroup;
			this.row = row;
		}
	}
}
