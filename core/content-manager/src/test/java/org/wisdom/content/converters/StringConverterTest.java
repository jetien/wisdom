/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.content.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class StringConverterTest {

    @Test
    public void testFromString() throws Exception {
        assertThat(StringConverter.INSTANCE.fromString("hello")).isEqualTo("hello");
        assertThat(StringConverter.INSTANCE.fromString("")).isEqualTo("");
        assertThat(StringConverter.INSTANCE.fromString(null)).isEqualTo(null);
    }

    @Test
    public void testGetType() throws Exception {
        assertThat(StringConverter.INSTANCE.getType()).isEqualTo(String.class);
    }
}