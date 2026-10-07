## <a id="style-page-break-in-table">Page breaks within tables</a>

Page breaks can occur between table rows and within table rows (within cells).
Table headers and footers are repeated on each page.

### Locations where page breaks never occur

Page breaks never occur in the following locations within a table.

- Within a table caption
- Between a table caption and the table
- Within a table header
- Within a table footer
- Between a table header and a row group
- Between a table footer and a row group

Therefore, <span class="cssprop">page-break-after</span>, <span class="cssprop">page-break-before</span>, and <span class="cssprop">page-break-inside</span>
are ignored when specified in these locations. As a result of these rules, as long as a table has a row group,
some part of a row group is always displayed regardless of where a page break occurs. A table consisting only of a header or footer never appears.

If the parts listed above exceed the page height, the table overflows the bottom of the page.
For an appropriate layout, the combined height of the table caption, header, and footer should therefore be sufficiently small relative to the page height.

### Applying page-break-XXX

Page break properties apply between rows in the same way as for blocks in normal flow.
They also apply between row groups. However, left and
right do not have their usual effect for forced page breaks; they behave the same as always.

<span class="cssprop">page-break-inside</span> specified on a cell applies to its row.
If cells in the same row specify conflicting auto and avoid values, avoid takes precedence.
In other words, if even one cell in a row specifies avoid, page breaks are prohibited throughout the row.
Also, when a cell spans rows with rowspan, page breaks are suppressed within every row the cell belongs to, and <span class="cssprop">page-break-after</span> and <span class="cssprop">page-break-before</span>
are treated as avoid between those rows.

<span class="cssprop">page-break-after</span> and <span class="cssprop">page-break-before</span> specified on a cell apply to its row.
The order of precedence in this case is as follows.

always > avoid > auto

If a table is at the top of a page and page break restrictions prevent any break before the bottom of the page, restrictions on breaks between rows are ignored.
In this case, a page break occurs even in a location where breaks are prohibited.

### Page breaks within table rows (within cells)

If no cell in a table row specifies <span class="cssdecl">page-break-inside: avoid;</span>
and the row reaches the bottom of the page, an attempt is made to split the row. <span class="cssprop">orphans</span> and <span class="cssprop">widows</span> are respected,
and if none of the cells in the row can be split, the entire row is moved to the next page. If at least one cell can be split, the row is split.
In this case, the other cells may be split without respecting <span class="cssprop">orphans</span> and <span class="cssprop">widows</span>.

<p class="note">
For split cells, vertical alignment specified with <span class="cssprop">vertical-align</span> has no effect,
and all cell content is aligned to the top of the cell.
</p>

### Default page break behavior

As in typical browsers, HTML td and th elements do not prohibit page breaks within cells
by default<span class="since">4.0.0</span>.
To prevent page breaks within cells, specify this explicitly.

```css
td, th {
  break-inside: avoid; /* page-break-inside: avoid is equivalent */
}
```

<p class="note">
Up to version 3.2 (and in development builds of 4.0), td and th elements defaulted to
<span class="cssdecl">page-break-inside: avoid;</span>.
This default was removed because it could cause an entire tall table approaching the bottom of a page
to be moved to the next page, leaving the first page almost blank.
</p>
