/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.utility;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.ListValue;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.MessageID;
import com.exactpro.th2.infra.grpc.MessageMetadata;
import com.exactpro.th2.infra.grpc.Value;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageRequest;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageResponse;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageTask;
import com.exactpro.th2.utility.messagecomparator.grpc.ComparisonSettings;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

public class MessageComparatorServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageComparatorServiceTest.class);
    private static final String IGNORED_FIELD = "IgnoredField";
    private MessageComparatorService service;

    @Before
    public void before() {
        service = new MessageComparatorService();
    }

    @Test
    public void emptyFieldValue() {
        Message filled = createMessage()
                .putFields("Filled", createValue("fill"))
                .build();
        Message notFilled = createMessage().build();

        compareMessageVsMessageCall(service, filled, notFilled)
                .assertNoErrors();

        compareMessageVsMessageCall(service, notFilled, filled)
                .assertNoErrors();
    }

    @Test
    public void compareMessageVsMessage() {
        service.compareMessageVsMessage(Single.just(CompareMessageVsMessageRequest.newBuilder()
                .addComparisonTasks(CompareMessageVsMessageTask.newBuilder()
                        .setFirst(createMessage()
                                .putFields("Null", createValue("null"))
                                .build())
                        .setSecond(createMessage()
                                .build())
                        .setSettings(ComparisonSettings.newBuilder()
                                .addIgnoreFields(IGNORED_FIELD)
                                .build())
                        .build())
                .build()))
        .subscribe();
    }

    private static TestObserver<CompareMessageVsMessageResponse> compareMessageVsMessageCall(MessageComparatorService service, Message first, Message second) {
        return service.compareMessageVsMessage(Single.just(CompareMessageVsMessageRequest.newBuilder()
                .addComparisonTasks(CompareMessageVsMessageTask.newBuilder()
                        .setFirst(first)
                        .setSecond(second)
                        .build())
                .build()))
                .doOnSuccess(response -> LOGGER.info("Response result {}", response))
                .test();
    }

    private static Message.Builder createMessage() {
        return Message.newBuilder().setMetadata(createMessageMetadata())
                .putFields("FieldSimple", createValue("1"))
                .putFields(IGNORED_FIELD, createValue("6"))
                .putFields("FieldList", createValue("2", "3a", "b4", "c5d"));
    }

    private static MessageMetadata createMessageMetadata() {
        return MessageMetadata.newBuilder()
                .setMessageType("MessageType")
                .setId(MessageID.newBuilder()
                        .setSequence(123)
                        .setConnectionId(ConnectionID.newBuilder()
                                .setSessionAlias("session")
                                .build())
                        .build())
                .build();
    }

    private static Value createValue(String value) {
        return Value.newBuilder()
                .setSimpleValue(value)
                .build();
    }

    private static Value createValue(String ... values) {
        return Value.newBuilder()
                .setListValue(ListValue.newBuilder()
                        .addAllValues(Stream.of(values)
                                .map(MessageComparatorServiceTest::createValue)
                                .collect(Collectors.toList()))
                        .build())
                .build();
    }
}