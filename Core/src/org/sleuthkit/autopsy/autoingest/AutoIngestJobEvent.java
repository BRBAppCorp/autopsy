/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.autoingest;

import java.io.Serializable;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * RJCTODO
 */
@Immutable
abstract class AutoIngestJobEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final AutoIngestJob job;

    /**
     * RJCTODO
     *
     */
    AutoIngestJobEvent(AutoIngestManager.Event eventSubType, AutoIngestJob job) {
        super(eventSubType.toString(), null, null);
        this.job = job;
    }

    /**
     * RJCTODO
     * @return 
     */
    AutoIngestJob getJob() {
        return this.job;
    }
    
}
