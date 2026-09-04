package net.zamasoft.foliojet.layout.draw;

/**
 * 見た目を持たず頁の出力(注釈・フォーム等)へ作用する描画要素の印です
 * (2026-09-03新設、filter-element-group-design.md §0-3)。
 * {@link Drawer}は{@code filter}の層の中でも、この印を持つ描画要素だけは
 * 頁のGCで描く(録画の中では消えるため)。
 */
public interface PageOutputDrawable extends Drawable {
}
