package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 2段組みの中の浮動体(画像)まわりの、段の断片を固定します。
 *
 * <p>
 * 2026-09-11に内蔵CIDフォントの幅表を直し、全角スペース・読点・句点が
 * 全角に戻ったことで、この文書の配置が変わりました。<b>それまでは浮動体が
 * 段をまたいで切断されていました</b>——画像(85.50×114.96)の上93.60ptを
 * 左段に、残り21.36ptを右段の頭に描き、その2行だけ本文がx=286.50へ
 * 逃げていた。字送りが正しくなって画像が左段に収まり、本文はそのまま
 * 画像の右へ回り込みます。
 * </p>
 *
 * <p>
 * 段の断片は箱の分かれ方であって描画の切れ目ではありません。#bの右段は
 * 2つの箱に分かれますが、126.56+57.6 = 184.16 で隙間なく続いており、
 * 6行(86.4pt)がひと続きに組まれています。
 * </p>
 */
public class FloatInFlowTest extends AbstractTestCase {
	public FloatInFlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0415-column-fill/float-in-flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** #a の段の断片。左右に4行ずつで均等。{x, y, 幅, 高さ} */
	private static final double[][] A = { //
			{ 6, 6, 171, 57.6 }, //
			{ 201, 6, 171, 57.6 }, //
	};

	/**
	 * #b の段の断片。左段は浮動体の右に8行(高さは浮動体と同じ114.96)、
	 * 右段は6行で、57.6と28.8の2つの箱に分かれる(隙間なく続く)。
	 */
	private static final double[][] B = { //
			{ 6, 126.56, 171, 114.96 }, //
			{ 201, 126.56, 171, 57.6 }, //
			{ 201, 184.16, 171, 28.8 }, //
	};

	private int ia = 0;

	private int ib = 0;

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertTrue("#a の断片が想定より多い: " + this.ia, this.ia < A.length);
			check("#a", this.ia, A[this.ia], box, x, y);
			this.ia++;
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertTrue("#b の断片が想定より多い: " + this.ib, this.ib < B.length);
			check("#b", this.ib, B[this.ib], box, x, y);
			this.ib++;
			return true;
		}
		return false;
	}

	private static void check(final String id, final int index, final double[] expected, final IBox box,
			final double x, final double y) {
		final String at = id + " 断片" + index;
		assertEquals(at + " x", expected[0], x, 1);
		assertEquals(at + " y", expected[1], y, 1);
		assertEquals(at + " 幅", expected[2], box.getWidth(), 1);
		assertEquals(at + " 高さ", expected[3], box.getHeight(), 1);
	}
}
