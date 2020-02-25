package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.mirror.importer.domain.EntityId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.Entities;

public class EntityRepositoryTest extends AbstractRepositoryTest {

    @AfterEach
    @BeforeEach
    void beforeAndAfter() {
        entityRepository.deleteAll();
        entityRepository.clearCaches();
    }

    @Test
    void findByPrimaryKey() {
        int entityTypeId = getAccountEntityTypeId();

        Entities autoRenewAccount = new Entities();
        autoRenewAccount.setEntityTypeId(entityTypeId);
        autoRenewAccount.setEntityShard(0L);
        autoRenewAccount.setEntityRealm(0L);
        autoRenewAccount.setEntityNum(101L);
        autoRenewAccount = entityRepository.save(autoRenewAccount);

        Entities proxyEntity = new Entities();
        proxyEntity.setEntityTypeId(entityTypeId);
        proxyEntity.setEntityShard(0L);
        proxyEntity.setEntityRealm(0L);
        proxyEntity.setEntityNum(100L);
        proxyEntity = entityRepository.save(proxyEntity);

        Entities entity = new Entities();
        entity.setAutoRenewPeriod(100L);
        entity.setAutoRenewAccount(autoRenewAccount);
        entity.setDeleted(true);
        entity.setEd25519PublicKeyHex("0123456789abcdef");
        entity.setEntityNum(5L);
        entity.setEntityRealm(4L);
        entity.setEntityShard(3L);
        entity.setEntityTypeId(entityTypeId);
        entity.setExpiryTimeNs(300L);
        entity.setKey("key".getBytes());
        entity.setProxyAccountId(proxyEntity.getId());
        entity.setSubmitKey("SubmitKey".getBytes());
        entity = entityRepository.save(entity);

        assertThat(entityRepository
                .findByPrimaryKey(entity.getEntityShard(), entity.getEntityRealm(), entity.getEntityNum()))
                .get()
                .isEqualTo(entity);

        entity.setExpiryTimeNs(600L);
        entity = entityRepository.save(entity);

        assertThat(entityRepository
                .findByPrimaryKey(entity.getEntityShard(), entity.getEntityRealm(), entity.getEntityNum()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findEntityIdByNativeIds() {
        var entity = new Entities();
        entity.setEntityTypeId(getAccountEntityTypeId());
        entity.setEntityShard(1L);
        entity.setEntityRealm(2L);
        entity.setEntityNum(3L);
        final var expected = entityRepository.save(entity);

        var entityId = entityRepository.findEntityIdByNativeIds(entity.getEntityShard(), entity.getEntityRealm(),
                entity.getEntityNum()).get();

        assertAll(() -> assertEquals(expected.getId(), entityId.getId())
                ,() -> assertEquals(expected.getEntityShard(), entityId.getEntityShard())
                ,() -> assertEquals(expected.getEntityRealm(), entityId.getEntityRealm())
                ,() -> assertEquals(expected.getEntityNum(), entityId.getEntityNum())
        );
    }

    @Test
    void cacheEntityId() {
        // Cache but don't save.
        var entityId = new EntityId(1L, 2L, 3L, 4L, getAccountEntityTypeId());
        entityRepository.cache(entityId);

        var retrieved = entityRepository.findEntityIdByNativeIds(entityId.getEntityShard(), entityId.getEntityRealm(),
                entityId.getEntityNum()).get();

        assertAll(() -> assertEquals(retrieved.getId(), entityId.getId())
                ,() -> assertEquals(retrieved.getEntityShard(), entityId.getEntityShard())
                ,() -> assertEquals(retrieved.getEntityRealm(), entityId.getEntityRealm())
                ,() -> assertEquals(retrieved.getEntityNum(), entityId.getEntityNum())
        );
    }

    @Test
    void saveAndCacheEntityId() {
        var entity = getTestEntity();

        // Save+cache, then delete from repository but keep cached ID.
        entityRepository.saveAndCacheEntityId(entity);
        entityRepository.deleteAll();

        var retrieved = entityRepository.findEntityIdByNativeIds(entity.getEntityShard(), entity.getEntityRealm(),
                entity.getEntityNum()).get();

        assertAll(() -> assertEquals(retrieved.getId(), entity.getId())
                ,() -> assertEquals(retrieved.getEntityShard(), entity.getEntityShard())
                ,() -> assertEquals(retrieved.getEntityRealm(), entity.getEntityRealm())
                ,() -> assertEquals(retrieved.getEntityNum(), entity.getEntityNum())
        );
    }

    private int getAccountEntityTypeId() {
        return entityTypeRepository.findByName("account").get().getId();
    }

    private Entities getTestEntity() {
        var entityTypeId = entityTypeRepository.findByName("account").get().getId();
        var entity = new Entities();
        entity.setEntityTypeId(entityTypeId);
        entity.setEntityShard(1L);
        entity.setEntityRealm(2L);
        entity.setEntityNum(3L);
        return entity;
    }
}
