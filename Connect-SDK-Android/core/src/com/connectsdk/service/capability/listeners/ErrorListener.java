/*
 * ErrorListener
 * Connect SDK
 * 
 * Copyright (c) 2014 LG Electronics.
 * Created by Jeffrey Glenn on 07 Mar 2014
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

package com.connectsdk.service.capability.listeners;

import com.connectsdk.service.command.ServiceCommandError;

/**
 * Generic asynchronous operation response error handler block. In all cases, you will get a valid ServiceCommandError object. Connect SDK will make all attempts to give you the lowest-level error possible. In cases where an error is generated by Connect SDK, an enumerated error code (ConnectStatusCode) will be present on the ServiceCommandError object.
 *
 * ###Low-level error example
 * ####Situation
 * Connect SDK receives invalid XML from a device, generating a parsing error
 *
 * ####Result
 * Connect SDK will call the ErrorListener and pass off the ServiceCommandError generated during parsing of the XML.
 *
 * ###High-level error example
 * ####Situation
 * An invalid value is passed to a device capability method
 *
 * ####Result
 * The capability method will immediately invoke the ErrorListener and pass off an ServiceCommandError object with a status code of ConnectStatusCodeArgumentError.
 *
 * @param error ServiceCommandError object describing the nature of the problem. Error descriptions are not localized and mostly intended for developer use. It is not recommended to display most error descriptions in UI elements.
 */
public interface    ErrorListener {

    /**
     * Method to return the error that was generated. Will pass an error object with a helpful status code and error message.
     * 
     * @param error ServiceCommandError describing the error
     */
    public void onError(ServiceCommandError error);
}
