package ru.itclover.spark

import org.apache.spark.sql.streaming.GroupState
import ru.itclover.streammachine.AbstractStateMachineMapper
import ru.itclover.streammachine.core.PhaseParser
import ru.itclover.streammachine.core.PhaseResult.{Success, TerminalResult}

import scala.concurrent.duration.Duration

class SparkMachineMapper[Group, Event, State, Out](override val phaseParser: PhaseParser[Event, State, Out])
  extends AbstractStateMachineMapper[Event, State, Out]
    with ((Group, Iterator[Event], GroupState[Seq[State]]) => Iterator[Out])
    with Serializable {

  def apply(groupKey: Group, events: Iterator[Event], state: GroupState[Seq[State]]): Iterator[Out] = {

    if (state.hasTimedOut) {
      state.remove()
    }

    val (newStates, results) =
      events.toStream.foldLeft(
        (state.getOption.getOrElse(Vector.empty), Iterator.empty.asInstanceOf[Iterator[TerminalResult[Out]]])
      ) {
        case ((sts, ress), event) =>
          val (newResults, updatedStates) = process(event, sts)
          updatedStates -> (ress ++ newResults)
      }

    updateStateWithNewValue(state, newStates)
    results.collect { case Success(x) => x }
  }

  protected def updateStateWithNewValue(groupState: GroupState[Seq[State]], newState: Seq[State]): Unit = {
    if (newState.nonEmpty) {
      groupState.update(newState)
    } else {
      groupState.remove()
    }
  }
}

class TimedSparkMachineMapper[Group, Event, State, Out](timeout: Duration)(override val phaseParser: PhaseParser[Event, State, Out])
  extends SparkMachineMapper[Group, Event, State, Out](phaseParser) {
  private val millis = timeout.toMillis

  override protected def updateStateWithNewValue(groupState: GroupState[Seq[State]], newState: Seq[State]): Unit = {
    super.updateStateWithNewValue(groupState, newState)
    groupState.setTimeoutDuration(millis)
  }
}