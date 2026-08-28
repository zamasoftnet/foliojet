package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/** 紙上のsticky insetで横flex継続をずらさず、側欄の境界itemと入れ子flex画像幅を保つ。 */
public class FlexRowContinuationNestedTest extends AbstractTestCase {
	private final Map<Integer, double[]> textBoxes = new HashMap<>();

	public FlexRowContinuationNestedTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		this.session.property("output.resolution", "96");
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0510-flex/row-continuation-nested.html"), "text/html", null);
	}

	private boolean text(final int item, final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertTrue("item " + item + " の本文がページ上端より上に配置: y=" + y, y >= -0.1);
			this.textBoxes.put(item, new double[] { pageNumber, x, box.getWidth() });
			return true;
		}
		return false;
	}

	private boolean thumb(final int item, final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.REPLACED) {
			assertTrue("item " + item + " の画像がページ上端より上に配置: y=" + y, y >= -0.1);
			final double[] text = this.textBoxes.get(item);
			assertNotNull("item " + item + " の本文が欠落", text);
			assertEquals("item " + item + " の本文と画像が別ページ", text[0], pageNumber, 0);
			assertTrue("item " + item + " の本文幅が画像へ重なる: textRight=" + (text[1] + text[2])
					+ " imageLeft=" + x, text[1] + text[2] <= x + 0.1);
			return true;
		}
		return false;
	}

	public boolean check_item2(final IBox box, final int pageNumber, final double x, final double y) {
		return this.text(2, box, pageNumber, x, y);
	}

	public boolean check_thumb2(final IBox box, final int pageNumber, final double x, final double y) {
		return this.thumb(2, box, pageNumber, x, y);
	}

	public boolean check_title2(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.TEXT_BLOCK) {
			assertTrue("item 2 の改行幅が画像領域まで広がる: width=" + box.getWidth(), box.getWidth() < 180);
			return true;
		}
		return false;
	}

	public boolean check_item3(final IBox box, final int pageNumber, final double x, final double y) {
		return this.text(3, box, pageNumber, x, y);
	}

	public boolean check_thumb3(final IBox box, final int pageNumber, final double x, final double y) {
		return this.thumb(3, box, pageNumber, x, y);
	}

	public boolean check_item4(final IBox box, final int pageNumber, final double x, final double y) {
		return this.text(4, box, pageNumber, x, y);
	}

	public boolean check_thumb4(final IBox box, final int pageNumber, final double x, final double y) {
		return this.thumb(4, box, pageNumber, x, y);
	}

	public boolean check_item5(final IBox box, final int pageNumber, final double x, final double y) {
		return this.text(5, box, pageNumber, x, y);
	}

	public boolean check_thumb5(final IBox box, final int pageNumber, final double x, final double y) {
		return this.thumb(5, box, pageNumber, x, y);
	}
}
