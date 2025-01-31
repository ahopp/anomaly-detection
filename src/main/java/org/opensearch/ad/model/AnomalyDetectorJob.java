/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ad.model;

import static org.opensearch.ad.settings.AnomalyDetectorSettings.DEFAULT_AD_JOB_LOC_DURATION_SECONDS;
import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.ad.util.ParseUtils;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import com.amazon.opendistroforelasticsearch.commons.authuser.User;
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.ScheduledJobParameter;
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.schedule.CronSchedule;
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.schedule.IntervalSchedule;
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.schedule.Schedule;
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.schedule.ScheduleParser;
import com.google.common.base.Objects;

/**
 * Anomaly detector job.
 */
public class AnomalyDetectorJob implements Writeable, ToXContentObject, ScheduledJobParameter {
    enum ScheduleType {
        CRON,
        INTERVAL
    }

    public static final String PARSE_FIELD_NAME = "AnomalyDetectorJob";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        AnomalyDetectorJob.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String ANOMALY_DETECTOR_JOB_INDEX = ".opendistro-anomaly-detector-jobs";
    public static final String NAME_FIELD = "name";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";
    public static final String LOCK_DURATION_SECONDS = "lock_duration_seconds";

    private static final String SCHEDULE_FIELD = "schedule";
    private static final String WINDOW_DELAY_FIELD = "window_delay";
    private static final String IS_ENABLED_FIELD = "enabled";
    private static final String ENABLED_TIME_FIELD = "enabled_time";
    private static final String DISABLED_TIME_FIELD = "disabled_time";
    public static final String USER_FIELD = "user";

    private final String name;
    private final Schedule schedule;
    private final TimeConfiguration windowDelay;
    private final Boolean isEnabled;
    private final Instant enabledTime;
    private final Instant disabledTime;
    private final Instant lastUpdateTime;
    private final Long lockDurationSeconds;
    private final User user;

    public AnomalyDetectorJob(
        String name,
        Schedule schedule,
        TimeConfiguration windowDelay,
        Boolean isEnabled,
        Instant enabledTime,
        Instant disabledTime,
        Instant lastUpdateTime,
        Long lockDurationSeconds,
        User user
    ) {
        this.name = name;
        this.schedule = schedule;
        this.windowDelay = windowDelay;
        this.isEnabled = isEnabled;
        this.enabledTime = enabledTime;
        this.disabledTime = disabledTime;
        this.lastUpdateTime = lastUpdateTime;
        this.lockDurationSeconds = lockDurationSeconds;
        this.user = user;
    }

    public AnomalyDetectorJob(StreamInput input) throws IOException {
        name = input.readString();
        if (input.readEnum(AnomalyDetectorJob.ScheduleType.class) == ScheduleType.CRON) {
            schedule = new CronSchedule(input);
        } else {
            schedule = new IntervalSchedule(input);
        }
        windowDelay = IntervalTimeConfiguration.readFrom(input);
        isEnabled = input.readBoolean();
        enabledTime = input.readInstant();
        disabledTime = input.readInstant();
        lastUpdateTime = input.readInstant();
        lockDurationSeconds = input.readLong();
        if (input.readBoolean()) {
            user = new User(input);
        } else {
            user = null;
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder
            .startObject()
            .field(NAME_FIELD, name)
            .field(SCHEDULE_FIELD, schedule)
            .field(WINDOW_DELAY_FIELD, windowDelay)
            .field(IS_ENABLED_FIELD, isEnabled)
            .field(ENABLED_TIME_FIELD, enabledTime.toEpochMilli())
            .field(LAST_UPDATE_TIME_FIELD, lastUpdateTime.toEpochMilli())
            .field(LOCK_DURATION_SECONDS, lockDurationSeconds);
        if (disabledTime != null) {
            xContentBuilder.field(DISABLED_TIME_FIELD, disabledTime.toEpochMilli());
        }
        if (user != null) {
            xContentBuilder.field(USER_FIELD, user);
        }
        return xContentBuilder.endObject();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        output.writeString(name);
        if (schedule instanceof CronSchedule) {
            output.writeEnum(ScheduleType.CRON);
        } else {
            output.writeEnum(ScheduleType.INTERVAL);
        }
        schedule.writeTo(output);
        windowDelay.writeTo(output);
        output.writeInstant(enabledTime);
        output.writeInstant(disabledTime);
        output.writeInstant(lastUpdateTime);
        output.writeLong(lockDurationSeconds);
        if (user != null) {
            output.writeBoolean(true); // user exists
            user.writeTo(output);
        } else {
            output.writeBoolean(false); // user does not exist
        }
    }

    public static AnomalyDetectorJob parse(XContentParser parser) throws IOException {
        String name = null;
        Schedule schedule = null;
        TimeConfiguration windowDelay = null;
        // we cannot set it to null as isEnabled() would do the unboxing and results in null pointer exception
        Boolean isEnabled = Boolean.FALSE;
        Instant enabledTime = null;
        Instant disabledTime = null;
        Instant lastUpdateTime = null;
        Long lockDurationSeconds = DEFAULT_AD_JOB_LOC_DURATION_SECONDS;
        User user = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case SCHEDULE_FIELD:
                    schedule = ScheduleParser.parse(parser);
                    break;
                case WINDOW_DELAY_FIELD:
                    windowDelay = TimeConfiguration.parse(parser);
                    break;
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case ENABLED_TIME_FIELD:
                    enabledTime = ParseUtils.toInstant(parser);
                    break;
                case DISABLED_TIME_FIELD:
                    disabledTime = ParseUtils.toInstant(parser);
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = ParseUtils.toInstant(parser);
                    break;
                case LOCK_DURATION_SECONDS:
                    lockDurationSeconds = parser.longValue();
                    break;
                case USER_FIELD:
                    user = User.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AnomalyDetectorJob(
            name,
            schedule,
            windowDelay,
            isEnabled,
            enabledTime,
            disabledTime,
            lastUpdateTime,
            lockDurationSeconds,
            user
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AnomalyDetectorJob that = (AnomalyDetectorJob) o;
        return Objects.equal(getName(), that.getName())
            && Objects.equal(getSchedule(), that.getSchedule())
            && Objects.equal(isEnabled(), that.isEnabled())
            && Objects.equal(getEnabledTime(), that.getEnabledTime())
            && Objects.equal(getDisabledTime(), that.getDisabledTime())
            && Objects.equal(getLastUpdateTime(), that.getLastUpdateTime())
            && Objects.equal(getLockDurationSeconds(), that.getLockDurationSeconds());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, schedule, isEnabled, enabledTime, lastUpdateTime);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Schedule getSchedule() {
        return schedule;
    }

    public TimeConfiguration getWindowDelay() {
        return windowDelay;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public Instant getEnabledTime() {
        return enabledTime;
    }

    public Instant getDisabledTime() {
        return disabledTime;
    }

    @Override
    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public Long getLockDurationSeconds() {
        return lockDurationSeconds;
    }

    public User getUser() {
        return user;
    }
}
