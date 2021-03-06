package ru.itclover.tsp

import org.joda.time.{DateTime, Instant}
import ru.itclover.tsp.core.{Pattern, PatternResult}
import ru.itclover.tsp.core.PatternResult.{Failure, Stay, Success}
import ru.itclover.tsp.core.Time.TimeExtractor
import ru.itclover.tsp.phases.NoState
import ru.itclover.tsp.phases.NumericPhases.{NumericPhaseParser, SymbolNumberExtractor}


package object core {

  case class TestEvent[T](result: PatternResult[T], time: DateTime = DateTime.now())

  case class TestPhase[T]() extends Pattern[TestEvent[T], NoState, T] {
    override def initialState: NoState = NoState.instance

    override def apply(event: TestEvent[T], state: NoState) = event.result -> NoState.instance

    override def format(event: TestEvent[T], state: NoState) = s"TestPhase($event)"
  }

  class ConstResult[T](result: PatternResult[T]) extends Pattern[TestEvent[T], Unit, T] {
    override def apply(v1: TestEvent[T], v2: Unit): (PatternResult[T], Unit) = result -> Unit

    override def initialState: Unit = ()
  }


  implicit val doubleTestEvent = new TimeExtractor[TestEvent[Double]] {
    override def apply(event: TestEvent[Double]) = event.time
  }

  implicit val boolTestEvent = new TimeExtractor[TestEvent[Boolean]] {
    override def apply(event: TestEvent[Boolean]) = event.time
  }

  val probe = TestEvent(Success(1))

  val alwaysSuccess: Pattern[TestEvent[Int], Unit, Int] = new ConstResult(Success(0))
  val alwaysFailure: Pattern[TestEvent[Int], Unit, Int] = new ConstResult(Failure("failed"))
  val alwaysStay: Pattern[TestEvent[Int], Unit, Int] = new ConstResult(Stay)

  val t = DateTime.now()
  val times = t.minusMillis(11000) :: t.minusMillis(10000) :: t.minusMillis(9000) :: t.minusMillis(8000) ::
    t.minusMillis(7000) :: t.minusMillis(6000) :: t.minusMillis(5000) :: t.minusMillis(4000) :: t.minusMillis(3000) ::
    t.minusMillis(2000) :: t.minusMillis(1000) :: t :: Nil

  private val staySuccessRes = Seq(Stay, Success(1.0), Stay, Success(2.0), Success(1.0), Success(3.0), Failure("Test"), Success(4.0))
  val staySuccesses = for((t, res) <- times.take(staySuccessRes.length).zip(staySuccessRes)) yield TestEvent(res, t)

  def getTestingEvents[T](innerResults: Seq[PatternResult[T]]) = for(
    (t, res) <- times.take(innerResults.length).zip(innerResults)
  ) yield TestEvent(res, t)

  val failsRes = Seq(Stay, Success(1.0), Failure("Test"), Success(2.0), Failure("Test"), Success(1.0), Stay, Failure("Test"), Success(3.0), Failure("Test"), Stay)
  val fails = for((t, res) <- times.take(failsRes.length).zip(failsRes)) yield TestEvent(res, t)


  def fakeMapper[Event, PhaseOut](p: Pattern[Event, _, PhaseOut]) = FakeMapper[Event, PhaseOut]()

  def runRule[Event, Out](rule: Pattern[Event, _, Out], events: Seq[Event]): Vector[PatternResult.TerminalResult[Out]] = {
    val mapResults = fakeMapper(rule)
    events
      .foldLeft(PatternMapper(rule, mapResults)) { case (machine, event) => machine(event) }
      .result
  }

}
