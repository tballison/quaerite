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

import org.tallison.quaerite.core.features.Feature;

public class ServerConnection implements Feature {

    public static final String NAME = "serverConnection";
    private final String url;
    private final String user;
    private final String password;

    public ServerConnection(String url) {
        this(url, null, null);
    }

    public ServerConnection(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public String getURL() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServerConnection that = (ServerConnection) o;

        if (url != null ? !url.equals(that.url) : that.url != null)
            return false;
        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        return password != null ? password.equals(that.password) : that.password == null;
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (user != null ? user.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServerConnectionProperties{" +
                "url='" + url + '\'' +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object deepCopy() {
        return new ServerConnection(this.url, this.user, this.password);
    }
}
