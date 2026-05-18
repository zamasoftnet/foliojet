<?xml version="1.0" encoding="Shift_JIS"?>
<xsl:stylesheet version="1.0"
  xmlns="http://www.w3.org/1999/xhtml"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/data">
    <html xml:lang="ja" lang="ja">
      <head>
        <title>変換されたXML</title>
        <style type="text/css">
@page {
	margin: 0;
}
body {
	margin: 0;
	font: 10pt/1 monospace;
}
ul {
	margin: 0;
}
        </style>
      </head>

      <body>
        <ul>
          <xsl:apply-templates select="item"/>
        </ul>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="item">
    <li id="a{position()}"><xsl:value-of select="text()"/></li>
  </xsl:template>
</xsl:stylesheet>
