package it.bitrock.dvs.streams.topologies

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}

import it.bitrock.dvs.model.avro._
import it.bitrock.dvs.streams.CommonSpecUtils._
import it.bitrock.dvs.streams.TestValues._
import it.bitrock.kafkacommons.serialization.ImplicitConversions._
import it.bitrock.testcommons.Suite
import net.manub.embeddedkafka.schemaregistry._
import net.manub.embeddedkafka.schemaregistry.streams.EmbeddedKafkaStreams
import org.apache.kafka.common.serialization.Serde
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, OptionValues}

import scala.concurrent.duration._
import scala.util.Random

class FlightInterpolatedListStreamSpec extends Suite with AnyWordSpecLike with EmbeddedKafkaStreams with OptionValues {
  implicit private val clock: Clock                     = Clock.systemUTC()
  final private val consumerPollTimeout: FiniteDuration = 10.seconds

  "FlightInterpolatedListStream" should {
    "produce interpolated record of FlightReceivedList in interpolated topic" in ResourceLoaner.withFixture {
      case Resource(embeddedKafkaConfig, appConfig, kafkaStreamsOptions, _) =>
        implicit val embKafkaConfig: EmbeddedKafkaConfig = embeddedKafkaConfig
        implicit val keySerde: Serde[String]             = kafkaStreamsOptions.stringKeySerde

        val expectedMessages = (consumerPollTimeout / appConfig.kafka.topology.interpolationInterval).toInt

        val firstFlightListTime = Instant.now(clock)
        val firstFlightList     = FlightInterpolatedListStreamSpec.records(firstFlightListTime)

        val secondFlightListTime = firstFlightListTime.plusSeconds(consumerPollTimeout.toSeconds)
        val secondFlightList     = FlightInterpolatedListStreamSpec.records(secondFlightListTime)

        val topology =
          FlightInterpolatedListStream.buildTopology(appConfig, kafkaStreamsOptions).map { case (topology, _) => topology }
        val (firstGroup, secondGroup) = ResourceLoaner.runAll(topology) { _ =>
          publishToKafka(
            appConfig.kafka.topology.flightEnRouteListTopic.name,
            firstFlightListTime.toEpochMilli.toString,
            firstFlightList
          )

          val firstMessages = consumeNumberKeyedMessagesFromTopics[String, FlightInterpolatedList](
            topics = Set(appConfig.kafka.topology.flightInterpolatedListTopic.name),
            number = expectedMessages,
            timeout = consumerPollTimeout
          )

          publishToKafka(
            appConfig.kafka.topology.flightEnRouteListTopic.name,
            secondFlightListTime.toEpochMilli.toString,
            secondFlightList
          )

          val secondMessages = consumeNumberKeyedMessagesFromTopics[String, FlightInterpolatedList](
            topics = Set(appConfig.kafka.topology.flightInterpolatedListTopic.name),
            number = expectedMessages,
            timeout = consumerPollTimeout
          )

          (
            firstMessages(appConfig.kafka.topology.flightInterpolatedListTopic.name),
            secondMessages(appConfig.kafka.topology.flightInterpolatedListTopic.name)
          )
        }

        val endTestTime = Instant.now(clock)

        firstGroup should have size expectedMessages
        firstGroup.foreach(compareMessages(firstFlightListTime, firstFlightList))

        secondGroup should have size expectedMessages
        secondGroup.foreach(compareMessages(secondFlightListTime, secondFlightList))

        def compareMessages(
            messageTime: Instant,
            originalMessage: FlightReceivedList
        ): ((String, FlightInterpolatedList)) => Unit = {
          case (k, v) =>
            k.toLong shouldBe <(endTestTime.toEpochMilli)
            k.toLong shouldBe >(messageTime.minus(1, ChronoUnit.SECONDS).toEpochMilli)

            val confrontedFlights = v.elements.sortBy(_.icaoNumber) zip originalMessage.elements.sortBy(_.icaoNumber)

            confrontedFlights.foreach { case (interpolated, original) => compareFlights(k, interpolated, original) }
        }
    }
  }

  private def compareFlights(messageKey: String, interpolated: FlightInterpolated, original: FlightReceived): Assertion = {
    interpolated.iataNumber shouldBe original.iataNumber
    interpolated.icaoNumber shouldBe original.icaoNumber

    interpolated.geography should not be original.geography
    interpolated.geography.latitude should not be original.geography.latitude
    interpolated.geography.longitude should not be original.geography.longitude

    interpolated.geography.altitude shouldBe original.geography.altitude
    interpolated.geography.direction shouldBe original.geography.direction
    interpolated.speed shouldBe original.speed
    interpolated.updated shouldBe original.updated
    interpolated.interpolatedUntil.toEpochMilli shouldBe messageKey.toLong
    interpolated.updated.isBefore(interpolated.interpolatedUntil) shouldBe true
  }
}

object FlightInterpolatedListStreamSpec {

  def records(time: Instant): FlightReceivedList =
    FlightReceivedList(
      List(
        FlightReceivedEvent.copy(
          iataNumber = s"iata1-${Random.alphanumeric take 4 mkString ""}",
          icaoNumber = s"icao1-${Random.alphanumeric take 4 mkString ""}",
          updated = time.minus(1, ChronoUnit.SECONDS),
          geography = GeographyInfo(15d, 30d, 12345d, 13.4),
          speed = Random.nextDouble() * 1000d
        ),
        FlightReceivedEvent.copy(
          iataNumber = s"iata2-${Random.alphanumeric take 4 mkString ""}",
          icaoNumber = s"icao2-${Random.alphanumeric take 4 mkString ""}",
          updated = time,
          geography = GeographyInfo(45d, 10d, 11045d, 73.4),
          speed = Random.nextDouble() * 1000d
        ),
        FlightReceivedEvent.copy(
          iataNumber = s"iata3-${Random.alphanumeric take 4 mkString ""}",
          icaoNumber = s"icao3-${Random.alphanumeric take 4 mkString ""}",
          updated = time.plus(1, ChronoUnit.SECONDS),
          geography = GeographyInfo(-3.4d, -78.02d, 9165d, -9.2),
          speed = Random.nextDouble() * 1000d
        )
      )
    )
}
