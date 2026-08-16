package net.zamasoft.foliojet.driver;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.SourceWrapper;

/** A source whose consumed input is charged to a shared byte budget. */
class InputLimitedSource extends SourceWrapper {
	private final InputByteBudget budget;

	InputLimitedSource(final Source source, final InputByteBudget budget) {
		super(source);
		this.budget = budget;
	}

	@Override
	public long getLength() throws IOException {
		final long length = super.getLength();
		if (this.budget != null) {
			this.budget.checkKnownLength(length);
		}
		return length;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		final InputStream in = super.getInputStream();
		return this.budget == null ? in : this.budget.wrap(in);
	}

	@Override
	public Reader getReader() throws IOException {
		if (this.budget != null) {
			throw new IOException("A byte input stream is required when an input size limit is set");
		}
		return super.getReader();
	}

	@Override
	public boolean isInputStream() throws IOException {
		return this.budget != null || super.isInputStream();
	}

	@Override
	public boolean isReader() throws IOException {
		return this.budget == null && super.isReader();
	}

	@Override
	public boolean isFile() throws IOException {
		// ファイルを直接開くと予算を迂回するため、常にSourceのstream経路を使う。
		return this.budget == null && super.isFile();
	}
}

/** Synchronized because resource loading may use more than one worker thread. */
final class InputByteBudget {
	private final long limit;
	private final String property;
	private long used;

	InputByteBudget(final long limit, final String property) {
		if (limit < 0) {
			throw new IllegalArgumentException("limit must not be negative");
		}
		this.limit = limit;
		this.property = property;
	}

	synchronized void checkKnownLength(final long length) throws IOException {
		if (length >= 0 && length > this.limit - this.used) {
			throw this.exceeded();
		}
	}

	private synchronized void consume(final long count) throws IOException {
		if (count <= 0) {
			return;
		}
		if (count > this.limit - this.used) {
			this.used = this.limit;
			throw this.exceeded();
		}
		this.used += count;
	}

	private IOException exceeded() {
		return new IOException(this.property + " exceeded: " + this.limit);
	}

	InputStream wrap(final InputStream in) {
		return new FilterInputStream(in) {
			@Override public int read() throws IOException {
				final int value = super.read();
				if (value != -1) consume(1);
				return value;
			}

			@Override public int read(final byte[] bytes, final int off, final int len) throws IOException {
				final int count = super.read(bytes, off, len);
				if (count != -1) consume(count);
				return count;
			}

			@Override public long skip(final long count) throws IOException {
				long remaining = count;
				final byte[] buffer = new byte[(int) Math.min(8192L, Math.max(1L, count))];
				while (remaining > 0) {
					final int read = this.read(buffer, 0, (int) Math.min(buffer.length, remaining));
					if (read == -1) break;
					remaining -= read;
				}
				return count - remaining;
			}
		};
	}

}
