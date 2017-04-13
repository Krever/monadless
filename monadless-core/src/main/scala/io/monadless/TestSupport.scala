package io.monadless

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import org.scalamacros.resetallattrs._
import language.higherKinds
import scala.reflect.macros.TypecheckException
import language.higherKinds

private[monadless] trait TestSupport[M[_]] {
  def showTree[T](t: T): Unit = macro TestSupportMacro.showTree
  def showRawTree[T](t: T): Unit = macro TestSupportMacro.showRawTree
  def forceLift[T](t: T): T = macro TestSupportMacro.forceLift
  def runLiftTest[T](expected: T)(body: T): Unit = macro TestSupportMacro.runLiftTest[M, T]
}

private[monadless] class TestSupportMacro(val c: Context) {
  import c.universe._

  def showTree(t: Tree): Tree = {
    c.warning(c.enclosingPosition, t.toString)
    q"()"
  }
  def showRawTree(t: Tree): Tree = {
    c.warning(c.enclosingPosition, showRaw(t))
    q"()"
  }

  def forceLift(t: Tree): Tree =
    c.resetAllAttrs {
      Trees.Transform(c)(t) {
        case q"$pack.unlift[$t]($v)" =>
          q"${c.prefix}.get($v)"
      }
    }

  def runLiftTest[M[_], T](expected: Tree)(body: Tree)(implicit m: WeakTypeTag[M[_]], t: WeakTypeTag[T]): Tree =
    c.resetAllAttrs {

      val lifted =
        q"${c.prefix}.get(${c.prefix}.lift($body))"

      val forceLifted = forceLift(body)

      q"""
        val expected = scala.util.Try($expected)
        assert(expected == ${typecheckToTry(lifted, "lifted")})
        assert(expected == ${typecheckToTry(forceLifted, "force lifted")})
        ()
      """
    }

  def typecheckToTry(tree: Tree, name: String): Tree = {
    c.info(c.enclosingPosition, s"$name: $tree", force = false)
    try {
      q"scala.util.Try(${c.typecheck(c.resetAllAttrs(tree))})"
    } catch {
      case e: TypecheckException =>
        val msg = s"$name fails typechecking: $e"
        c.info(e.pos.asInstanceOf[Position], msg, force = true)
        q"""scala.util.Failure(new Exception($msg))"""
    }
  }
}
