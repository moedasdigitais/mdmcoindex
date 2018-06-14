package com.wavesplatform.lang.v1.evaluator.ctx.impl

import cats.data.EitherT
import com.wavesplatform.lang.v1.{CTX, FunctionHeader}
import com.wavesplatform.lang.v1.compiler.CompilerContext
import com.wavesplatform.lang.v1.compiler.Terms.FUNCTION_CALL
import com.wavesplatform.lang.v1.compiler.Types._
import com.wavesplatform.lang.v1.evaluator.FunctionIds._
import com.wavesplatform.lang.v1.evaluator.ctx._
import com.wavesplatform.lang.v1.parser.BinaryOperation
import com.wavesplatform.lang.v1.parser.BinaryOperation._
import monix.eval.Coeval
import scodec.bits.ByteVector

import scala.util.Try

object PureContext {
  private val optionT                                             = OPTIONTYPEPARAM(TYPEPARAM('T'))
  private val noneCoeval: Coeval[Either[String, Option[Nothing]]] = Coeval.evalOnce(Right(None))
  private val nothingCoeval: Coeval[Either[String, Nothing]]      = Coeval.defer(Coeval(Right(throw new Exception("explicit contract termination"))))

  val none: LazyVal = LazyVal(EitherT(noneCoeval).subflatMap(Right(_: Option[Nothing]))) // IDEA HACK
  val err           = LazyVal(EitherT(nothingCoeval))
  val errRef        = "throw"

  val fraction: BaseFunction = PredefFunction("fraction", 1, FRACTION, LONG, "value" -> LONG, "numerator" -> LONG, "denominator" -> LONG) {
    case (v: Long) :: (n: Long) :: (d: Long) :: Nil =>
      val result = BigInt(v) * n / d
      for {
        _ <- Either.cond(result < Long.MaxValue, (), s"Long overflow: value `$result` greater than 2^63-1")
        _ <- Either.cond(result > Long.MinValue, (), s"Long overflow: value `$result` less than -2^63-1")
      } yield result.toLong
    case _ => ???
  }

  val extract: BaseFunction = PredefFunction("extract", 1, EXTRACT, TYPEPARAM('T'), "opt" -> optionT) {
    case Some(v) :: Nil => Right(v)
    case None :: Nil    => Left("Extract from empty option")
    case _              => ???
  }

  val some: BaseFunction = PredefFunction("Some", 1, SOME, optionT, "obj" -> TYPEPARAM('T')) {
    case v :: Nil => Right(Some(v))
    case _        => ???
  }

  val _isInstanceOf: BaseFunction = PredefFunction("_isInstanceOf", 1, ISINSTANCEOF, BOOLEAN, "obj" -> TYPEPARAM('T'), "of" -> STRING) {
    case (p: CaseObj) :: (s: String) :: Nil => Right(p.caseType.name == s)
    case _                                  => ???
  }

  val isDefined: BaseFunction = PredefFunction("isDefined", 1, ISDEFINED, BOOLEAN, "opt" -> optionT) {
    case Some(_) :: Nil => Right(true)
    case None :: Nil    => Right(false)
    case _              => ???
  }

  val size: BaseFunction = PredefFunction("size", 1, SIZE_BYTES, LONG, "byteVector" -> BYTEVECTOR) {
    case (bv: ByteVector) :: Nil => Right(bv.size)
    case _                       => ???
  }

  val take: BaseFunction = PredefFunction("take", 1, TAKE_BYTES, BYTEVECTOR, "byteVector" -> BYTEVECTOR, "number" -> LONG) {
    case (bv: ByteVector) :: (number: Long) :: Nil => Right(bv.take(number))
    case _                                         => ???
  }

  private def createOp(op: BinaryOperation, t: TYPE, r: TYPE, func: Short)(body: (t.Underlying, t.Underlying) => r.Underlying): BaseFunction =
    PredefFunction(opsToFunctions(op), 1, func, r, "a" -> t, "b" -> t) {
      case a :: b :: Nil =>
        Right(body(a.asInstanceOf[t.Underlying], b.asInstanceOf[t.Underlying]))
      case _ => ???
    }

  val getElement: BaseFunction =
    PredefFunction("getElement", 2, GET_LIST, TYPEPARAM('T'), "arr" -> LISTTYPEPARAM(TYPEPARAM('T')), "pos" -> LONG) {
      case (arr: IndexedSeq[_]) :: (pos: Long) :: Nil => Try(arr(pos.toInt)).toEither.left.map(_.toString)
      case _                                          => ???
    }

