//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.data

import com.google.inject.{Singleton, Inject}
import java.io.{File, FileWriter, PrintWriter}
import java.sql.DriverManager
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.adapters.H2Adapter
import org.squeryl.{Schema, Session}
import scala.io.Source

import codex.Log

/** Handles some nuts and bolts of using in-app SQL databases. */
@Singleton class DB @Inject() (log :Log) {

  // resolve the H2 driver
  Class.forName("org.h2.Driver")

  /** Returns a session for a database in `root` with name `name`. The database will be created if it
    * does not already exist, and migrations will be run if it is an out of date version.
    *
    * @param codeVers the version of the database expected by the code.
    * @param migs a list of migrations to run if `diskVers` < `codeVers`, of the form:
    *             `(vers, descrip, sql*)`.
    */
  def session (root :File, name :String, schema :Schema, codeVers :Int,
               migs :(Int,String,Seq[String])*) = {

    // read the DB version file
    val vfile = new File(root, name + ".codex.vers")
    val fileVers = try {
      Source.fromFile(vfile).getLines.next.toInt
    } catch {
      case e :Throwable => 0
    }
    if (codeVers < fileVers) {
      log.warning("DB on file system is higher version than code? Beware.",
                  "file", fileVers, "code", codeVers)
    }

    // create the database session (which will create the database, if necessary)
    val dburl = "jdbc:h2:" + new File(root, name + ".codex").getAbsolutePath
    val sess = Session.create(DriverManager.getConnection(dburl, "sa", ""), new H2Adapter)
    // sess.setLogger(dblogger)

    def writeVersion (version :Int) {
      val out = new PrintWriter(new FileWriter(vfile))
      out.println(version)
      out.close
    }

    // if we have no version string, we need to initialize the database
    if (fileVers < 1) {
      log.info("Initializing schema.", "name", name, "vers", codeVers)
      using(sess) { schema.create }
      writeVersion(codeVers)
    }
    // otherwise perform the applicable migration(s)
    else migs filter(fileVers < _._1) foreach { case (vers, descrip, sqls) =>
      log.info(descrip)
      // perform the migration
      val stmt = Session.currentSession.connection.createStatement
      try sqls foreach { stmt.executeUpdate(_) }
      finally stmt.close
      // note that we're consistent with the specified version
      writeVersion(vers)
    }

    sess
  }
}
