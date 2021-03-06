package it.bitrock.dvs.streams.topologies

import it.bitrock.dvs.model.avro._
import it.bitrock.dvs.streams.CommonSpecUtils._
import it.bitrock.dvs.streams.TestValues._
import it.bitrock.kafkacommons.serialization.ImplicitConversions._
import it.bitrock.testcommons.Suite
import net.manub.embeddedkafka.schemaregistry._
import net.manub.embeddedkafka.schemaregistry.streams.EmbeddedKafkaStreams
import org.apache.kafka.common.serialization.Serde
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpecLike

class TotalStreamsSpec extends Suite with AnyWordSpecLike with EmbeddedKafkaStreams with OptionValues {
  "TotalStreams" should {
    "produce TotalFlight elements in the appropriate topic" in ResourceLoaner.withFixture {
      case Resource(embeddedKafkaConfig, appConfig, kafkaStreamsOptions, topologies) =>
        implicit val embKafkaConfig: EmbeddedKafkaConfig = embeddedKafkaConfig
        implicit val keySerde: Serde[String]             = kafkaStreamsOptions.stringKeySerde

        val receivedRecords = ResourceLoaner.runAll(topologies(TotalTopologies)) { _ =>
          val flightMessages = 0 to 9 map { key =>
            key.toString -> FlightReceivedEvent.copy(
              iataNumber = key.toString,
              icaoNumber = key.toString
            )
          }
          publishToKafka(appConfig.kafka.topology.flightReceivedTopic.name, flightMessages)
          publishToKafka(dummyFlightReceivedForcingSuppression(appConfig.kafka.topology.flightReceivedTopic.name))
          val messagesMap = consumeNumberKeyedMessagesFromTopics[String, CountFlight](
            topics = Set(appConfig.kafka.topology.totalFlightTopic.name),
            number = 1,
            timeout = ConsumerPollTimeout
          )
          messagesMap(appConfig.kafka.topology.totalFlightTopic.name).map(_._2).head
        }
        receivedRecords.eventCount shouldBe ExpectedTotalFlightResult
    }

    "produce TotalAirline elements in the appropriate topic" in ResourceLoaner.withFixture {
      case Resource(embeddedKafkaConfig, appConfig, kafkaStreamsOptions, topologies) =>
        implicit val embKafkaConfig: EmbeddedKafkaConfig = embeddedKafkaConfig
        implicit val keySerde: Serde[String]             = kafkaStreamsOptions.stringKeySerde

        val receivedRecords = ResourceLoaner.runAll(topologies(TotalTopologies)) { _ =>
          val flightMessages = 0 to 9 map { key =>
            key.toString -> FlightReceivedEvent.copy(
              iataNumber = key.toString,
              icaoNumber = key.toString,
              airline = AirlineInfo(CodeAirlineArray(key), "", 0)
            )
          }
          publishToKafka(appConfig.kafka.topology.flightReceivedTopic.name, flightMessages)
          publishToKafka(dummyFlightReceivedForcingSuppression(appConfig.kafka.topology.flightReceivedTopic.name))
          val messagesMap = consumeNumberKeyedMessagesFromTopics[String, CountAirline](
            topics = Set(appConfig.kafka.topology.totalAirlineTopic.name),
            number = 1,
            timeout = ConsumerPollTimeout
          )
          messagesMap(appConfig.kafka.topology.totalAirlineTopic.name).map(_._2).head
        }
        receivedRecords.eventCount shouldBe ExpectedTotalAirlineResult
    }
  }
}
