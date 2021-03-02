/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.util;

import static com.exactpro.sf.comparison.ComparisonUtil.getStatusType;
import static com.exactpro.sf.comparison.Formatter.formatForHtml;
import static com.google.protobuf.TextFormat.shortDebugString;

import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.comparison.ComparatorSettings;
import com.exactpro.sf.comparison.ComparisonResult;
import com.exactpro.sf.comparison.Formatter;
import com.exactpro.sf.scriptrunner.StatusType;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.sailfish.utils.MessageWrapper;
import com.exactpro.th2.sailfish.utils.ProtoToIMessageConverter;
import com.exactpro.th2.util.grpc.CompareFilterVsMessagesRequest;
import com.exactpro.th2.util.grpc.CompareFilterVsMessagesResponse;
import com.exactpro.th2.util.grpc.CompareMessageVsMessageRequest;
import com.exactpro.th2.util.grpc.CompareMessageVsMessageResponse;
import com.exactpro.th2.util.grpc.CompareMessageVsMessageResult;
import com.exactpro.th2.util.grpc.CompareMessageVsMessageTaskOrBuilder;
import com.exactpro.th2.util.grpc.ComparisonEntry;
import com.exactpro.th2.util.grpc.ComparisonEntry.Builder;
import com.exactpro.th2.util.grpc.ComparisonEntryStatus;
import com.exactpro.th2.util.grpc.ComparisonEntryType;
import com.exactpro.th2.util.grpc.ComparisonSettings;
import com.exactpro.th2.util.grpc.RxMessageComparatorGrpc.MessageComparatorImplBase;
import com.google.protobuf.MessageOrBuilder;

import io.reactivex.Single;

