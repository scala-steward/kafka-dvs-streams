package it.bitrock.dvs.streams.config

import java.net.URI

import scala.concurrent.duration.FiniteDuration

final case class KafkaConfig(
    applicationId: String,
    bootstrapServers: String,
    schemaRegistryUrl: URI,
    topology: TopologyConfig,
    enableInterceptors: Boolean,
    monitoring: Monitoring
)

final case class TopologyConfig(
    flightRawTopic: TopicMetadata,
    airportRawTopic: TopicMetadata,
    airlineRawTopic: TopicMetadata,
    cityRawTopic: TopicMetadata,
    airplaneRawTopic: TopicMetadata,
    flightReceivedTopic: TopicMetadata,
    flightReceivedPartitionerTopic: TopicMetadata,
    flightReceivedListTopic: TopicMetadata,
    flightLandedListTopic: TopicMetadata,
    flightEnRouteListTopic: TopicMetadata,
    topArrivalAirportTopic: TopicMetadata,
    topDepartureAirportTopic: TopicMetadata,
    topSpeedTopic: TopicMetadata,
    topAirlineTopic: TopicMetadata,
    totalFlightTopic: TopicMetadata,
    totalAirlineTopic: TopicMetadata,
    aggregationTimeWindowSize: FiniteDuration,
    aggregationTotalTimeWindowSize: FiniteDuration,
    aggregationTimeWindowGrace: FiniteDuration,
    commitInterval: FiniteDuration,
    cacheMaxSizeBytes: Long,
    maxRequestSize: Long,
    threadsAmount: Int
)

final case class TopicMetadata(name: String, partitions: Int)

final case class Monitoring(flightReceivedList: MonitoringConf)
final case class MonitoringConf(allowedDelay: FiniteDuration, topic: String, delayTopic: String)
