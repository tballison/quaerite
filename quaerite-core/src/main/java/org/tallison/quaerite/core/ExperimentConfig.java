/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tallison.quaerite.core;


import org.apache.commons.lang3.StringUtils;

public class ExperimentConfig {

    public static final int DEFAULT_NUM_THREADS = 6;

    private int numThreads = DEFAULT_NUM_THREADS;
    private String idField = StringUtils.EMPTY;
    private long sleep = -1;

    public int getNumThreads() {
        return numThreads;
    }

    //returns id field if customized in experiment config
    //or empty string if nothing was specified
    public String getIdField() {
        return idField;
    }
    public void setIdField(String idField) {
        this.idField = idField;
    }
    public long getSleep() {
        return sleep;
    }
    public void setSleep(long sleep) {
        this.sleep = sleep;
    }
    


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExperimentConfig that = (ExperimentConfig) o;

        if (numThreads != that.numThreads) return false;
        if (sleep != that.sleep) return false;
        return idField != null ? idField.equals(that.idField) : that.idField == null;
    }

    @Override
    public int hashCode() {
        int result = numThreads;
        result = 31 * result + (idField != null ? idField.hashCode() : 0);
        result = 31 * result + (int) (sleep ^ (sleep >>> 32));
        return result;
    }
}
