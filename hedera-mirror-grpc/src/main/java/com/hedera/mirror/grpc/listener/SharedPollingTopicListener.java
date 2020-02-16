package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Subscription;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;

import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;

@Named
@Log4j2
public class SharedPollingTopicListener implements TopicListener {

    private final GrpcProperties grpcProperties;
    private final TopicMessageRepository topicMessageRepository;
    private final InstantToLongConverter instantToLongConverter;
    private final Flux<TopicMessage> poller;

    public SharedPollingTopicListener(GrpcProperties grpcProperties, ListenerProperties listenerProperties,
                                      TopicMessageRepository topicMessageRepository,
                                      InstantToLongConverter instantToLongConverter) {
        this.grpcProperties = grpcProperties;
        this.topicMessageRepository = topicMessageRepository;
        this.instantToLongConverter = instantToLongConverter;

        Duration frequency = listenerProperties.getPollingFrequency();
        PollingContext context = new PollingContext();
        Scheduler scheduler = Schedulers.newSingle("shared-poll", true);

        poller = Flux.defer(() -> poll(context))
                .repeatWhen(Repeat.times(Long.MAX_VALUE)
                        .fixedBackoff(Duration.ofMillis(frequency.toMillis()))
                        .jitter(Jitter.random(0.1))
                        .withBackoffScheduler(scheduler))
                .name("shared-poll")
                .metrics()
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Starting to poll every {}ms", frequency.toMillis()))
                .doOnComplete(() -> log.info("Completed polling"))
                .doOnCancel(() -> log.info("Cancelled polling"))
                .share();
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return poller.filter(t -> filterMessage(t, filter))
                .doOnSubscribe(s -> log.info("Listening for messages: {}", filter));
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        Instant instant = context.getLastConsensusTimestamp();
        Long consensusTimestamp = instantToLongConverter.convert(instant);
        Pageable pageable = PageRequest.of(0, grpcProperties.getMaxPageSize());
        log.debug("Querying for messages after: {}", instant);

        return Flux.defer(() -> Flux
                .fromIterable(topicMessageRepository.findByConsensusTimestampGreaterThan(consensusTimestamp, pageable)))
                .name("findByConsensusTimestampGreaterThan")
                .metrics()
                .doOnCancel(context::onComplete)
                .doOnComplete(context::onComplete)
                .doOnSubscribe(context::onSubscribe);
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return filter.getRealmNum() == message.getRealmNum() &&
                filter.getTopicNum() == message.getTopicNum() &&
                !filter.getStartTime().isAfter(message.getConsensusTimestampInstant());
    }

    @Data
    private class PollingContext {

        private final AtomicLong count = new AtomicLong();
        private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private volatile Instant lastConsensusTimestamp = Instant.now();

        void onNext(TopicMessage topicMessage) {
            lastConsensusTimestamp = topicMessage.getConsensusTimestampInstant();
        }

        void onSubscribe(Subscription subscription) {
            count.set(0L);
            stopwatch.reset().start();
            log.debug("Executing query for messages after timestamp {}", lastConsensusTimestamp);
        }

        void onComplete() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            var rate = elapsed > 0 ? (int) (1000.0 * count.get() / elapsed) : 0;
            log.debug("Finished querying with {} messages in {} ({}/s)", count, stopwatch, rate);
        }
    }
}
