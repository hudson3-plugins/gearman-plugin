/*
 *
 * Copyright 2013 Hewlett-Packard Development Company, L.P.
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
 *
 */
package hudson.plugins.gearman;

import hudson.model.Run;
import hudson.security.ACL;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.gearman.client.GearmanJobResult;
import org.gearman.client.GearmanJobResultImpl;
import org.gearman.worker.AbstractGearmanFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * This is a gearman function to set a jenkins build description
 *
 *
 * @author Khai Do
 */
public class SetDescriptionWorker extends AbstractGearmanFunction {

    private static final Logger logger = LoggerFactory
            .getLogger(Constants.PLUGIN_LOGGER_NAME);


    /*
     * The Gearman Function
     * @see org.gearman.worker.AbstractGearmanFunction#executeFunction()
     */
    @Override
    public GearmanJobResult executeFunction() {

        // check job results
        boolean jobResult = false;
        String jobResultMsg = "";

        String decodedData;
        // decode json
        try {
            decodedData = new String((byte[]) this.data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Unsupported encoding exception in argument");
        }

        // convert parameters passed in from client to hash map
        Gson gson = new Gson();
        Map<String, String> data = gson.fromJson(decodedData,
                new TypeToken<Map<String, String>>() {
                }.getType());

        // get build description
        String buildDescription = data.get("html_description");
        // get build id
        String jobName = data.get("name");
        String buildNumber = data.get("number");
        if (!jobName.isEmpty() && !buildNumber.isEmpty()) {
            // find build then update its description
            Run<?, ?> build = GearmanPluginUtil.findBuild(jobName, Integer.parseInt(buildNumber));
            if (build != null) {
                //SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
                SecurityContext oldContext = impersonate(ACL.SYSTEM);
                try {
                    try {
                        build.setDescription(buildDescription);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Unable to set description for "
                                + jobName + ": " + buildNumber);
                    }
                } finally {
                    SecurityContextHolder.setContext(oldContext);
                }
                jobResultMsg = "Description for Jenkins build " + buildNumber + " was updated to " + buildDescription;
                jobResult = true;
            } else {
                throw new IllegalArgumentException("Cannot find build number "
                        + buildNumber);
            }
        } else {
            throw new IllegalArgumentException("Build id is invalid or not specified");
        }

        GearmanJobResult gjr = new GearmanJobResultImpl(this.jobHandle, jobResult,
                jobResultMsg.getBytes(), null, null, 0, 0);
        return gjr;
    }

    private SecurityContext impersonate(Authentication auth) {
        SecurityContext old = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(new NonSerializableSecurityContext(auth));
        return old;
    }

    public static class NonSerializableSecurityContext implements SecurityContext {

        private transient Authentication authentication;

        public NonSerializableSecurityContext() {
        }

        public NonSerializableSecurityContext(Authentication authentication) {
            this.authentication = authentication;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SecurityContext) {
                SecurityContext test = (SecurityContext) obj;

                if ((this.getAuthentication() == null) && (test.getAuthentication() == null)) {
                    return true;
                }

                if ((this.getAuthentication() != null) && (test.getAuthentication() != null)
                        && this.getAuthentication().equals(test.getAuthentication())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Authentication getAuthentication() {
            return authentication;
        }

        @Override
        public int hashCode() {
            if (this.authentication == null) {
                return -1;
            } else {
                return this.authentication.hashCode();
            }
        }

        @Override
        public void setAuthentication(Authentication authentication) {
            this.authentication = authentication;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());

            if (this.authentication == null) {
                sb.append(": Null authentication");
            } else {
                sb.append(": Authentication: ").append(this.authentication);
            }

            return sb.toString();
        }
    }
}
