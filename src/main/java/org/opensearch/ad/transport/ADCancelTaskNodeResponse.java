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
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opensearch.ad.transport;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.ad.task.ADTaskCancellationState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

public class ADCancelTaskNodeResponse extends BaseNodeResponse {

    private ADTaskCancellationState state;

    public ADCancelTaskNodeResponse(DiscoveryNode node, ADTaskCancellationState state) {
        super(node);
        this.state = state;
    }

    public ADCancelTaskNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.state = in.readEnum(ADTaskCancellationState.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(state);
    }

    public static ADCancelTaskNodeResponse readNodeResponse(StreamInput in) throws IOException {
        return new ADCancelTaskNodeResponse(in);
    }

    public ADTaskCancellationState getState() {
        return state;
    }
}
