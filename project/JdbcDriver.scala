import scala.util.Properties.isJavaAtLeast

import sbt._
import Keys._

object JdbcDriver {
  import Dependencies._

  lazy val project =
    Project(id = "jdbc-driver", base = file("jdbc-driver")).settings(
      name := "jdbc-driver",
      autoScalaLibrary := false,
      crossScalaVersions := Seq(scalaVersion.value),
      crossPaths := false,
      scalacOptions ~= {
        _.filterNot(o => o.startsWith("-Y") || o.startsWith("-W"))
      },
      libraryDependencies ++= Seq(
        "commons-io" % "commons-io" % "2.20.0",
        "org.apache.commons" % "commons-lang3" % "3.18.0",
        "org.specs2" %% "specs2-core" % specsVer.value % Test
      ),
      Compile / sourceGenerators += Def.task[Seq[File]] {
        val base = (Compile / baseDirectory).value
        val managed = (Compile / sourceManaged).value

        generateCallableStatement(base, managed / "acolyte" / "jdbc") +:
          generateRowClasses(
            base,
            managed / "acolyte" / "jdbc",
            "acolyte.jdbc",
            false
          )
      }
    )

  // Source generators
  private def generateCallableStatement(basedir: File, outdir: File): File = {
    val tmpl = basedir / "src" / "main" / "templates" / "CallableStatement.tmpl"
    val f = outdir / "CallableStatement.java"

    IO.writer[File](f, "", IO.defaultCharset, false) { w =>
      // Generate by substitution on each line of template
      IO.reader[Unit](tmpl) { r =>
        IO.foreachLine(r) { l =>
          w.append(
            l.replace(
              "#JAVA_1_7#",
              if (!isJavaAtLeast("1.7")) "" else """/** Java 1.7+
     * {@inheritDoc}
     */
    public <T extends Object> T getObject(final int parameterIndex, 
                                          final Class<T> type) 
        throws SQLException {

        checkClosed();

        if (this.result == null) {
            throw new SQLException("No result");
        } // end of if

        return this.result.getObject(parameterIndex, type);
    } // end of getObject

    /** Java 1.7+
     * {@inheritDoc}
     */
    public <T extends Object> T getObject(final String parameterName,
                                          final Class<T> type) 
        throws SQLException {

        checkClosed();

        if (this.result == null) {
            throw new SQLException("No result");
        } // end of if

        return this.result.getObject(parameterName, type);
    } // end of getObject
"""
            )
          ).append("\n")

        }
      }

      f
    }
  }

