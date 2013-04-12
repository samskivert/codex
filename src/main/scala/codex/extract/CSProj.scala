//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.extract

import java.io.File
import scala.xml.{Node, NodeSeq, XML}

import codex._
import codex.data.Depend

/** Utilities for interacting with Visual Studio/MonoDevelop .csproj files. */
object CSProj {

  /** Models a reference to a DLL. */
  case class Reference (name :String, file :Option[File]) {
    /** Converts this reference to a Codex dependency. */
    def toDepend (forTest :Boolean) = {
      // TODO: have the project give us hints as to where to look for system DLLs?
      val dll = (file orElse Dll.find(name))
      val version = dll map(Monodis.assemblyInfo) map(_.version) getOrElse("0.0.0.0")
      Depend(name, name, version, "dll", forTest, dll map(_.getAbsolutePath))
    }
  }

  /** Contains info extracted from a .csproj file. */
  case class Info (rootNamespace :String, assemblyName :String, version :String,
                   refs :Seq[Reference], sources :Seq[File])

  /** Extracts info from the supplied .csproj file. */
  def parse (csproj :File) :Info = {
    def toFile (node :Node) = file(csproj.getParentFile, node.text.split("""\\""") :_*)
    val xml = XML.loadFile(csproj)
    Info(text(xml \ "PropertyGroup" \ "RootNamespace"),
         text(xml \ "PropertyGroup" \ "AssemblyName"),
         "0.0.0.0", // TODO
         xml \ "ItemGroup" \ "Reference" flatMap { ref =>
           ref \ "@Include" map(dll => Reference(dll.text.trim, ref \ "HintPath" map(toFile) match {
             case Seq() => None
             case Seq(file, _*) => Some(file)
           }))
         },
         xml \ "ItemGroup" \ "Compile" \\ "@Include" map(toFile))
  }

  private def text (nodes :NodeSeq) = nodes.headOption map(_.text.trim) getOrElse("missing")
}
