package jp.cssj.homare.style.box.content;

import jp.cssj.homare.style.box.AbstractInnerTableBox;
import jp.cssj.homare.style.box.IBox;
import jp.cssj.homare.style.box.params.Types;

/**
 * ブロックの分割モードです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class BreakMode {
	/** 切断線の位置とorphans,widowsにより、残すか、切断するか、後に送るかを自動的に判断します。 */
	public static final byte AUTO = 0;

	/** 指定されたボックスの現在の位置で強制改ページします。 */
	public static final byte FORCE = 1;

	public abstract byte getType();

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

		public byte getType() {
			return AUTO;
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

		public final byte breakType;

		public ForceBreakMode(IBox box, byte breakType) {
			assert breakType == Types.PAGE_BREAK_PAGE || breakType == Types.PAGE_BREAK_COLUMN
					|| breakType == Types.PAGE_BREAK_VERSO || breakType == Types.PAGE_BREAK_RECTO;
			this.box = box;
			this.breakType = breakType;
		}

		public byte getType() {
			return FORCE;
		}

		public String toString() {
			switch (this.breakType) {
			case Types.PAGE_BREAK_PAGE:
				return "FORCE_BREAK_MODE ALWAYS";
			case Types.PAGE_BREAK_COLUMN:
				return "FORCE_BREAK_MODE COLUMN";
			case Types.PAGE_BREAK_VERSO:
				return "FORCE_BREAK_MODE LEFT";
			case Types.PAGE_BREAK_RECTO:
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

		public TableForceBreakMode(AbstractInnerTableBox box, byte breakType, int rowGroup, int row) {
			super(box, breakType);
			assert row == -1 || box.getType() == IBox.TYPE_TABLE_ROW;
			assert row != -1 || box.getType() == IBox.TYPE_TABLE_ROW_GROUP;
			this.rowGroup = rowGroup;
			this.row = row;
		}
	}
}
