package ru.itclover.tsp.core

import org.scalatest.WordSpec
import ru.itclover.tsp.core.PatternResult.Stay
import ru.itclover.tsp.phases.MonadPhases
//import ru.itclover.tsp.core.Pattern.Functions._
import ru.itclover.tsp.core.PatternResult.{Failure, Success}
//import ru.itclover.tsp.core.Time._
//import ru.itclover.tsp.phases.ConstantPhases.ConstantFunctions
//import ru.itclover.tsp.phases.NumericPhases.SymbolParser
import ru.itclover.tsp.phases.{ConstantPhases, NoState}
import ru.itclover.tsp.utils.ParserMatchers
import ru.itclover.tsp.phases.NumericPhases._
import scala.Predef.{any2stringadd => _, _}


class NumericPhaseTest extends WordSpec with ParserMatchers {

  "BinaryNumericParser" should {
    "work on stay and success events and +, -, *, /" in {
      val b: NumericPhaseParser[TestEvent[Double], NoState] = ConstantPhases[TestEvent[Double], Double](10.0)
      checkOnTestEvents(
        (p: TestPhase[Double]) => p plus b,
        staySuccesses,
        Seq(Success(11.0), Success(11.0), Success(12.0), Success(12.0), Success(11.0), Success(13.0), Failure("Test"), Success(14.0))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => p minus b,
        staySuccesses,
        Seq(Success(-9.0), Success(-9.0), Success(-8.0), Success(-8.0), Success(-9.0), Success(-7.0), Failure("Test"), Success(-6.0))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => p times b,
        staySuccesses,
        Seq(Success(10.0), Success(10.0), Success(20.0), Success(20.0), Success(10.0), Success(30.0), Failure("Test"), Success(40.0))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => p div b,
        staySuccesses,
        Seq(Success(0.1), Success(0.1), Success(0.2), Success(0.2), Success(0.1), Success(0.3), Failure("Test"), Success(0.4))
      )
    }
  }

  "Numeric phases" should {
    "work for type casting" in {
      import ru.itclover.tsp.phases.NumericPhases.SymbolParser
      import ru.itclover.tsp.phases.NumericPhases._

      val intVal = 18
      val floatVal = 18.0f
      val strVal = "18"
      val longVal = 18L

      implicit val fixedIntSymbolExtractor = new SymbolExtractor[TestEvent[Double], Int] {
        override def extract(event: TestEvent[Double], symbol: Symbol) = intVal
      }
      implicit val fixedFloatSymbolExtractor = new SymbolExtractor[TestEvent[Double], Float] {
        override def extract(event: TestEvent[Double], symbol: Symbol) = floatVal
      }
      implicit val fixedStringSymbolExtractor = new SymbolExtractor[TestEvent[Double], String] {
        override def extract(event: TestEvent[Double], symbol: Symbol) = strVal
      }
      implicit val fixedLongSymbolExtractor = new SymbolExtractor[TestEvent[Double], Long] {
        override def extract(event: TestEvent[Double], symbol: Symbol) = longVal
      }

      val intParser: Pattern[TestEvent[Double], NoState, Int] = 'i.as[Int]
      val floatParser: Pattern[TestEvent[Double], NoState, Float] = 'i.as[Float]
      val strParser: Pattern[TestEvent[Double], NoState, String] = 'i.as[String]
      val longParser: Pattern[TestEvent[Double], NoState, Long] = 'i.as[Long]


      checkOnTestEvents(
        (p: TestPhase[Double]) => p.flatMap(_ => intParser),
        staySuccesses,
        Seq(Success(intVal), Success(intVal), Success(intVal), Success(intVal), Success(intVal), Success(intVal), Failure("Test"), Success(intVal))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => p.flatMap(_ => floatParser),
          staySuccesses,
          Seq(Success(floatVal), Success(floatVal), Success(floatVal), Success(floatVal), Success(floatVal), Success(floatVal), Failure("Test"), Success(floatVal)),
          epsilon = Some(0.001f)
      )


      checkOnTestEvents_strict(
        (p: TestPhase[Double]) => p.flatMap(_ => strParser),
          staySuccesses,
          Seq(Success(strVal), Success(strVal), Success(strVal), Success(strVal), Success(strVal), Success(strVal), Failure("Test"), Success(strVal))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => p.flatMap(_ => longParser),
        staySuccesses,
        Seq(Success(longVal), Success(longVal), Success(longVal), Success(longVal), Success(longVal), Success(longVal), Failure("Test"), Success(longVal))
      )
    }
  }

  "Abs phase" should {
    "work" in {
      import ru.itclover.tsp.core.Pattern.Functions._
      val results = Stay :: Success(-1.0) :: Success(1.0) :: Failure("Test") :: Nil
      checkOnTestEvents(
        (p: TestPhase[Double]) => abs(p),
        for((t, res) <- times.take(results.length).zip(results)) yield TestEvent(res, t),
        Seq(Success(1.0), Success(1.0), Success(1.0), Failure("Test"))
      )
    }
  }

  "Reduce phase" should {
    val results = Stay :: Success(-1.0) :: Success(1.0) :: Failure("Test") :: Nil
    val events = for ((t, res) <- times.take(results.length).zip(results)) yield TestEvent(res, t)

    "work for one argument (return as it is)" in {
      checkOnTestEvents(
        (p: TestPhase[Double]) => Reduce(Math.max)(p.map(_ * 2.0)),
        events,
        Seq(Success(-2.0), Success(-2.0), Success(2.0), Failure("Test"))
      )
    }

    "work on min/max, sum reducers for successes" in {
      checkOnTestEvents(
        (p: TestPhase[Double]) => Reduce(Math.max)(p, p.map(_ * 2.0), p.map(_ / 2.0)),
        events,
        Seq(Success(-0.5), Success(-0.5), Success(2.0), Failure("Test"))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => Reduce(_ + _)(p, p.map(_ * 2.0), p.map(_ / 2.0)),
        events,
        Seq(Success(-3.5), Success(-3.5), Success(3.5), Failure("Test"))
      )
    }

    "not work on mixed arguments e.g. (Stay, Success) and etc" in {
      val constStay = new ConstResult(Stay: PatternResult[Double]).asInstanceOf[NumericPhaseParser[TestEvent[Double], Any]]
      val constFail = new ConstResult(Failure("Const Failure"): PatternResult[Double]).asInstanceOf[NumericPhaseParser[TestEvent[Double], Any]]

      checkOnTestEvents(
        (p: TestPhase[Double]) => {
          Reduce(Math.max)(p.asInstanceOf[NumericPhaseParser[TestEvent[Double], Any]], constStay)
        },
        events,
        Seq(Failure("Test"), Failure("Test"), Failure("Test"), Failure("Test"))
      )

      checkOnTestEvents(
        (p: TestPhase[Double]) => {
          Reduce(Math.max)(p.asInstanceOf[NumericPhaseParser[TestEvent[Double], Any]], constFail)
        },
        events,
        Seq(Failure("Const Failure"), Failure("Const Failure"), Failure("Const Failure"), Failure("Test"))
      )
    }
  }

}
