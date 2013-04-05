//
// Codex - a multi-language code indexer and grokker
// http://github.com/samskivert/codex

package codex.http

import com.samskivert.mustache.{Mustache, Template, DefaultCollector}
import java.io.{InputStreamReader, FileNotFoundException, File, FileReader}
import java.util.{Iterator => JIterator}
import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable.{Map => MMap}

import codex._

/** Provides our various Mustache templates. */
object Templates {

  /** Loads and caches a template with the specified `tmpls/` relative path. */
  val tmpl :(String => Template) = {
    val devdir = file(new File("src"), "main", "resources", "tmpls")
    if (devdir.exists) devTmpl(devdir) _
    else prodTmpl _
  }

  private def prodTmpl (path :String) = synchronized {
    _compiled.getOrElseUpdate(path, {
      val in = getClass.getClassLoader.getResourceAsStream(s"tmpls/$path")
      if (in == null) throw new FileNotFoundException(s"tmpls/$path")
      _compiler.compile(new InputStreamReader(in))
    })
  }

  private def devTmpl (devdir :File)(path :String) = {
    _compiler.compile(new FileReader(file(devdir, path.split("/") :_*)))
  }

  private val _compiler = Mustache.compiler.withCollector(new DefaultCollector {
    override def toIterator (value :Object) :JIterator[_] = value match {
      case  iter :Iterator[_] => asJavaIterator(iter)
      case iable :Iterable[_] => asJavaIterator(iable.iterator)
      case _ => super.toIterator(value)
    }
    override def createFetcher (ctx :Object, name :String) = ctx match {
      case map :Map[_,_] => _mapFetcher
      case _             => super.createFetcher(ctx, name)
    }
  })

  private val _mapFetcher = new Mustache.VariableFetcher {
    override def get (ctx :Object, name :String) :Object =
      ctx.asInstanceOf[Map[String,AnyRef]].getOrElse(name, Template.NO_FETCHER_FOUND)
  }

  private val _compiled = MMap[String,Template]()
}
