//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import pomutil.{POM, Dependency}

import codex.data.FqId

/** Provides information about a project's organization. */
abstract class ProjectModel (root :File) {

  /** Returns the directory that contains this project's main source code. */
  def sourceDir :File

  /** Returns the directory that contains this project's test source code. */
  def testSourceDir :File

  /** Returns the projects on which this project depends. */
  def depends :Seq[FqId]

  /** Returns the projects on which this project's test code depends (these are in addition to the
    * main [[depends]]). */
  def testDepends :Seq[FqId]

  /** Creates a file, relative to the project root, with the supplied path components. */
  protected def file (comps :String*) = (root /: comps)(new File(_, _))
}

object ProjectModel {

  /** Determines the best way to obtain project metadata for the project rooted at `root` and returns
    * a model of that metadata.
    */
  def infer (fqId :FqId, root :File) :ProjectModel = {
    // TODO: something more scalable
    val pom = new File(root, "pom.xml")
    if (pom.exists) new MavenProjectModel(root, pom)
    else {
      val m2pom = new File(root, s"${fqId.artifactId}-${fqId.version}.pom")
      if (m2pom.exists) new M2ProjectModel(fqId, root, m2pom)
      else new DefaultProjectModel(root)
    }
  }

  class DefaultProjectModel (root :File) extends ProjectModel(root) {
    override def sourceDir = {
      val options = Seq(file("src", "main"), file("src"))
      options find(_.isDirectory) getOrElse(root)
    }
    override def testSourceDir :File = {
      val options = Seq(file("test"), file("src", "test"), file("tests"), file("src", "tests"))
      options find(_.isDirectory) getOrElse(root)
    }
    override def depends :Seq[FqId] = Seq() // no idea!
    override def testDepends :Seq[FqId] = Seq() // no idea!
  }

  class MavenProjectModel (root :File, pfile :File) extends ProjectModel(root) {
    val pom = POM.fromFile(pfile).get
    override def sourceDir = file("src", "main") // TODO: read from POM
    override def testSourceDir = file("src", "test") // TODO: read from POM
    override def depends = pom.depends.filter(_.scope != "test") map(toFqId)
    override def testDepends = pom.depends.filter(_.scope == "test") map(toFqId)
    private def toFqId (dep :Dependency) = FqId(dep.groupId, dep.artifactId, dep.version)
  }

  class M2ProjectModel (fqId :FqId, root :File, pfile :File) extends ProjectModel(root) {
    val pom = POM.fromFile(pfile).get
    // TODO: download sources if jar doesn't exist
    override def sourceDir = new File(root, pfile.getName.replaceAll(".pom", "-sources.jar"))
    override def testSourceDir = root
    override def depends = pom.depends.filter(_.scope != "test") map(toFqId)
    override def testDepends = pom.depends.filter(_.scope == "test") map(toFqId)
    private def toFqId (dep :Dependency) = FqId(dep.groupId, dep.artifactId, dep.version)
  }
}
