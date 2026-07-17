package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;

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
	 * 段組の改段(自動)です(旧 FLAGS_COLUMN の型付け)。改段は段組
	 * ボックスに到達したところで吸収され、内側では通常の自動改ページと
	 * して振る舞う。
	 */
	public static final class ColumnBreakMode extends AutoBreakMode {
		private ColumnBreakMode(IBox box) {
			super(box);
		}

		private ColumnBreakMode() {
			super();
		}

		public String toString() {
			return "COLUMN_" + super.toString();
		}
	}

	/**
	 * 自動改ページを改段として印付けます(強制改ページは breakType が
	 * 段を表すためそのまま)。
	 */
	public static BreakMode column(final BreakMode mode) {
		if (mode instanceof ColumnBreakMode) {
			return mode;
		}
		if (mode instanceof AutoBreakMode auto) {
			return auto.box == null ? new ColumnBreakMode() : new ColumnBreakMode(auto.box);
		}
		return mode;
	}

	/**
	 * 段組ボックス自身に到達した改段を吸収し、内側の通常改ページへ
	 * 戻します。
	 */
	public static BreakMode absorbColumn(final BreakMode mode, final int columnCount) {
		if (columnCount > 1 && mode instanceof ColumnBreakMode column) {
			return column.box == null ? DEFAULT_BREAK_MODE : new AutoBreakMode(column.box);
		}
		return mode;
	}

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
