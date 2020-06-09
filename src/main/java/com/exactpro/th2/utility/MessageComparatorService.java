/*
 * Copyright $today.year-2020 Exactpro (Exactpro Systems Limited)
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

import static com.exactpro.sf.comparison.ComparisonUtil.getStatusType;
import static com.google.protobuf.TextFormat.shortDebugString;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.comparison.ComparatorSettings;
import com.exactpro.sf.comparison.ComparisonResult;
import com.exactpro.sf.comparison.Formatter;
import com.exactpro.sf.comparison.MessageComparator;
import com.exactpro.sf.scriptrunner.StatusType;
import com.exactpro.th2.MessageWrapper;
import com.exactpro.th2.ProtoToIMessageConverter;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareFilterVsMessagesRequest;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareFilterVsMessagesResponse;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageRequest;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageResponse;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageResult;
import com.exactpro.th2.utility.messagecomparator.grpc.CompareMessageVsMessageTaskOrBuilder;
import com.exactpro.th2.utility.messagecomparator.grpc.ComparisonEntry;
import com.exactpro.th2.utility.messagecomparator.grpc.ComparisonEntry.Builder;
import com.exactpro.th2.utility.messagecomparator.grpc.ComparisonEntryStatus;
import com.exactpro.th2.utility.messagecomparator.grpc.ComparisonEntryType;
import com.exactpro.th2.utility.messagecomparator.grpc.RxMessageComparatorServiceGrpc.MessageComparatorServiceImplBase;
import com.google.protobuf.MessageOrBuilder;

import io.reactivex.Single;

public class MessageComparatorService extends MessageComparatorServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageComparatorService.class);

    private static final ProtoToIMessageConverter CONVERTER = new ProtoToIMessageConverter(new DefaultMessageFactoryProxy(), null, null);

    @Override
    public Single<CompareFilterVsMessagesResponse> compareFilterVsMessages(Single<CompareFilterVsMessagesRequest> request) {
        return super.compareFilterVsMessages(request);
    }

    @Override
    public Single<CompareMessageVsMessageResponse> compareMessageVsMessage(Single<CompareMessageVsMessageRequest> request) {
        return request.doOnEvent(MessageComparatorService::loggingMessageVsMessageStart)
                .flattenAsFlowable(CompareMessageVsMessageRequest::getComparisonTasksList)
                .map(MessagePair::from)
                .map(ComparisonMessagesResult::compare)
                .map(ComparisonMessagesResult::convert)
                .toList()
                .map(list -> CompareMessageVsMessageResponse.newBuilder()
                        .addAllComparisonResults(list)
                        .build())
                .doOnEvent(MessageComparatorService::loggingMessageVsMessageEnd);
    }

    private static void loggingMessageVsMessageEnd(MessageOrBuilder compareMessageVsMessageResponse, Throwable throwable) {
        if (throwable == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("End comparison message vs message {}", shortDebugString(compareMessageVsMessageResponse));
            }
        } else {
            LOGGER.error("Internal exception during comparation message vs message", throwable);
        }
    }

    private static void loggingMessageVsMessageStart(MessageOrBuilder compareMessageVsMessageRequest, Throwable throwable) {
        if (throwable == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Start comparison message vs message {}", shortDebugString(compareMessageVsMessageRequest));
            }
        } else {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Emit request problem", throwable);
            }
        }
    }

    private static class MessagePair {
        /** This message can contains filter */
        protected final MessageWrapper first;
        /** This conatins values of simple type */
        protected final MessageWrapper second;

        private MessagePair(MessageWrapper first, MessageWrapper second) {
            this.first = first;
            this.second = second;
        }

        public static MessagePair from(CompareMessageVsMessageTaskOrBuilder messageVsMessageTask) {
            return new MessagePair(convertToMessagePair(messageVsMessageTask.getFirst()),
                    convertToMessagePair(messageVsMessageTask.getSecond()));
        }

        private static MessageWrapper convertToMessagePair(Message protoMessage) {
            return CONVERTER.fromProtoMessage(protoMessage, false);
        }
    }

    private static class ComparisonMessagesResult extends MessagePair {
        protected final ComparisonResult comparisonResult;

        private ComparisonMessagesResult(MessageWrapper first, MessageWrapper second, ComparisonResult comparisonResult) {
            super(first, second);
            this.comparisonResult = comparisonResult;
        }

        public CompareMessageVsMessageResult convert() {
            return CompareMessageVsMessageResult.newBuilder()
                    .setFirstMessageId(first.getMessageId())
                    .setSecondMessageId(second.getMessageId())
                    .setComparisonResult(putSubComparisons(ComparisonEntry.newBuilder(), comparisonResult)
                            .setStatus(convertToProto(getStatusType(comparisonResult))))
                    .build();
        }

        public static ComparisonMessagesResult compare(MessagePair messagePair) {
            ComparatorSettings comparatorSettings = new ComparatorSettings();
            ComparisonResult comparisonResult = MessageComparator.compare(messagePair.second, messagePair.first, comparatorSettings, false);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Comparion of message {} vs message {}" + comparisonResult,
                        shortDebugString(messagePair.first.getMessageId()), shortDebugString(messagePair.second.getMessageId()));
            }
            return new ComparisonMessagesResult(messagePair.first, messagePair.second, comparisonResult);
        }

        @Nullable
        private static ComparisonEntryStatus convertToProto(StatusType statusType) {
            if (statusType == null) {
                return null;
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
            return putSubComparisons(ComparisonEntry.newBuilder()
                    .setFirst(Formatter.formatExpected(comparisonResult))
                    .setSecond(Objects.toString(comparisonResult.getActual(), null))
                    .setStatus(convertToProto(comparisonResult.getStatus()))
                    .setType(comparisonResult.hasResults()
                            ? ComparisonEntryType.COLLECTION
                            : ComparisonEntryType.FIELD), comparisonResult)
                    .build();
            //            verificationEntry.setKey(isKey(comparisonResult, metaContainer));
            //            verificationEntry.setOperation(resolveOperation(comparisonResult));
            //            MetaContainer children = metaContainer == null ? null
            //                    : metaContainer.get(comparisonResult.getName()) == null ? metaContainer
            //                    : metaContainer.get(comparisonResult.getName()).get(0);
        }

        private static Builder putSubComparisons(Builder comparisonResultBuilder, ComparisonResult comparisonResult) {
            if (comparisonResult.hasResults()) {
                comparisonResult.getResults().forEach((fieldName, fieldComparison) -> comparisonResultBuilder.putFields(fieldName, convertToComparisonEntry(fieldComparison)));
            }
            return comparisonResultBuilder;
        }
    }
}