  private def generateRowClasses(
      basedir: File,
      outdir: File,
      pkg: String,
      deprecated: Boolean = false
    ): Seq[File] = {
    val rowTmpl = basedir / "src" / "main" / "templates" / "Row.tmpl"
    val letter = ('A' to 'Z').map(_.toString) ++: ('A' to 'Z').map(l => "A" + l)
    val lim = letter.size

    val rows: Seq[File] = for (n ← 2 to lim) yield {
      val f = outdir / s"Row$n.java"
      IO.writer[File](f, "", IO.defaultCharset, false) { w =>
        val cp = for (i ← 0 until n) yield letter(i)
        val ps = for (i ← 0 until n) yield s"public final ${letter(i)} _$i;"
        val ip = for (i ← 0 until n) yield s"final ${letter(i)} c$i"
        val as = for (i ← 0 until n) yield s"this._$i = c$i;"
        val rp = for (i ← 0 until n) yield s"cs.add(this._$i);"
        val na = for (i ← 0 until n) yield "null"

        val sp =
          for (i ← 0 until n)
            yield """/**
     * Sets value for cell #%s.
     *
     * @param value the new value
     * @return Updated row
     */
    public Row%s<%s> set%d(final %s value) {
      return new Row%s<%s>(%s);
    }""".format(
              i + 1,
              n,
              cp.mkString(","),
              i + 1,
              letter(i),
              n,
              cp.mkString(","),
              (for (j ← 0 until n)
                yield { if (j == i) "value" else s"this._$j" }).mkString(", ")
            )

        val ao = for (i ← (n + 1) to (lim - n)) yield {
          val ol = for (j ← 0 until i) yield letter(n + j - 1)

          s"""/**
     * Appends the values of the rows.
     */
    public <${ol mkString ", "}> void append(Row$i<${ol mkString ", "}> other) {
    }"""
        }

        val hc = for (i ← 0 until n) yield s"append(this._$i)"
        val eq = for (i ← 0 until n) yield s"append(this._$i, other._$i)"

        // Generate by substitution on each line of template
        IO.reader[Unit](rowTmpl) { r =>
          IO.foreachLine(r) { l =>
            w.append(
              l.replace("#PKG#", pkg)
                .replace("#CLA#", { if (deprecated) "@Deprecated" else "" })
                .replaceAll("#N#", n.toString)
                .replaceAll("#CP#", cp.mkString(","))
                .replace("#PS#", ps.mkString("\n    "))
                .replace("#AS#", as.mkString("\n        "))
                .replace("#IP#", ip.mkString(", "))
                .replace("#RP#", rp.mkString("\n        "))
                .replace("#NA#", na.mkString(", "))
                .replace("#SP#", sp.mkString("\n\n    "))
                .replace("#AO#", ao.mkString("\n\n    "))
                .replace("#HC#", hc.mkString(".\n            "))
                .replace("#EQ#", eq.mkString(".\n            "))
            ).append("\n")

          }
        }

        f
      }
    }

    val listTmpl = basedir / "src" / "main" / "templates" / "RowList.tmpl"
    val rowLists: Seq[File] = for (n ← 1 to lim) yield {
      val f = outdir / s"RowList$n.java"
      val cp = for (i ← 0 until n) yield letter(i)
      val cs = for (i ← 0 until n) yield s"final Class<${letter(i)}> c$i"
      val ic = for (i ← 0 until n) yield {
        s"""if (c$i == null) {
                throw new IllegalArgumentException("Invalid class for column #$i");
            }"""
      }
      val ac = for (i ← 0 until n) yield {
        s"""this._c$i = c$i;
            colClasses.add(this._c$i);"""
      }
      val ca = for (i ← 0 until n) yield s"c$i"
      val ap = cp map { l => s"final $l ${l.toLowerCase}" }
      val pd = cp map { l => s"@param ${l.toLowerCase} the $l value" }
      val ps = for (i ← 0 until n) yield {
        s"""/**
         * Class of column #$i
         */
        final Class<${letter(i)}> _c$i;"""
      }
      val ags = for (i ← 0 until n) yield {
        s"""/**
     * Returns the class of column #$i.
     * @return Class of column #$i.
     */
    public abstract Class<${letter(i)}> c$i();"""
      }
      val gc = for (i ← 0 until n) yield s"c$i()"
      val gs = for (i ← 0 until n) yield {
        s"""/**
         * {inheritDoc}
         */
        public Class<${letter(i)}> c$i() { return this._c$i; }"""
      }
      val psc = for (i ← 0 until n) yield s"_c$i"

      IO.writer[File](f, "", IO.defaultCharset, false) { w =>
        // Generate by substitution on each line of template
        IO.reader[Unit](listTmpl) { r =>
          IO.foreachLine(r) { l =>
            w.append(
              l.replace("#PKG#", pkg)
                .replace("#CLA#", { if (deprecated) "@Deprecated" else "" })
                .replaceAll("#N#", n.toString)
                .replaceAll("#CP#", cp.mkString(","))
                .replaceAll("#CS#", cs.mkString(", "))
                .replaceAll("#PD#", pd.mkString("\r     * "))
                .replaceAll("#AP#", ap.mkString(", "))
                .replaceAll("#AV#", cp.map(_.toLowerCase).mkString(", "))
                .replaceAll("#PS#", ps.mkString("\n\n        "))
                .replaceAll("#AGS#", ags.mkString("\n\n    "))
                .replaceAll("#GC#", gc.mkString(", "))
                .replaceAll("#GS#", gs.mkString("\n\n        "))
                .replaceAll("#PSC#", psc.mkString(", "))
                .replaceAll("#IC#", ic.mkString("\n\n            "))
                .replaceAll("#AC#", ac.mkString("\n\n            "))
                .replaceAll("#CA#", ca.mkString(", "))
            ).append("\n")
          }
        }

        f
      }
    }

    val facTmpl = basedir / "src" / "main" / "templates" / "RowLists.tmpl"
    val rlf: File = {
      val f = outdir / "RowLists.java"

      IO.writer[File](f, "", IO.defaultCharset, false) { w =>
        val funcs = (1 to lim).foldLeft(Nil: List[String]) { (l, n) =>
          val g = for (i ← 0 until n) yield letter(i)
          val ps = for (i ← 0 until n) yield s"final Class<${letter(i)}> _c$i"
          val cs = for (i ← 0 until n) yield s"final Column<${letter(i)}> _c$i"
          val as = for (i ← 0 until n) yield s"_c$i"
          val ls = for (i ← 0 until n) yield s"withLabel(${i + 1}, _c$i.name)"
          val ns =
            for (i ← 0 until n) yield (s"withNullable(${i + 1}, _c$i.nullable)")
          val gp = g.mkString(",")

          l :+ """
    /**
     * Returns list of row with %d column(s).
     */
    public static <%s> RowList%d.Impl<%s> rowList%d(%s) { return new RowList%d.Impl<%s>(%s); }"""
            .format(
              n,
              gp,
              n,
              gp,
              n,
              ps.mkString(", "),
              n,
              gp,
              as.mkString(", ")
            ) :+ """
    /**
     * Returns list of row with %d column(s).
     */
    public static <%s> RowList%d.Impl<%s> rowList%d(%s) { return new RowList%d.Impl<%s>(%s.columnClass).%s.%s; }"""
            .format(
              n,
              gp,
              n,
              gp,
              n,
              cs.mkString(", "),
              n,
              gp,
              as.mkString(".columnClass, "),
              ls.mkString("."),
              ns.mkString(".")
            )

        }

        IO.reader[File](facTmpl) { r =>
          IO.foreachLine(r) { l =>
            w.append(
              l.replace("#PKG#", pkg)
                .replace("#CLA#", { if (deprecated) "@Deprecated" else "" })
                .replace("#F#", funcs.mkString("\n"))
            ).append("\n")
          }
          f
        }
      }
    }

    rows ++: rowLists :+ rlf
  }
}
