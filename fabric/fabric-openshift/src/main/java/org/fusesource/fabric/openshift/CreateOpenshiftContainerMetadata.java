/*
 * Copyright (C) FuseSource, Inc.
 *   http://fusesource.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.fusesource.fabric.openshift;

import org.fusesource.fabric.api.CreateContainerBasicMetadata;

public class CreateOpenshiftContainerMetadata extends CreateContainerBasicMetadata<CreateOpenshiftContainerOptions> {

    private final String domainId;
    private final String uuid;
    private final String createLog;

    public CreateOpenshiftContainerMetadata(String domainId, String uuid, String createLog) {
        this.domainId = domainId;
        this.uuid = uuid;
        this.createLog = createLog;
    }


    public String getDomainId() {
        return domainId;
    }

    public String getUuid() {
        return uuid;
    }

    public String getCreateLog() {
        return createLog;
    }
}
