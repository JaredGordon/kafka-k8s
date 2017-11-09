package io.pivotal.ecosystem.kafka;

import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    @Bean
    public KafkaServiceInfo kafkaServiceInfo() {
        KafkaServiceInfo info = new KafkaServiceInfo();

        info.setBootstrapServers("35.196.82.166:9092");
        info.setRetries(0);
        info.setBatchSize(16384);
        info.setLingerMs(1);
        info.setBufferMemory(33554432);
        info.setKeySerializer(IntegerSerializer.class);
        info.setValueSerializer(StringSerializer.class);
        info.setTopicName("test");

        info.setGroudId("pivotal");
        info.setEnableAutoCommit(true);
        info.setAutoCommitInterval(100);
        info.setSessionTimeout(15000);
        info.setKeyDeserializer(IntegerDeserializer.class);
        info.setValueDeserializer(StringDeserializer.class);

        return info;
    }
}