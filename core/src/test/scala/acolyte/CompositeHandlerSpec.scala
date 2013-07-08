package acolyte

import java.sql.SQLException

import org.specs2.mutable.Specification

import acolyte.AbstractStatement.NO_PARAMS
import acolyte.StatementHandler.Parameter
import acolyte.CompositeHandler.{ QueryHandler, UpdateHandler }

object CompositeHandlerSpec extends Specification {
  "Composite statement handler" title

  "Query detection" should {
    "not be inited" in {
      new CompositeHandler().withQueryDetection(null.
        asInstanceOf[java.util.regex.Pattern]).
        aka("init") must throwA[IllegalArgumentException]

    }

    "always match" in {
      lazy val h = new CompositeHandler().withQueryDetection(".*")

      (h.isQuery("TEST") aka "detection 1" must beTrue).
        and(h.isQuery("SELECT * FROM table") aka "detection 2" must beTrue)
    }

    "match with a single pattern" in {
      new CompositeHandler().withQueryDetection("^SELECT ").
        isQuery("SELECT * FROM table") aka "detection" must beTrue

    }

    "not match" in {
      new CompositeHandler().withQueryDetection("^SELECT ").
        isQuery("TEST") aka "detection" must beFalse

    }

    "match with multiple patterns" in {
      new CompositeHandler().withQueryDetection("^SELECT ").
        withQueryDetection("EXEC that_proc").
        isQuery("EXEC that_proc('test')") aka "detection" must beTrue

    }
  }

  "Update handling" should {
    "not be inited" in {
      new CompositeHandler().withUpdateHandler(null.asInstanceOf[UpdateHandler]).
        aka("init") must throwA[IllegalArgumentException]

    }

    "be successful" in {
      (new CompositeHandler().withUpdateHandler(new UpdateHandler {
        def apply(s: String, p: java.util.List[Parameter]) = 1
      }).whenSQLUpdate("TEST", NO_PARAMS) aka "count" mustEqual 1).
        and(new CompositeHandler().withUpdateHandler(new UpdateHandler {
          def apply(s: String, p: java.util.List[Parameter]) = 3
        }).whenSQLUpdate("TEST", NO_PARAMS) aka "count" mustEqual 3).
        and(new CompositeHandler().withUpdateHandler(new UpdateHandler {
          def apply(s: String, p: java.util.List[Parameter]) = 10
        }).whenSQLUpdate("TEST", NO_PARAMS) aka "count" mustEqual 10)

    }

    "throw exception for update statement" in {
      new CompositeHandler().whenSQLUpdate("DELETE * FROM table", NO_PARAMS).
        aka("update") must throwA[SQLException].like {
          case e => e.getMessage.
              aka("message") mustEqual "No update handler: DELETE * FROM table"
        }
    }
  }

  "Query handling" should {
    "not be inited" in {
      new CompositeHandler().withQueryHandler(null.asInstanceOf[QueryHandler]).
        aka("init") must throwA[IllegalArgumentException]

    }

    "be successful for empty resultset" in {
      lazy val rows = RowLists.rowList1(classOf[String])
      lazy val res =
        new CompositeHandler().withQueryHandler(new QueryHandler {
          def apply(s: String, p: java.util.List[Parameter]) = rows.asResult
        }).whenSQLQuery("SELECT *", NO_PARAMS)

      res.aka("resultset") mustEqual rows.asResult
    }

    "be successful for not-empty resultset" in {
      lazy val rows = new RowList2(classOf[String], classOf[Float]).
        append(Rows.row2("str", 1.23.toFloat))

      lazy val res =
        new CompositeHandler().withQueryHandler(new QueryHandler {
          def apply(s: String, p: java.util.List[Parameter]) = rows.asResult
        }).whenSQLQuery("SELECT *", NO_PARAMS)

      res.aka("resultset") mustEqual rows.asResult
    }
  }
}