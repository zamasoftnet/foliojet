package net.zamasoft.foliojet.layout.text;

import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * テキスト中の文字以外の埋め物です。
 *
 * <p>
 * 分類マーカー(foliojetの語彙)であり、定数({@code JOIN}/{@code BREAK}/
 * {@code CONTINUE_BEFORE}/{@code CONTINUE_AFTER})と{@code getString()}は
 * {@link TextControl}から継承する(2026-08-01の一本化で同内容の再宣言を
 * 撤去した)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class Quad extends TextControl {
}
