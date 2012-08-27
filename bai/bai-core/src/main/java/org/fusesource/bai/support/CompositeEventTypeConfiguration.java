/*
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.bai.support;

import org.apache.camel.Predicate;
import org.fusesource.bai.EventTypeConfiguration;

import java.util.List;

/**
*/
public class CompositeEventTypeConfiguration extends EventTypeConfiguration {
    private final List<EventTypeConfiguration> configs;

    public CompositeEventTypeConfiguration(List<EventTypeConfiguration> configs) {
        this.configs = configs;
    }

    @Override
    public void configureEventFlag(String value) {
        for (EventTypeConfiguration config : configs) {
            config.configureEventFlag(value);
        }
    }

    @Override
    public void addFilter(Predicate predicate) {
        for (EventTypeConfiguration config : configs) {
            config.addFilter(predicate);
        }
    }

    @Override
    public void addIncludeRegex(String regex) {
        for (EventTypeConfiguration config : configs) {
            config.addIncludeRegex(regex);
        }
    }
}