  val getListSize: BaseFunction = PredefFunction("size", 2, SIZE_LIST, LONG, "arr" -> LISTTYPEPARAM(TYPEPARAM('T'))) {
    case (arr: IndexedSeq[_]) :: Nil => Right(arr.size.toLong)
    case _                           => ???
  }

  val uMinus: BaseFunction = PredefFunction("-", 1, MINUS_LONG, LONG, "n" -> LONG) {
    case (n: Long) :: Nil => Right(Math.negateExact(n))
    case _                => ???
  }

  val uNot: BaseFunction = PredefFunction("!", 1, NOT_BOOLEAN, BOOLEAN, "p" -> BOOLEAN) {
    case (p: Boolean) :: Nil => Right(!p)
    case _                   => ???
  }

  private def createTryOp(op: BinaryOperation, t: TYPE, r: TYPE, func: Short)(body: (t.Underlying, t.Underlying) => r.Underlying): BaseFunction =
    PredefFunction(opsToFunctions(op), 1, func, r, "a" -> t, "b" -> t) {
      case a :: b :: Nil =>
        try {
          Right(body(a.asInstanceOf[t.Underlying], b.asInstanceOf[t.Underlying]))
        } catch {
          case e: Throwable => Left(e.getMessage())
        }
      case _ => ???
    }

  val mulLong: BaseFunction       = createTryOp(MUL_OP, LONG, LONG, MUL_LONG)(Math.multiplyExact)
  val divLong: BaseFunction       = createTryOp(DIV_OP, LONG, LONG, DIV_LONG)(Math.floorDiv)
  val modLong: BaseFunction       = createTryOp(MOD_OP, LONG, LONG, MOD_LONG)(Math.floorMod)
  val sumLong: BaseFunction       = createTryOp(SUM_OP, LONG, LONG, SUM_LONG)(Math.addExact)
  val subLong: BaseFunction       = createTryOp(SUB_OP, LONG, LONG, SUB_LONG)(Math.subtractExact)
  val sumString: BaseFunction     = createOp(SUM_OP, STRING, STRING, SUM_STRING)(_ + _)
  val sumByteVector: BaseFunction = createOp(SUM_OP, BYTEVECTOR, BYTEVECTOR, SUM_BYTES)((a, b) => ByteVector(a.toArray ++ b.toArray))
  val ge: BaseFunction            = createOp(GE_OP, LONG, BOOLEAN, GE_LONG)(_ >= _)
  val gt: BaseFunction            = createOp(GT_OP, LONG, BOOLEAN, GT_LONG)(_ > _)
  val sge: BaseFunction           = createOp(GE_OP, STRING, BOOLEAN, GE_STRING)(_ >= _)
  val sgt: BaseFunction           = createOp(GT_OP, STRING, BOOLEAN, GT_STRING)(_ > _)

  val eq: BaseFunction = PredefFunction(EQ_OP.func, 1, EQ, BOOLEAN, "a" -> TYPEPARAM('T'), "b" -> TYPEPARAM('T')) {
    case a :: b :: Nil => Right(a == b)
    case _             => ???
  }

  val ne: BaseFunction = UserFunction(NE_OP.func, 1, BOOLEAN, "a" -> TYPEPARAM('T'), "b" -> TYPEPARAM('T')) {
    case a :: b :: Nil => Right(FUNCTION_CALL(FunctionHeader.Predef(NOT_BOOLEAN), List(FUNCTION_CALL(FunctionHeader.Predef(EQ), List(a, b)))))
    case _             => ???
  }

  val operators: Seq[BaseFunction] = Seq(
    mulLong,
    divLong,
    modLong,
    sumLong,
    subLong,
    sumString,
    sumByteVector,
    eq,
    ne,
    ge,
    gt,
    sge,
    sgt,
    getElement,
    getListSize,
    uMinus,
    uNot
  )

  private val vars      = Map(("None", (OPTION(NOTHING), none)), (errRef, (NOTHING, err)))
  private val functions = Seq(fraction, extract, isDefined, some, size, take, _isInstanceOf) ++ operators

  lazy val ctx                              = CTX(Seq.empty, vars, functions)
  lazy val evalContext: EvaluationContext   = ctx.evaluationContext
  lazy val compilerContext: CompilerContext = ctx.compilerContext

}
