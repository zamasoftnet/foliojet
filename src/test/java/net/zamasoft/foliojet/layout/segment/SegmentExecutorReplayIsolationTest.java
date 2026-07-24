package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * E-6増分3b-3(2026-07-24新設): {@code ReplacedLive}のreplay駆動が
 * {@link ReplacedBoxImage}のback-referenceを奪わないこと
 * ({@code SegmentExecutor.isolatedReplayInstance}の隔離契約)の
 * 単体テストです。
 *
 * <p>
 * 現存する唯一のproduction実装({@code BarcodeImage})は
 * {@code setReplacedBox}がno-opのため、この欠陥はgolden比較では観測
 * できない(潜在欠陥)。back-referenceを実際に保存するスタブで
 * 「scratch計測再生がlive側の画像状態を破壊しうる」構造そのものを
 * 固定する。
 * </p>
 */
public class SegmentExecutorReplayIsolationTest extends TestCase {

	/** back-referenceを実際に保存する(BarcodeImageと違いno-opでない)スタブ。 */
	private static final class RecordingReplacedBoxImage implements Image, ReplacedBoxImage {
		AbstractReplacedBox registeredBox;

		public double getWidth() {
			return 10;
		}

		public double getHeight() {
			return 5;
		}

		public void drawTo(GC gc) {
		}

		public String getAltString() {
			return null;
		}

		public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
			this.registeredBox = box;
		}

		public Image duplicate() {
			return new RecordingReplacedBoxImage();
		}
	}

	/**
	 * {@link ReplacedBoxImage}参照のreplayインスタンスは複製imageを持つ
	 * 独立paramsで作られ、replay側のcalculateSizeがliveのimageへ
	 * back-referenceを書き込まない(奪わない)。
	 */
	public void testReplayInstanceDoesNotStealBackReference() {
		final RecordingReplacedBoxImage liveImage = new RecordingReplacedBoxImage();
		final ReplacedParams params = new ReplacedParams();
		params.image = liveImage;
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		// liveのレイアウト(calculateSize)がback-referenceを登録する
		live.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);

		final AbstractReplacedBox replay = SegmentExecutor.isolatedReplayInstance(live);
		assertNotSame(live, replay);
		// paramsは複製image入りの独立インスタンス(liveのparamsは無変更)
		assertNotSame(params, replay.getReplacedParams());
		assertNotSame(liveImage, replay.getReplacedParams().image);
		assertTrue(replay.getReplacedParams().image instanceof ReplacedBoxImage);
		assertSame(liveImage, params.image);

		// replay側(scratch計測相当)のcalculateSizeは複製imageへ登録し、
		// liveのback-referenceは奪われない——是正対象の潜在欠陥そのもの
		replay.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);
		assertSame(replay, ((RecordingReplacedBoxImage) replay.getReplacedParams().image).registeredBox);

		// 値としては同等のparamsで再生される(隔離は内容を変えない)
		assertEquals(params.lineHeight, replay.getReplacedParams().lineHeight);
		assertEquals(params.size, replay.getReplacedParams().size);
	}

	/** 通常の(共有可能な)imageでは従来どおりparams/imageを共有する。 */
	public void testPlainImageKeepsSharedParams() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new Image() {
			public double getWidth() {
				return 10;
			}

			public double getHeight() {
				return 5;
			}

			public void drawTo(GC gc) {
			}

			public String getAltString() {
				return null;
			}
		};
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		final AbstractReplacedBox replay = SegmentExecutor.isolatedReplayInstance(live);
		assertNotSame(live, replay);
		assertSame(params, replay.getReplacedParams());
		assertSame(params.image, replay.getReplacedParams().image);
	}
}
