package jp.cssj.homare.impl.objects.barcode.jp4scc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.krysalis.barcode4j.impl.AbstractBarcodeBean;
import org.krysalis.barcode4j.output.CanvasProvider;

/**
 * Japanese Post 4-State Customer Codeの情報の保持と描画を行うBeanです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class JP4SCCBean extends AbstractBarcodeBean {
	private static final byte[] N1 = { 1, 1, 4 };
	private static final byte[] N2 = { 1, 3, 2 };
	private static final byte[] N3 = { 3, 1, 2 };
	private static final byte[] N4 = { 1, 2, 3 };
	private static final byte[] N5 = { 1, 4, 1 };
	private static final byte[] N6 = { 3, 2, 1 };
	private static final byte[] N7 = { 2, 1, 3 };
	private static final byte[] N8 = { 2, 3, 1 };
	private static final byte[] N9 = { 4, 1, 1 };
	private static final byte[] N0 = { 1, 4, 4 };
	private static final byte[] N_ = { 4, 1, 4 };
	private static final byte[] CC1 = { 3, 2, 4 };
	private static final byte[] CC2 = { 3, 4, 2 };
	private static final byte[] CC3 = { 2, 3, 4 };
	private static final byte[] CC4 = { 4, 3, 2 };
	private static final byte[] CC5 = { 2, 4, 3 };
	private static final byte[] CC6 = { 4, 2, 3 };
	private static final byte[] CC7 = { 4, 4, 1 };
	private static final byte[] CC8 = { 1, 1, 1 };
	private static final byte[] START = { 1, 3 };
	private static final byte[] STOP = { 3, 1 };

	public void generateBarcode(CanvasProvider canvas, String msg) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		msg = msg.toUpperCase();

		try {
			int ck = 0;
			out.write(START);
			int j = 0;
			for (int i = 0; i < msg.length(); ++i) {
				char c = msg.charAt(i);
				switch (c) {
				case '1':
					out.write(N1);
					ck += 1;
					++j;
					break;
				case '2':
					out.write(N2);
					ck += 2;
					++j;
					break;
				case '3':
					out.write(N3);
					ck += 3;
					++j;
					break;
				case '4':
					out.write(N4);
					ck += 4;
					++j;
					break;
				case '5':
					out.write(N5);
					ck += 5;
					++j;
					break;
				case '6':
					out.write(N6);
					ck += 6;
					++j;
					break;
				case '7':
					out.write(N7);
					ck += 7;
					++j;
					break;
				case '8':
					out.write(N8);
					ck += 8;
					++j;
					break;
				case '9':
					out.write(N9);
					ck += 9;
					++j;
					break;
				case '0':
					out.write(N0);
					ck += 0;
					++j;
					break;
				case '-':
					out.write(N_);
					ck += 10;
					++j;
					break;
				case 'A':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N0);
					ck += 0;
					++j;
					break;
				case 'B':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N1);
					ck += 1;
					++j;
					break;
				case 'C':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N2);
					ck += 2;
					++j;
					break;
				case 'D':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N3);
					ck += 3;
					++j;
					break;
				case 'E':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N4);
					ck += 4;
					++j;
					break;
				case 'F':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N5);
					ck += 5;
					++j;
					break;
				case 'G':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N6);
					ck += 6;
					++j;
					break;
				case 'H':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N7);
					ck += 7;
					++j;
					break;
				case 'I':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N8);
					ck += 8;
					++j;
					break;
				case 'J':
					out.write(CC1);
					ck += 11;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N9);
					ck += 9;
					++j;
					break;
				case 'K':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N0);
					ck += 0;
					++j;
					break;
				case 'L':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N1);
					ck += 1;
					++j;
					break;
				case 'M':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N2);
					ck += 2;
					++j;
					break;
				case 'N':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N3);
					ck += 3;
					++j;
					break;
				case 'O':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N4);
					ck += 4;
					++j;
					break;
				case 'P':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N5);
					ck += 5;
					++j;
					break;
				case 'Q':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N6);
					ck += 6;
					++j;
					break;
				case 'R':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N7);
					ck += 7;
					++j;
					break;
				case 'S':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N8);
					ck += 8;
					++j;
					break;
				case 'T':
					out.write(CC2);
					ck += 12;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N9);
					ck += 9;
					++j;
					break;
				case 'U':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N0);
					ck += 0;
					++j;
					break;
				case 'V':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N1);
					ck += 1;
					++j;
					break;
				case 'W':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N2);
					ck += 2;
					++j;
					break;
				case 'X':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N3);
					ck += 3;
					++j;
					break;
				case 'Y':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N4);
					ck += 4;
					++j;
					break;
				case 'Z':
					out.write(CC3);
					ck += 13;
					++j;
					if (j >= 20) {
						break;
					}
					out.write(N5);
					ck += 5;
					++j;
					break;
				default:
					continue;
				}
				if (j >= 20) {
					break;
				}
			}

			for (; j < 20; ++j) {
				out.write(CC4);
				ck += 14;
			}

			switch (19 - ck % 19) {
			case 19:
				out.write(N0);
				break;
			case 1:
				out.write(N1);
				break;
			case 2:
				out.write(N2);
				break;
			case 3:
				out.write(N3);
				break;
			case 4:
				out.write(N4);
				break;
			case 5:
				out.write(N5);
				break;
			case 6:
				out.write(N6);
				break;
			case 7:
				out.write(N7);
				break;
			case 8:
				out.write(N8);
				break;
			case 9:
				out.write(N9);
				break;
			case 10:
				out.write(N_);
				break;
			case 11:
				out.write(CC1);
				break;
			case 12:
				out.write(CC2);
				break;
			case 13:
				out.write(CC3);
				break;
			case 14:
				out.write(CC4);
				break;
			case 15:
				out.write(CC5);
				break;
			case 16:
				out.write(CC6);
				break;
			case 17:
				out.write(CC7);
				break;
			case 18:
				out.write(CC8);
				break;
			default:
				throw new IllegalArgumentException("Unexpectred digit.");
			}
			out.write(STOP);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		byte[] code = out.toByteArray();
		for (int x = 0; x < code.length; ++x) {
			this.drawBar(canvas, x * 2 * this.moduleWidth, code[x]);
		}
	}

	private void drawBar(CanvasProvider canvas, double x, byte state) {
		switch (state) {
		case 1:
			canvas.deviceFillRect(x, 0 * this.moduleWidth, this.moduleWidth, 6 * this.moduleWidth);
			break;

		case 2:
			canvas.deviceFillRect(x, 0 * this.moduleWidth, this.moduleWidth, 4 * this.moduleWidth);
			break;

		case 3:
			canvas.deviceFillRect(x, 2 * this.moduleWidth, this.moduleWidth, 4 * this.moduleWidth);
			break;

		case 4:
			canvas.deviceFillRect(x, 2 * this.moduleWidth, this.moduleWidth, 2 * this.moduleWidth);
			break;

		default:
			throw new IllegalStateException("Unexpected state." + state);
		}
	}

	public double getBarWidth(int arg0) {
		// Japanese Post 4-State Customer Codeではバーの幅は関係ありません。
		return 0;
	}

}
