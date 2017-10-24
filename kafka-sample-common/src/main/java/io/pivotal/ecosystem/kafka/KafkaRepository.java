/*
 Copyright (C) 2016-Present Pivotal Software, Inc. All rights reserved.

 This program and the accompanying materials are made available under
 the terms of the under the Apache License, Version 2.0 (the "License”);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package io.pivotal.ecosystem.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.KafkaDataListener;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class KafkaRepository {

    private KafkaServiceInfo info;


    public KafkaRepository(KafkaServiceInfo info) {
        this.info = info;
    }

    private Map<String, Object> senderProperties() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, info.getBootstrapServers());
        props.put(ProducerConfig.RETRIES_CONFIG, info.getRetries());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, info.getBatchSize());
        props.put(ProducerConfig.LINGER_MS_CONFIG, info.getLingerMs());
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, info.getBufferMemory());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, info.getKeySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, info.getValueSerializer());

        return props;
    }

    private KafkaTemplate<Integer, String> getTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(senderProperties()));
    }

    public ListenableFuture<SendResult<Integer, String>> sendMessage(String message) throws ExecutionException, InterruptedException {
        KafkaTemplate<Integer, String> template = getTemplate();
        ListenableFuture<SendResult<Integer, String>> future = template.send(info.getTopicName(), message);
        template.flush();
        return future;
    }


    private KafkaMessageListenerContainer<Integer, String> createContainer(
            ContainerProperties containerProps) {
        Map<String, Object> props = consumerProps();
        DefaultKafkaConsumerFactory<Integer, String> cf =
                new DefaultKafkaConsumerFactory<>(props);
        return new KafkaMessageListenerContainer<>(cf, containerProps);
    }

    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, info.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, info.getGroudId());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, info.isEnableAutoCommit());
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, info.getAutoCommitInterval());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, info.getSessionTimeout());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, info.getKeyDeserializer());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, info.getValueDeserializer());
        return props;
    }

    KafkaMessageListenerContainer<Integer, String> getConsumer(KafkaDataListener listener) throws ExecutionException, InterruptedException {
        ContainerProperties containerProps = new ContainerProperties(info.getTopicName());
        containerProps.setMessageListener(listener);
        return createContainer(containerProps);
    }
}