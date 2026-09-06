package jp.cssj.test.unit._0500_grid;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;

/**
 * {@code grid-template-rows: subgrid} Stage 2を座標で固定します。子孫寄与、
 * 遅延最終化、frame/gap、clamp、3段ネスト、行分割と平坦化Gridとの同値を
 * 一つのfixtureで検査します。
 */
public class GridSubgridRowsTest extends AbstractTestCase {
	public GridSubgridRowsTest(final String name) {
		super(name);
	}

	private final List<String[]> ineffective = new ArrayList<>();
	private double mainX = Double.NaN, mainY = Double.NaN;
	private double indefX = Double.NaN, indefY = Double.NaN;
	private double percentX = Double.NaN, percentY = Double.NaN;
	private double implicitX = Double.NaN, implicitY = Double.NaN;
	private double startX = Double.NaN, startY = Double.NaN;
	private double fixedX = Double.NaN, fixedY = Double.NaN;
	private double singleX = Double.NaN, singleY = Double.NaN;
	private double autoX = Double.NaN, autoY = Double.NaN;
	private double spanX = Double.NaN, spanY = Double.NaN;
	private double threeX = Double.NaN, threeY = Double.NaN;
	private double gapX = Double.NaN, gapY = Double.NaN;
	private double edgeX = Double.NaN, edgeY = Double.NaN;
	private double emptyX = Double.NaN, emptyY = Double.NaN;
	private double mixedX = Double.NaN, mixedY = Double.NaN;
	private double clampX = Double.NaN, clampY = Double.NaN;
	private double shrinkX = Double.NaN, shrinkY = Double.NaN;
	private double eqSubX = Double.NaN, eqSubY = Double.NaN;
	private double eqFlatX = Double.NaN, eqFlatY = Double.NaN;
	private double eqFrameSubX = Double.NaN, eqFrameSubY = Double.NaN;
	private double eqFrameFlatX = Double.NaN, eqFrameFlatY = Double.NaN;
	private final double[][] eqSub = new double[3][2];
	private final double[] eqFrameSub = new double[2];

