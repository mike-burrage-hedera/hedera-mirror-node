package com.hedera.mirror.grpc.domain;

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

import java.time.Instant;
import java.util.Comparator;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.springframework.data.annotation.Id;

@Builder
@Value
public class TopicMessage implements Comparable<TopicMessage> {

    @Id
    private Instant consensusTimestamp;

    @ToString.Exclude
    private byte[] message;

    private int realmNum;

    @ToString.Exclude
    private byte[] runningHash;

    private long sequenceNumber;

    private int topicNum;

    @Override
    public int compareTo(TopicMessage other) {
        return Comparator.nullsFirst(Comparator.comparingLong(TopicMessage::getSequenceNumber)).compare(this, other);
    }
}
