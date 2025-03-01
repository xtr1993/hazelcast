/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.sql.impl.type.converter;

import com.hazelcast.core.HazelcastJsonValue;

/**
 * Converter for {@link java.lang.String} type.
 */
public final class StringConverter extends AbstractStringConverter {

    public static final StringConverter INSTANCE = new StringConverter();

    private StringConverter() {
        super(ID_STRING);
    }

    @Override
    public Class<?> getValueClass() {
        return String.class;
    }

    @Override
    protected String cast(Object val) {
        return (String) val;
    }

    @Override
    public HazelcastJsonValue asJson(final Object val) {
        return new HazelcastJsonValue(cast(val));
    }
}
