package net.zamasoft.foliojet.layout.segment;

import java.io.File;
import java.io.IOException;

/**
 * text payload専用のspillファサードです(E-6増分3b-2、2026-07-24新設)。
 *
 * <p>
 * {@code LayoutSource.Chars}のchar[]本体を、予算超過時にディスクへ
 * 退避するための最小の口({@code append}/{@code read}/{@code close})を
 * 提供する。物理格納は{@link SpillStore}(package-private最小部品——
 * codex裁定によりstore自体は公開しない)で、本クラスはUTF-16BEの
 * encode/decodeとrecordId直接readだけを担う。レイアウト意味論は
 * 一切持たない。
 * </p>
 *
 * <p>
 * <b>寿命</b>: {@code LayoutSource}がspillを最初に必要としたときに
 * 遅延生成され、{@code LayoutSource#close()}(変換終了経路のfinally)で
 * 確実に閉じられる。closeは冪等で、両一時ファイルを削除する。
 * </p>
 *
 * <p>
 * スレッド安全ではない(単一レイアウトセッション内での利用が前提。
 * {@code SpillStore}と同じ契約)。
 * </p>
 */
public final class TextSpill implements AutoCloseable {
	private final SpillStore store;

	private TextSpill(final SpillStore store) {
		this.store = store;
	}

	/** 新しいspillストア(一時ファイル)を開きます。 */
	public static TextSpill open() throws IOException {
		return new TextSpill(SpillStore.create());
	}

	/**
	 * {@code ch[off..off+len)}をUTF-16BEで1 recordとして追記し、
	 * recordId(0起点の連番)を返します。
	 */
	public long append(final char[] ch, final int off, final int len) throws IOException {
		final byte[] record = new byte[len * 2];
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			record[i * 2] = (byte) (c >>> 8);
			record[i * 2 + 1] = (byte) c;
		}
		return this.store.append(record);
	}

	/**
	 * recordIdのrecordをdecodeして返します。返す配列は<b>常に呼び出し
	 * ごとに新しい(fresh)</b>——replay駆動の下流はin-place変換を行う
	 * ため、キャッシュ共有は行わない(3b-1の方針)。heapメタデータの
	 * 期待長と実recordの長さが食い違う場合は破損として
	 * {@link IOException}で失敗する。
	 */
	public char[] read(final long recordId, final int expectedUtf16Length) throws IOException {
		final byte[] record = this.store.read(recordId);
		if (record.length != expectedUtf16Length * 2) {
			throw new IOException("spilled text record corrupted (length " + record.length + " != "
					+ (expectedUtf16Length * 2) + ") at record " + recordId);
		}
		final char[] ch = new char[expectedUtf16Length];
		for (int i = 0; i < ch.length; ++i) {
			ch[i] = (char) (((record[i * 2] & 0xFF) << 8) | (record[i * 2 + 1] & 0xFF));
		}
		return ch;
	}

	/** ストアを閉じ、一時ファイルを削除します(冪等)。 */
	@Override
	public void close() {
		this.store.close();
	}

	File dataFileForTest() {
		return this.store.dataFileForTest();
	}

	File indexFileForTest() {
		return this.store.indexFileForTest();
	}

	/** 一時ファイルが両方とも削除済みかを返します(テスト観測用)。 */
	public boolean tempFilesDeletedForTest() {
		return !this.store.dataFileForTest().exists() && !this.store.indexFileForTest().exists();
	}
}
