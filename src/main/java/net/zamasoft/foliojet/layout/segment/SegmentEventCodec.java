package net.zamasoft.foliojet.layout.segment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link SegmentEvent}のバイナリcodecです(E-6増分2、2026-07-24新設、
 * production経路は未配線)。
 *
 * <p>
 * <b>バイト化の範囲</b>(この設計判断は増分2の中心):
 * </p>
 * <ul>
 * <li>{@link SegmentEvent.EndBox}/{@link SegmentEvent.Text}/
 * {@link SegmentEvent.Barrier}は完全にバイト化する(int/boolean/UTF-8
 * 文字列/enum ordinalのみで構成されるため)。</li>
 * <li>{@link SegmentEvent.BeginBox}({@link BoxRecipe})と
 * {@link SegmentEvent.Replaced}({@link ReplacedRecipe})の参照は
 * まだバイト化しない——encode時にin-memoryのside table(参照リスト)へ
 * 退避し、レコードにはその添字だけを書く。decodeは同一インスタンスを
 * 復元する。recipe(テンプレート群)の完全バイト化はrecipe設計の完成後
 * (増分3以降)。Javaシリアライズでのオブジェクトグラフ保存はcodex
 * 設計相談「やらないこと」により禁止。</li>
 * </ul>
 *
 * <p>
 * <b>レコード形式</b>: {@code [codec版(1byte)][tag(1byte)][payload]}。
 * decodeは版・tag・payload長(不足・余剰とも)を検査し、不正データは
 * {@link IllegalStateException}で失敗する(silent fallbackしない)。
 * </p>
 *
 * <p>
 * side tableはcodecインスタンスに属する——encodeとdecodeは同じ
 * インスタンスで行うのが契約(spillをまたぐ参照の寿命は、side tableを
 * 所有する側=将来のLayoutSourceが管理する)。スレッド安全ではない。
 * </p>
 */
final class SegmentEventCodec {
	/** レコード形式の版。 */
	static final byte CODEC_VERSION = 1;

	static final byte TAG_BEGIN_BOX = 1;
	static final byte TAG_END_BOX = 2;
	static final byte TAG_TEXT = 3;
	static final byte TAG_REPLACED = 4;
	static final byte TAG_BARRIER = 5;

	/** encode時に退避した参照(BoxRecipe/ReplacedRecipe)のside table。 */
	private final List<Object> sideTable = new ArrayList<>();

	/** イベントを1レコードのbyte列へencodeします。 */
	byte[] encode(final SegmentEvent event) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final DataOutputStream out = new DataOutputStream(bytes);
			out.writeByte(CODEC_VERSION);
			switch (event) {
			case SegmentEvent.BeginBox(final BoxRecipe recipe) -> {
				out.writeByte(TAG_BEGIN_BOX);
				out.writeInt(this.stash(recipe));
			}
			case SegmentEvent.EndBox() -> out.writeByte(TAG_END_BOX);
			case SegmentEvent.Text(final int sourceOffset, final String text, final boolean fixed) -> {
				out.writeByte(TAG_TEXT);
				out.writeInt(sourceOffset);
				out.writeBoolean(fixed);
				// writeUTFは64KB上限があるため長さ+UTF-8バイト列で書く
				final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
				out.writeInt(utf8.length);
				out.write(utf8);
			}
			case SegmentEvent.Replaced(final ReplacedRecipe recipe) -> {
				out.writeByte(TAG_REPLACED);
				out.writeInt(this.stash(recipe));
			}
			case SegmentEvent.Barrier(final Optional<BoxKind> kind, final BarrierReason reason) -> {
				out.writeByte(TAG_BARRIER);
				out.writeByte(kind.isPresent() ? kind.get().ordinal() : -1);
				out.writeByte(reason.ordinal());
			}
			}
			out.flush();
			return bytes.toByteArray();
		} catch (final IOException e) {
			// in-memoryストリームでは発生しない
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 1レコードをイベントへdecodeします。版・tag・payload長を検査し、
	 * 不正データは{@link IllegalStateException}で失敗します。
	 */
	SegmentEvent decode(final byte[] record) {
		try {
			final DataInputStream in = new DataInputStream(new ByteArrayInputStream(record));
			final byte version = in.readByte();
			if (version != CODEC_VERSION) {
				throw new IllegalStateException("unsupported event codec version: " + version);
			}
			final byte tag = in.readByte();
			final SegmentEvent event = switch (tag) {
			case TAG_BEGIN_BOX -> new SegmentEvent.BeginBox(this.unstash(in.readInt(), BoxRecipe.class));
			case TAG_END_BOX -> new SegmentEvent.EndBox();
			case TAG_TEXT -> {
				final int sourceOffset = in.readInt();
				final boolean fixed = in.readBoolean();
				final int length = in.readInt();
				if (length < 0 || length > in.available()) {
					throw new IllegalStateException("corrupted text record (length " + length + ")");
				}
				final byte[] utf8 = new byte[length];
				in.readFully(utf8);
				yield new SegmentEvent.Text(sourceOffset, new String(utf8, StandardCharsets.UTF_8), fixed);
			}
			case TAG_REPLACED -> new SegmentEvent.Replaced(this.unstash(in.readInt(), ReplacedRecipe.class));
			case TAG_BARRIER -> {
				final byte kindOrdinal = in.readByte();
				final Optional<BoxKind> kind;
				if (kindOrdinal == -1) {
					kind = Optional.empty();
				} else {
					if (kindOrdinal < 0 || kindOrdinal >= BoxKind.values().length) {
						throw new IllegalStateException("corrupted barrier record (kind " + kindOrdinal + ")");
					}
					kind = Optional.of(BoxKind.values()[kindOrdinal]);
				}
				final byte reasonOrdinal = in.readByte();
				if (reasonOrdinal < 0 || reasonOrdinal >= BarrierReason.values().length) {
					throw new IllegalStateException("corrupted barrier record (reason " + reasonOrdinal + ")");
				}
				yield new SegmentEvent.Barrier(kind, BarrierReason.values()[reasonOrdinal]);
			}
			default -> throw new IllegalStateException("unknown event tag: " + tag);
			};
			if (in.read() != -1) {
				throw new IllegalStateException("trailing bytes after event record (tag " + tag + ")");
			}
			return event;
		} catch (final EOFException e) {
			throw new IllegalStateException("truncated event record", e);
		} catch (final IOException e) {
			// in-memoryストリームでは発生しない
			throw new UncheckedIOException(e);
		}
	}

	private int stash(final Object reference) {
		final int ref = this.sideTable.size();
		this.sideTable.add(reference);
		return ref;
	}

	private <T> T unstash(final int ref, final Class<T> type) {
		if (ref < 0 || ref >= this.sideTable.size()) {
			throw new IllegalStateException(
					"side table ref out of range: " + ref + " (size=" + this.sideTable.size() + ")");
		}
		final Object value = this.sideTable.get(ref);
		if (!type.isInstance(value)) {
			throw new IllegalStateException("side table ref " + ref + " is not a " + type.getSimpleName() + ": "
					+ value.getClass().getName());
		}
		return type.cast(value);
	}

	int sideTableSizeForTest() {
		return this.sideTable.size();
	}
}
