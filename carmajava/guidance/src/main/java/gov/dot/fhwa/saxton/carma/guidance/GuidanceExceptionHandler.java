/*
 * Copyright (C) 2018 LEIDOS.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package gov.dot.fhwa.saxton.carma.guidance;

import gov.dot.fhwa.saxton.carma.guidance.util.ILogger;
import gov.dot.fhwa.saxton.carma.guidance.util.LoggerManager;

/**
 * Provides facilities to handle caught/uncaught exception signal a guidance-wide PANIC event in
 * the event of unrecoverable conditions arising.
 */
public class GuidanceExceptionHandler {
    
    private ILogger log = LoggerManager.getLogger();
    private GuidanceStateMachine stateMachine;
    
    public GuidanceExceptionHandler(GuidanceStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    // Handle an uncaught exception, which will result in guidance shutdown.
    public void handleException(Throwable e) {
        log.error("Global guidance exception handler caught an uncaught exception from thread: " + Thread.currentThread().getName(), e);
        stateMachine.processEvent(GuidanceEvent.PANIC);
    }

    // 
    public void handleException(String s, Throwable e) {
        log.error(s, e);
        stateMachine.processEvent(GuidanceEvent.PANIC);
    }
}