	@Override
	protected void transcode() throws Exception {
		final long records = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_RECORDS.get();
		final long binds = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_BINDS.get();
		final File file = new File("files/unittest/0500-grid/subgrid-rows.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final long dRecords = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_RECORDS.get() - records;
		final long dBinds = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_BINDS.get() - binds;
		// 親rangeに吸収された録画項目はbindされず、再生時に作り直される。
		// このfixtureは78項目+祖先Gridの再構築分57=135録画(T2時点)。
		// 録画回数は観測に留め、両経路共通の項目bind数で配置の重複を検出する。
		System.err.println("[subgrid-rows] recorded=" + dRecords + " bound=" + dBinds);
		assertEquals("fixture内のgrid item数", 78, dBinds);
		assertEquals("row subgrid内の並列注だけを報告", 1, this.ineffective.size());
		assertEquals("float", this.ineffective.get(0)[0]);
		assertEquals(MessageCodeUtils.detail("2823.subgrid-rows-margin-note"), this.ineffective.get(0)[1]);
	}

	@Override
	public void message(final short code, final String[] args, final String mes) {
		super.message(code, args, mes);
		if (code == MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION) {
			this.ineffective.add(args == null ? new String[0] : args.clone());
		}
	}

	public boolean check_main_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(1, pageNumber);
			this.mainX = x;
			this.mainY = y;
			return true;
		}
		return false;
	}

	/** row 2開始=20+5、子のcontent開始=25+(border 2+padding 1)。 */
	public boolean check_main_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mainX, x, 0.1);
			assertEquals(this.mainY + 28, y, 0.1);
			return true;
		}
		return false;
	}

	/** 内側線=25+3+(30-3)+5=60。親の第3行開始と一致する。 */
	public boolean check_main_second(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mainX, x, 0.1);
			assertEquals(this.mainY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_main_parent_row2(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mainX + 110, x, 0.1);
			assertEquals(this.mainY + 25, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_main_parent_row3(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mainX + 110, x, 0.1);
			assertEquals(this.mainY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_main_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mainX, x, 0.1);
			assertEquals(this.mainY + 85, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_indef_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(2, pageNumber);
			this.indefX = x;
			this.indefY = y;
			return true;
		}
		return false;
	}

	public boolean check_indef_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.indefX, x, 0.1);
			assertEquals(this.indefY + 25, y, 0.1);
			return true;
		}
		return false;
	}

	/** 子孫寄与で確定した2行目=6pt+継承gap 5pt後。 */
	public boolean check_indef_second(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.indefX, x, 0.1);
			assertEquals(this.indefY + 36, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_indef_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.indefY + 61, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_percent_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(3, pageNumber);
			this.percentX = x;
			this.percentY = y;
			return true;
		}
		return false;
	}

	public boolean check_percent_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.percentX, x, 0.1);
			assertEquals(this.percentY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_percent_last(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.percentX, x, 0.1);
			assertEquals(this.percentY + 50, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_percent_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.percentY + 100, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_implicit_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(4, pageNumber);
			this.implicitX = x;
			this.implicitY = y;
			return true;
		}
		return false;
	}

	public boolean check_implicit_0(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.implicitX, x, 0.1);
			assertEquals(this.implicitY + 34, y, 0.1);
			return true;
		}
		return false;
	}

	/** subgrid軸に暗黙行はなく、3件とも第3行開始へclampする。 */
	public boolean check_implicit_1(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.implicitX, x, 0.1);
			assertEquals(this.implicitY + 34, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_implicit_2(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.implicitX, x, 0.1);
			assertEquals(this.implicitY + 34, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_start_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(5, pageNumber);
			this.startX = x;
			this.startY = y;
			return true;
		}
		return false;
	}

	public boolean check_start_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.startX, x, 0.1);
			assertEquals(this.startY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_start_second(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.startX, x, 0.1);
			assertEquals(this.startY + 24, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_fixed_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.fixedX = x;
			this.fixedY = y;
			assertEquals(this.startY + 64, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_fixed_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.fixedX, x, 0.1);
			assertEquals(this.fixedY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 指定height:100ptは無視され、親2行20+gap4+30=54ptへ正確に縮む。 */
	public boolean check_fixed_subgrid(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(54.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_fixed_second(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.fixedX, x, 0.1);
			assertEquals(this.fixedY + 24, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_single_frame_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(6, pageNumber);
			this.singleX = x;
			this.singleY = y;
			return true;
		}
		return false;
	}

	public boolean check_single_frame_first(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.singleX, x, 0.1);
			assertEquals(this.singleY + 3, y, 0.1);
			return true;
		}
		return false;
	}

	/** 範囲外itemはspan=1の唯一の行へclampされ、先頭itemと同じ位置になる。 */
	public boolean check_single_frame_implicit(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.singleX, x, 0.1);
			assertEquals(this.singleY + 3, y, 0.1);
			return true;
		}
		return false;
	}

	/** span=1の親子row ledgerは同じ境界で100pt/60ptへ分ける。 */
	public boolean check_split_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertEquals(0.0, x, 0.1);
		if (pageNumber == 7) {
			assertEquals(200.0, y, 0.1);
			assertEquals(100.0, box.getHeight(), 0.1);
			return false;
		}
		assertEquals(8, pageNumber);
		assertEquals(0.0, y, 0.1);
		assertEquals(60.0, box.getHeight(), 0.1);
		return true;
	}

	public boolean check_split_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(8, pageNumber);
			assertEquals(0.0, x, 0.1);
			assertEquals(60.0, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_auto_compete_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(9, pageNumber);
			this.autoX = x;
			this.autoY = y;
			return true;
		}
		return false;
	}

	public boolean check_auto_compete_child(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.autoX, x, 0.1);
			assertEquals(this.autoY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_auto_compete_sibling(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.autoX + 50, x, 0.1);
			assertEquals(this.autoY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_auto_compete_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.autoY + 30, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_span_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(10, pageNumber);
			this.spanX = x;
			this.spanY = y;
			return true;
		}
		return false;
	}

	public boolean check_span_child(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.spanX, x, 0.1);
			assertEquals(this.spanY, y, 0.1);
			assertEquals(50.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_span_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.spanY + 50, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_three_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(11, pageNumber);
			this.threeX = x;
			this.threeY = y;
			return true;
		}
		return false;
	}

	private boolean checkThree(final IBox box, final double x, final double y, final double expectedY) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.threeX, x, 0.1);
			assertEquals(this.threeY + expectedY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_three_0(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkThree(box, x, y, 3);
	}

	public boolean check_three_1(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkThree(box, x, y, 23);
	}

	public boolean check_three_2(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkThree(box, x, y, 53);
	}

	public boolean check_three_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.threeY + 69, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_gap_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(12, pageNumber);
			this.gapX = x;
			this.gapY = y;
			return true;
		}
		return false;
	}

	public boolean check_gap_normal_1(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.gapX, x, 0.1);
			assertEquals(this.gapY + 17, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_gap_explicit_1(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.gapX + 50, x, 0.1);
			assertEquals(this.gapY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_gap_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.gapY + 30, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_edge_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(13, pageNumber);
			this.edgeX = x;
			this.edgeY = y;
			return true;
		}
		return false;
	}

	public boolean check_edge_item(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.edgeX, x, 0.1);
			assertEquals(this.edgeY + 2.5, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_edge_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.edgeY + 16, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_empty_frame_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(14, pageNumber);
			this.emptyX = x;
			this.emptyY = y;
			assertEquals(7.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	/** 完全に空のsubgridはframe 7ptだけを寄与し、子gap 5ptを上積みしない。 */
	public boolean check_empty_frame_subgrid(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.emptyX, x, 0.1);
			assertEquals(this.emptyY, y, 0.1);
			assertEquals(7.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_empty_frame_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.emptyY + 7, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_mixed_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(15, pageNumber);
			this.mixedX = x;
			this.mixedY = y;
			return true;
		}
		return false;
	}

	private boolean checkMixed(final IBox box, final double x, final double y, final double expectedY) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mixedX, x, 0.1);
			assertEquals(this.mixedY + expectedY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_mixed_0(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkMixed(box, x, y, 0);
	}

	public boolean check_mixed_1(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkMixed(box, x, y, 30);
	}

	public boolean check_mixed_2(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkMixed(box, x, y, 70);
	}

	public boolean check_mixed_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.mixedY + 100, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_clamp_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(16, pageNumber);
			this.clampX = x;
			this.clampY = y;
			return true;
		}
		return false;
	}

	private boolean checkClamp(final IBox box, final double x, final double y, final double expectedY) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.clampX, x, 0.1);
			assertEquals(this.clampY + expectedY, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_clamp_negative(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkClamp(box, x, y, 0);
	}

	public boolean check_clamp_named(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkClamp(box, x, y, 12);
	}

	public boolean check_clamp_over(final IBox box, final int pageNumber, final double x, final double y) {
		return this.checkClamp(box, x, y, 34);
	}

	public boolean check_shrink_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(17, pageNumber);
			this.shrinkX = x;
			this.shrinkY = y;
			return true;
		}
		return false;
	}

	public boolean check_shrink_subgrid(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.shrinkX, x, 0.1);
			assertEquals(this.shrinkY + 80, y, 0.1);
			assertEquals(10.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_shrink_after(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.shrinkY + 90, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_margin_note_probe(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(18, pageNumber);
			return true;
		}
		return false;
	}

	public boolean check_eq_sub_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(19, pageNumber);
			this.eqSubX = x;
			this.eqSubY = y;
			assertEquals(120.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_eq_flat_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.eqFlatX = x;
			this.eqFlatY = y;
			assertEquals(120.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	private boolean rememberEq(final IBox box, final double x, final double y, final int index) {
		if (box.getType() == BoxType.BLOCK) {
			this.eqSub[index][0] = x - this.eqSubX;
			this.eqSub[index][1] = y - this.eqSubY;
			return true;
		}
		return false;
	}

	private boolean compareEq(final IBox box, final double x, final double y, final int index) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.eqSub[index][0], x - this.eqFlatX, 0.1);
			assertEquals(this.eqSub[index][1], y - this.eqFlatY, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_eq_sub_a(final IBox box, final int pageNumber, final double x, final double y) {
		return this.rememberEq(box, x, y, 0);
	}

	public boolean check_eq_sub_span(final IBox box, final int pageNumber, final double x, final double y) {
		return this.rememberEq(box, x, y, 1);
	}

	public boolean check_eq_sub_pct(final IBox box, final int pageNumber, final double x, final double y) {
		return this.rememberEq(box, x, y, 2);
	}

	public boolean check_eq_flat_a(final IBox box, final int pageNumber, final double x, final double y) {
		return this.compareEq(box, x, y, 0);
	}

	public boolean check_eq_flat_span(final IBox box, final int pageNumber, final double x, final double y) {
		return this.compareEq(box, x, y, 1);
	}

	public boolean check_eq_flat_pct(final IBox box, final int pageNumber, final double x, final double y) {
		return this.compareEq(box, x, y, 2);
	}

	public boolean check_eq_frame_sub_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.eqFrameSubX = x;
			this.eqFrameSubY = y;
			assertEquals(16.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_eq_frame_flat_parent(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.eqFrameFlatX = x;
			this.eqFrameFlatY = y;
			assertEquals(16.0, box.getHeight(), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_eq_frame_sub(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.eqFrameSub[0] = x - this.eqFrameSubX;
			this.eqFrameSub[1] = y - this.eqFrameSubY;
			return true;
		}
		return false;
	}

	public boolean check_eq_frame_flat(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.eqFrameSub[0], x - this.eqFrameFlatX, 0.1);
			assertEquals(this.eqFrameSub[1], y - this.eqFrameFlatY, 0.1);
			return true;
		}
		return false;
	}
}
