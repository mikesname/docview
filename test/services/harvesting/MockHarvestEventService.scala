package services.harvesting

import java.time.Instant

import models.HarvestEvent.HarvestEventType
import models.{HarvestEvent, UserProfile}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future.{successful => immediate}

case class MockHarvestEventService(events: ListBuffer[HarvestEvent] = collection.mutable.ListBuffer.empty) extends HarvestEventService {

  override def get(repoId: String, datasetId: Option[String], jobId: Option[String]) =
    immediate(events.filter(e => e.repoId == repoId
      && (datasetId.isDefined && datasetId.contains(e.datasetId))
      && (jobId.isDefined && jobId.contains(e.jobId))).sortBy(_.created))

  override def save(repoId: String, datasetId: String, jobId: String, info: Option[String])(implicit userOpt: Option[UserProfile]) = {
    events += HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Started, info, Instant.now())
    immediate(new HarvestEventHandle {
      override def close() = immediate {
        events += HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Completed, info, Instant.now())
        ()
      }

      override def cancel() = immediate {
        events += HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Cancelled, info, Instant.now())
        ()
      }

      override def error(t: Throwable) = immediate {
        events += HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Errored, info, Instant.now())
        ()
      }
    })
  }
}
