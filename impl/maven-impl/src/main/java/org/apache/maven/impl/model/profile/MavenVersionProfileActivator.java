/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.impl.model.profile;

import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Activation;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.services.BuilderProblem;
import org.apache.maven.api.services.ModelProblem;
import org.apache.maven.api.services.ModelProblemCollector;
import org.apache.maven.api.services.model.ProfileActivationContext;
import org.apache.maven.api.services.model.ProfileActivator;

/**
 * Determines profile activation based on the Maven runtime version.
 *
 * @see Activation#getMaven()
 */
@Named("maven-version")
@Singleton
public class MavenVersionProfileActivator implements ProfileActivator {

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getActivation();

        if (activation == null) {
            return false;
        }

        String maven = activation.getMaven();

        if (maven == null) {
            return false;
        }

        String version = context.getSystemProperty("maven.version");

        if (version == null || version.isEmpty()) {
            problems.add(
                    BuilderProblem.Severity.ERROR,
                    ModelProblem.Version.BASE,
                    "Failed to determine Maven version for profile " + profile.getId(),
                    activation.getLocation("maven"));
            return false;
        }

        try {
            return JdkVersionProfileActivator.isJavaVersionCompatible(maven, version);
        } catch (NumberFormatException e) {
            problems.add(
                    BuilderProblem.Severity.WARNING,
                    ModelProblem.Version.BASE,
                    "Failed to determine Maven activation for profile " + profile.getId()
                            + " due invalid Maven version: '" + version + "'",
                    activation.getLocation("maven"),
                    e);
            return false;
        }
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getActivation();
        return activation != null && activation.getMaven() != null;
    }
}