public class MessageComparator extends MessageComparatorImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageComparator.class);

    private static final ProtoToIMessageConverter CONVERTER = new ProtoToIMessageConverter(new DefaultMessageFactoryProxy(), null, null);

    @Override
    public Single<CompareFilterVsMessagesResponse> compareFilterVsMessages(Single<CompareFilterVsMessagesRequest> request) {
        return super.compareFilterVsMessages(request);
    }

    @Override
    public Single<CompareMessageVsMessageResponse> compareMessageVsMessage(Single<CompareMessageVsMessageRequest> request) {
        return request.doOnEvent(MessageComparator::loggingMessageVsMessageStart)
                .flattenAsFlowable(CompareMessageVsMessageRequest::getComparisonTasksList)
                .map(MessagePair::from)
                .map(ComparisonMessagesResult::compare)
                .map(ComparisonMessagesResult::convert)
                .toList()
                .map(list -> CompareMessageVsMessageResponse.newBuilder()
                        .addAllComparisonResults(list)
                        .build())
                .doOnEvent(MessageComparator::loggingMessageVsMessageEnd);
    }

    private static void loggingMessageVsMessageEnd(MessageOrBuilder compareMessageVsMessageResponse, Throwable throwable) {
        if (throwable == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("End comparison message vs message {}", shortDebugString(compareMessageVsMessageResponse));
            }
        } else {
            LOGGER.error("Internal exception during comparation message vs message", throwable);
            throw new RuntimeException(throwable); //FIXME: transfer internal error
        }
    }

    private static void loggingMessageVsMessageStart(MessageOrBuilder compareMessageVsMessageRequest, Throwable throwable) {
        if (throwable == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Start comparison message vs message {}", shortDebugString(compareMessageVsMessageRequest));
            }
        } else {
            LOGGER.error("Emit request problem", throwable);
            throw new RuntimeException(throwable); //FIXME: transfer internal error
        }
    }

    private static class MessagePair {
        /** This message can contains filter */
        protected final MessageWrapper first;
        /** This conatins values of simple type */
        protected final MessageWrapper second;
        protected final ComparisonSettings comparisonSettings;

        protected MessagePair(MessageWrapper first, MessageWrapper second, ComparisonSettings comparisonSettings) {
            this.first = first;
            this.second = second;
            this.comparisonSettings = comparisonSettings;
        }

        protected MessagePair(MessagePair messagePair) {
            this(messagePair.first, messagePair.second, messagePair.comparisonSettings);
        }

        public static MessagePair from(CompareMessageVsMessageTaskOrBuilder messageVsMessageTask) {
            return new MessagePair(convertToMessagePair(messageVsMessageTask.getFirst()),
                    convertToMessagePair(messageVsMessageTask.getSecond()), messageVsMessageTask.getSettings());
        }

        private static MessageWrapper convertToMessagePair(Message protoMessage) {
            return CONVERTER.fromProtoMessage(protoMessage, false);
        }
    }

    private static class ComparisonMessagesResult extends MessagePair {
        protected final ComparisonResult comparisonResult;

        private ComparisonMessagesResult(MessagePair messagePair, ComparisonResult comparisonResult) {
            super(messagePair);
            this.comparisonResult = comparisonResult;
        }

        public CompareMessageVsMessageResult convert() {
            return CompareMessageVsMessageResult.newBuilder()
                    .setFirstMessageId(first.getMessageId())
                    .setSecondMessageId(second.getMessageId())
                    .setComparisonResult(convertAndPutSubComparisons(ComparisonEntry.newBuilder(), comparisonResult)
                            .setStatus(convertToProto(getStatusType(comparisonResult))))
                    .build();
        }

        public static ComparisonMessagesResult compare(MessagePair messagePair) {
            ComparatorSettings comparatorSettings = createSettings(messagePair.comparisonSettings);
            ComparisonResult comparisonResult = com.exactpro.sf.comparison.MessageComparator.compare(messagePair.second, messagePair.first, comparatorSettings, false);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Comparion of message {} vs message {}" + comparisonResult,
                        shortDebugString(messagePair.first.getMessageId()), shortDebugString(messagePair.second.getMessageId()));
            }
            return new ComparisonMessagesResult(messagePair, comparisonResult);
        }

        private static ComparatorSettings createSettings(ComparisonSettings protoSettings) {
            return new ComparatorSettings()
                    .setIgnoredFields(protoSettings.getIgnoreFieldsList().stream()
                            .collect(Collectors.toUnmodifiableSet()));
        }

        @Nullable
        private static ComparisonEntryStatus convertToProto(StatusType statusType) {
            if (statusType == null) {
                throw new IllegalArgumentException("Status can't be null");
            }

            switch (statusType) {
            case PASSED:
                return ComparisonEntryStatus.PASSED;
            case FAILED:
                return ComparisonEntryStatus.FAILED;
            case NA:
                return ComparisonEntryStatus.NA;
            default:
                throw new IllegalArgumentException("Unsupportable status type '" + statusType + '\'');
            }
        }

        private static ComparisonEntry convertToComparisonEntry(ComparisonResult comparisonResult) {
            Builder builder = ComparisonEntry.newBuilder()
                    .setFirst(Formatter.formatExpected(comparisonResult))
                    .setSecond(formatForHtml(Objects.toString(comparisonResult.getActual(), null), false))
                    .setType(comparisonResult.hasResults()
                            ? ComparisonEntryType.COLLECTION
                            : ComparisonEntryType.FIELD);
            if (comparisonResult.getStatus() != null) {
                builder.setStatus(convertToProto(comparisonResult.getStatus()));
            }
            return convertAndPutSubComparisons(builder, comparisonResult)
                    .build();
            //            verificationEntry.setKey(isKey(comparisonResult, metaContainer));
            //            verificationEntry.setOperation(resolveOperation(comparisonResult));
            //            MetaContainer children = metaContainer == null ? null
            //                    : metaContainer.get(comparisonResult.getName()) == null ? metaContainer
            //                    : metaContainer.get(comparisonResult.getName()).get(0);
        }

        private static Builder convertAndPutSubComparisons(Builder comparisonResultBuilder, ComparisonResult comparisonResult) {
            if (comparisonResult.hasResults()) {
                comparisonResult.getResults().forEach((fieldName, fieldComparison) -> comparisonResultBuilder.putFields(fieldName, convertToComparisonEntry(fieldComparison)));
            }
            return comparisonResultBuilder;
        }
    }
}
