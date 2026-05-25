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
package org.apache.maven.model.profile.activation;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.api.model.Activation;
import org.apache.maven.api.model.Profile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.apache.maven.model.building.ModelProblem.Version;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.profile.ProfileActivationContext;

/**
 * Determines profile activation based on the Maven runtime version.
 *
 * @see Activation#getMaven()
 * @deprecated use {@code org.apache.maven.api.services.ModelBuilder} instead
 */
@Named("maven-version")
@Singleton
@Deprecated(since = "4.0.0")
public class MavenVersionProfileActivator implements ProfileActivator {

    @Override
    public boolean isActive(org.apache.maven.model.Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getDelegate().getActivation();

        if (activation == null) {
            return false;
        }

        String maven = activation.getMaven();

        if (maven == null) {
            return false;
        }

        String version = context.getSystemProperties().get("maven.version");

        if (version == null || version.isEmpty()) {
            problems.add(new ModelProblemCollectorRequest(Severity.ERROR, Version.BASE)
                    .setMessage("Failed to determine Maven version for profile " + profile.getId())
                    .setLocation(activation.getLocation("maven")));
            return false;
        }

        if (maven.startsWith("!")) {
            return !version.startsWith(maven.substring(1));
        } else if (isRange(maven)) {
            try {
                VersionRange range = VersionRange.createFromVersionSpec(maven);
                return range.containsVersion(new DefaultArtifactVersion(version));
            } catch (InvalidVersionSpecificationException e) {
                problems.add(new ModelProblemCollectorRequest(Severity.WARNING, Version.BASE)
                        .setMessage("Failed to determine Maven activation for profile " + profile.getId()
                                + " due invalid Maven version range: '" + maven + "'")
                        .setLocation(activation.getLocation("maven"))
                        .setException(e));
                return false;
            }
        } else {
            return version.startsWith(maven);
        }
    }

    @Override
    public boolean presentInConfig(
            org.apache.maven.model.Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getDelegate().getActivation();
        return activation != null && activation.getMaven() != null;
    }

    private static boolean isRange(String value) {
        return value.startsWith("[") || value.startsWith("(");
    }
}
