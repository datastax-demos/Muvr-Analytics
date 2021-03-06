package io.muvr.analytics.basic

import java.io.{IOException, File}
import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.analytics.cassandra.JournalKey
import io.muvr.UserId
import io.muvr.exercise._
import org.apache.commons.io.FileUtils
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import scala.collection.JavaConversions._

trait TrainingExampleWriter {
  val Exercise_Id_Name_Separator = "/"

  def extractNameFrom(exerciseId: String) =
    exerciseId.split(Exercise_Id_Name_Separator).last

  def extractGroupFrom(exerciseId: String) =
    exerciseId.split(Exercise_Id_Name_Separator).head

  def writeExample(exerciseId: String, data: Seq[SensorData]): Unit
}

/**
 * Write the examples to CSV files in the output directory. The writer will generate random UUIDs as filenames for the
 * different examples.
 */
case class CSVTrainingExampleWriter(outDir: String) extends TrainingExampleWriter {

  import com.github.tototoshi.csv._

  def writeExample(datasetId: String, data: Seq[SensorData]): Unit = {
    val id = UUID.randomUUID().toString
    val csvFile = new File(s"$outDir/$id.csv")
    writeExample(datasetId, data, csvFile)
  }

  private def writeExample(datasetId: String, data: Seq[SensorData], target: File): Unit = {
    val exerciseName = extractNameFrom(datasetId)
    val exerciseGroup = extractGroupFrom(datasetId)
    val writer = CSVWriter.open(target)

    val csvData = data.map {
      case Threed(x, y, z) ⇒ List(exerciseGroup, exerciseName, x, y, z)
    }

    writer.writeAll(csvData)
    writer.close()
  }
}

trait LabelMapper {
  def labelMapper(label: String): Option[String]
}

trait IdentityLabelMapper extends LabelMapper {
  override def labelMapper(label: String) = Option(label)
}

trait ActivityLabelMapper extends LabelMapper {
  override def labelMapper(label: String) = label match {
    case "arms/biceps-curl" ⇒ Some("/slacking") // trainer (walking mostly)
    case exercise ⇒ Some("/exercise") // lifter user
    case _ ⇒ None
  }
}

trait UserFilter {

  def filterUsers(user: UserId): Boolean
}

trait AllUsers extends UserFilter {

  override def filterUsers(user: UserId) = true
}

trait SingleUserFilter extends UserFilter {

  def trainingsUser: String

  override def filterUsers(user: UserId) = user == UserId(trainingsUser)
}

trait DataPreparationPipeline extends LabelMapper with UserFilter {

  type RawData = RDD[(JournalKey, Any)]

  type RefinedData

  def prepareData(rawData: RawData)(implicit sc: SparkContext): RefinedData
}

trait CSVHelpers {
  def prepareCSVWriter(rootDir: String) = {
    val csvWriter = CSVTrainingExampleWriter(rootDir)

    FileUtils.deleteDirectory(new File(rootDir))
    Files.createDirectories(Paths.get(rootDir))

    csvWriter
  }
}

trait RawRDDHelpers {

  def groupExamplesByUser(input: RDD[(JournalKey, Any)]) = {
    input.flatMap { case (JournalKey(UserExerciseProcessorPersistenceId(userId), _, _), EntireResistanceExerciseSession(id, _, examples)) ⇒
      println(userId + " " + id + " " + examples.length)
      examples.map(e ⇒ userId → e)
    }.flatMap { case (userId, example) ⇒
      example.correct.map(correct ⇒ (userId, correct.resistanceExercise, example.fusedSensorData))
    }.groupBy { case (userId, exercise, data) ⇒
      userId
    }
  }
}

trait UserCenteredDataPreparationPipeline extends DataPreparationPipeline with RawRDDHelpers{

  type RefinedData = RDD[(String, Iterable[(String, Seq[SensorData])])]

  def prepareData(rawData: RawData)(implicit sc: SparkContext): RefinedData = {
    groupExamplesByUser(rawData)
      .map { case (userId, exercises) ⇒
      println("------------ USER " + userId)
      exercises.groupBy(_._2).foreach {
        case (exerciseName, data) ⇒
          println(exerciseName + " - " + data.size)
      }

      val examples = exercises.flatMap {
        case (_, exercise, listOfFusedData) ⇒
          labelMapper(exercise.id).map(_ → listOfFusedData.flatMap(data ⇒ data.data))
      }
      (userId, examples)
    }
      .filter { case (userId, exercise) ⇒ filterUsers(userId) }
      .map { case (userId, exercise) ⇒ userId.id.toString -> exercise }
  }
}

object ActivityDataPreparationPipeline
  extends UserCenteredDataPreparationPipeline
  with SingleUserFilter
  with ActivityLabelMapper
  with RawRDDHelpers with Serializable{

  val trainingsUser = "9d1a8b72-1651-4d42-acb9-7df4d4ac4cf1"
}

object ExerciseDataPreparationPipeline
  extends UserCenteredDataPreparationPipeline
  with AllUsers
  with IdentityLabelMapper
  with RawRDDHelpers with Serializable{

  val trainingsUser = "9d1a8b72-1651-4d42-acb9-7df4d4ac4cf1"
}

object TrainingMain extends CSVHelpers{

  import SparkConfiguration._
  import cassandrax._

  def removeDirectory(directory: String): Unit =
    try {
      FileUtils.deleteDirectory(new File(directory))
    } catch {
      case e: IOException =>
        println(s"Failed to delete directory $directory. $e")
    }

  def main(args: Array[String]) {

    implicit val sc = new SparkContext(sparkConf)
    val allExamples = sc.eventTable()
    val activityOutputFolder = "/data/spark-csv-activity"
    val exerciseOutputFolder = "/data/spark-csv-exercises"

    removeDirectory(activityOutputFolder)
    removeDirectory(exerciseOutputFolder)
    
    ActivityDataPreparationPipeline
      .prepareData(allExamples)
      .map{
      case (userId, examples) ⇒
        val writer = prepareCSVWriter(s"$activityOutputFolder/datasets/$userId")
        examples.foreach {
          case (label, data) ⇒
            writer.writeExample(label, data)
        }
        userId
      }
      .saveAsTextFile(s"$activityOutputFolder/users")

    ExerciseDataPreparationPipeline
      .prepareData(allExamples)
      .map{
      case (userId, examples) ⇒
        val writer = prepareCSVWriter(s"$exerciseOutputFolder/datasets/$userId")
        examples.foreach {
          case (label, data) ⇒
            writer.writeExample(label, data)
        }
        userId
    }
      .saveAsTextFile(s"$exerciseOutputFolder/users")

    System.exit(0)
  }
}
