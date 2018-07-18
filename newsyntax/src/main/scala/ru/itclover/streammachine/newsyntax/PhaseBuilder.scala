package ru.itclover.streammachine.newsyntax

import ru.itclover.streammachine.aggregators.AggregatorPhases.AggregatorFunctions
import ru.itclover.streammachine.core.Time.{MaxWindow, TimeExtractor}
import ru.itclover.streammachine.core.{PhaseParser, Time, Window}
import ru.itclover.streammachine.newsyntax.TrileanOperators.{And, AndThen, Or}
import ru.itclover.streammachine.phases.ConstantPhases.OneRowPhaseParser
import ru.itclover.streammachine.phases.NumericPhases.{BinaryNumericParser, SymbolExtractor, SymbolParser}
import ru.itclover.streammachine.phases.BooleanPhases.{Assert, BooleanPhaseParser, ComparingParser}

class PhaseBuilder[Event] {
  def build(x: Expr, level: Integer = 0)(implicit timeExtractor: TimeExtractor[Event]): PhaseParser[Event, _, _] = {
    val nextBuild = (x: Expr) => build(x, level + 1)
    x match {
      case BooleanLiteral(value) => OneRowPhaseParser[Event, Boolean](_ => value)
      case IntegerLiteral(value) => OneRowPhaseParser[Event, Long](_ => value)
      case BooleanOperatorExpr(operator, lhs, rhs) => new ComparingParser[Event, Any, Any, Boolean](
        nextBuild(lhs).asInstanceOf[PhaseParser[Event, Any, Boolean]],
        nextBuild(rhs).asInstanceOf[PhaseParser[Event, Any, Boolean]])(operator.comparingFunction,
        operator.operatorSymbol) {}
      case ComparisonOperatorExpr(operator, lhs, rhs) => new ComparingParser[Event, Any, Any, Double](
        nextBuild(lhs).asInstanceOf[PhaseParser[Event, Any, Double]],
        nextBuild(rhs).asInstanceOf[PhaseParser[Event, Any, Double]])(operator.comparingFunction,
        operator.operatorSymbol) {}
      case DoubleLiteral(value) => OneRowPhaseParser[Event, Double](_ => value)
      case FunctionCallExpr(function, arguments) => function match {
        case "avg" => PhaseParser.Functions.avg(nextBuild(arguments.head).asInstanceOf[PhaseParser[Event, _, Double]],
          Window(arguments(1).asInstanceOf[TimeLiteral].millis))
        case "sum" => PhaseParser.Functions.sum(nextBuild(arguments.head).asInstanceOf[PhaseParser[Event, _, Double]],
          Window(arguments(1).asInstanceOf[TimeLiteral].millis))
        case "lag" => PhaseParser.Functions.lag(nextBuild(arguments.head))
        case "abs" => PhaseParser.Functions.abs(nextBuild(arguments.head).asInstanceOf[PhaseParser[Event, _, Double]])
        case _ => throw new RuntimeException(s"Unknown function $function")
      }
      case Identifier(identifier) => SymbolParser(Symbol(identifier)).as[Double](
        ev = new SymbolExtractor[Event, Double] {
          override def extract(event: Event, symbol: Symbol): Double = 0.0 // TODO: real identifier value
        }
      ) // TODO: Correct type
      case OperatorExpr(operator, lhs, rhs) =>
        val lhsParser = nextBuild(lhs).asInstanceOf[PhaseParser[Event, _, Double]]
        val rhsParser = nextBuild(rhs).asInstanceOf[PhaseParser[Event, _, Double]]
        BinaryNumericParser(lhsParser, rhsParser, operator.comp[Double], operator.operatorSymbol)
      case StringLiteral(value) => OneRowPhaseParser[Event, String](_ => value)
      case TrileanExpr(cond, exactly, window, range, until) =>
        if (until != null) {
          nextBuild(cond).timed(MaxWindow).asInstanceOf[PhaseParser[Event, _, Boolean]] and
            Assert(nextBuild(until).asInstanceOf[BooleanPhaseParser[Event, _]])
        }
        else {
          val w = Window(window.millis)
          var c = nextBuild(cond)
          if (range.isInstanceOf[RepetitionRangeExpr]) {
            // TODO: truthCount
          }
          if (range.isInstanceOf[TimeRangeExpr]) {
            // TODO: truthMillisCount
          }
          if (exactly) {
            c.timed(w, w)
          } else {
            c.timed(Time.less(w))
          }
        }
      case TrileanOperatorExpr(operator, lhs, rhs) =>
        operator match {
          case And => nextBuild(lhs) togetherWith nextBuild(rhs)
          case AndThen => nextBuild(lhs) andThen nextBuild(rhs)
          case Or => nextBuild(lhs) either nextBuild(rhs)
        }
      case _ => throw new RuntimeException(s"something went wrong parsing $x")
    }
  }
}