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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;

import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.retriever.TopicMessageRetriever;

@Named
@Log4j2
@RequiredArgsConstructor
public class PollingTopicListener implements TopicListener {

    private final GrpcProperties grpcProperties;
    private final ListenerProperties listenerProperties;
    private final TopicMessageRetriever topicMessageRetriever;
    private final Scheduler scheduler = Schedulers
            .newParallel("poll", 4 * Runtime.getRuntime().availableProcessors(), true);

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        PollingContext context = new PollingContext(filter);
        Duration frequency = listenerProperties.getPollingFrequency();

        return Flux.defer(() -> poll(context))
                .delaySubscription(Duration.ofMillis(frequency.toMillis()), scheduler)
                .repeatWhen(Repeat.times(Long.MAX_VALUE)
                        .fixedBackoff(Duration.ofMillis(frequency.toMillis()))
                        .jitter(Jitter.random(0.1))
                        .withBackoffScheduler(scheduler))
                .name("poll")
                .metrics()
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Starting to poll every {}ms: {}", frequency.toMillis(), filter));
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        TopicMessageFilter filter = context.getFilter();
        TopicMessage last = context.getLast();
        long limit = filter.hasLimit() ? filter.getLimit() - context.getCount().get() : 0;
        Instant startTime = last != null ? last.getConsensusTimestampInstant().plusNanos(1) : filter.getStartTime();

        TopicMessageFilter newFilter = TopicMessageFilter.builder()
                .endTime(filter.getEndTime())
                .limit(limit)
                .realmNum(filter.getRealmNum())
                .subscriberId(filter.getSubscriberId())
                .startTime(startTime)
                .topicNum(filter.getTopicNum())
                .build();

        return topicMessageRetriever.retrieve(newFilter);
    }

    @Data
    private class PollingContext {

        private final TopicMessageFilter filter;
        private final AtomicLong count = new AtomicLong(0L);
        private volatile TopicMessage last;

        void onNext(TopicMessage topicMessage) {
            last = topicMessage;
            count.incrementAndGet();
        }
    }
}
