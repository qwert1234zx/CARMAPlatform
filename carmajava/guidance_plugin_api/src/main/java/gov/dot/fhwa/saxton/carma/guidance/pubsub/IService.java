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

package gov.dot.fhwa.saxton.carma.guidance.pubsub;

import gov.dot.fhwa.saxton.carma.rosutils.*;

/**
 * Generic interface for supporting calls to a remote service. Designed to share an underlying
 * resource between many clients to avoid duplication.
 *
 * @param <T> Type parameter for service request message
 * @param <S> Type parameter for service response message
 */
public interface IService<T, S> {
    /**
     * Perform a synchronous call for the service, waiting until the response is returned and executing the appropriate
     * callback.
     * 
     * @param request The message for the request
     * @param callback The callback to execute on message failure or success
     */
    void call(T request, OnServiceResponseCallback<S> callback);

    /**
    * Generate a new (empty) request-type message instance to have it's fields populated before transmission.
    *
    * @return An empty message of type T
    */
    T newMessage();

    /**
     * Notify this IService instance's parent of this instance's closure. This will not necessarily close the underlying
     * resources associated with this IService.
     */
    void close();
}
