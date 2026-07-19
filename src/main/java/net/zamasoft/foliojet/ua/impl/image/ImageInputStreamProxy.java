package net.zamasoft.foliojet.ua.impl.image;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageInputStream;

class ImageInputStreamProxy extends InputStream {
	private final ImageInputStream imageInputStream;

	ImageInputStreamProxy(ImageInputStream imageInputStream) {
		this.imageInputStream = imageInputStream;
	}

	@Override
	public int read() throws IOException {
		return this.imageInputStream.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.imageInputStream.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return this.imageInputStream.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		return this.imageInputStream.skipBytes(n);
	}

	@Override
	public int available() throws IOException {
		long length = this.imageInputStream.length();
		if (length < 0) {
			return 0;
		}
		long available = length - this.imageInputStream.getStreamPosition();
		return available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available;
	}

	@Override
	public void close() throws IOException {
		this.imageInputStream.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		this.imageInputStream.mark();
	}

	@Override
	public synchronized void reset() throws IOException {
		this.imageInputStream.reset();
	}

	@Override
	public boolean markSupported() {
		return true;
	}
}
