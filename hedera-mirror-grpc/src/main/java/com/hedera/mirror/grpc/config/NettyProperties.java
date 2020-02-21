package com.hedera.mirror.grpc.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import javax.validation.constraints.Min;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NettyProperties {
    @Min(1024) // 64 kb
    private int flowControlWindow = 64 * 1024;

    @Min(1)
    private int maxConcurrentCallsPerConnection = 5;

    @Min(8) // 6 kb
    private int maxMessageSize = 6 * 1024;

    @Min(8) // 1 kb
    private int maxMetadataSize = 1024;

    @Min(1)
    private int bossEventLoopGroupPoolCount = 1;

    @Min(8)
    private int workerEventLoopGroupPoolCount = 100;

    private boolean isNioChannelType = true;
}