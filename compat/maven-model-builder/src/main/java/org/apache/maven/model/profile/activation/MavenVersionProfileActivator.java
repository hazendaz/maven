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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.model.Activation;
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

    private static final int MAX_VERSION_TOKENS = 3;

    // Synthetic high upper bound used when the configured range has no explicit upper limit.
    private static final String MAX_VERSION_PLACEHOLDER = "99999999";

    private static final Pattern FILTER_1 = Pattern.compile("[^\\d._-]");
    private static final Pattern FILTER_2 = Pattern.compile("[._-]");
    private static final Pattern FILTER_3 = Pattern.compile("\\.");

    @Override
    public boolean isActive(
            org.apache.maven.model.Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getActivation();

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

        try {
            return isMavenVersionCompatible(maven, version);
        } catch (NumberFormatException e) {
            problems.add(new ModelProblemCollectorRequest(Severity.WARNING, Version.BASE)
                    .setMessage("Failed to determine Maven activation for profile " + profile.getId()
                            + " due to invalid Maven version/range: '" + version + "' / '" + maven + "'")
                    .setLocation(activation.getLocation("maven"))
                    .setException(e));
            return false;
        }
    }

    @Override
    public boolean presentInConfig(
            org.apache.maven.model.Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        Activation activation = profile.getActivation();
        return activation != null && activation.getMaven() != null;
    }

    private static boolean isMavenVersionCompatible(String requiredMavenRange, String currentMavenVersion) {
        if (requiredMavenRange.startsWith("!")) {
            return !currentMavenVersion.startsWith(requiredMavenRange.substring(1));
        } else if (isRange(requiredMavenRange)) {
            return isInRange(currentMavenVersion, getRange(requiredMavenRange));
        } else {
            return currentMavenVersion.startsWith(requiredMavenRange);
        }
    }

    private static boolean isInRange(String value, List<RangeValue> range) {
        int leftRelation = getRelationOrder(value, range.get(0), true);

        if (leftRelation == 0) {
            return true;
        }

        if (leftRelation < 0) {
            return false;
        }

        return getRelationOrder(value, range.get(1), false) <= 0;
    }

    private static int getRelationOrder(String value, RangeValue rangeValue, boolean isLeft) {
        if (rangeValue.value.isEmpty()) {
            return isLeft ? 1 : -1;
        }

        value = FILTER_1.matcher(value).replaceAll("");

        List<String> valueTokens = new ArrayList<>(Arrays.asList(FILTER_2.split(value)));
        List<String> rangeValueTokens = new ArrayList<>(Arrays.asList(FILTER_3.split(rangeValue.value)));

        addZeroTokens(valueTokens, MAX_VERSION_TOKENS);
        addZeroTokens(rangeValueTokens, MAX_VERSION_TOKENS);

        for (int i = 0; i < MAX_VERSION_TOKENS; i++) {
            int x = Integer.parseInt(valueTokens.get(i));
            int y = Integer.parseInt(rangeValueTokens.get(i));
            if (x < y) {
                return -1;
            } else if (x > y) {
                return 1;
            }
        }
        if (!rangeValue.closed) {
            return isLeft ? -1 : 1;
        }
        return 0;
    }

    private static void addZeroTokens(List<String> tokens, int max) {
        while (tokens.size() < max) {
            tokens.add("0");
        }
    }

    private static boolean isRange(String value) {
        return value.startsWith("[") || value.startsWith("(");
    }

    private static List<RangeValue> getRange(String range) {
        List<RangeValue> ranges = new ArrayList<>();
        String trimmedRange = range.trim();

        if (!trimmedRange.contains(",")
                && (trimmedRange.startsWith("[") || trimmedRange.startsWith("("))
                && (trimmedRange.endsWith("]") || trimmedRange.endsWith(")"))) {
            String value = trimmedRange.substring(1, trimmedRange.length() - 1);
            ranges.add(new RangeValue(value, trimmedRange.startsWith("[")));
            ranges.add(new RangeValue(value, trimmedRange.endsWith("]")));
            return ranges;
        }

        for (String token : trimmedRange.split(",")) {
            if (token.startsWith("[")) {
                ranges.add(new RangeValue(token.substring(1), true));
            } else if (token.startsWith("(")) {
                ranges.add(new RangeValue(token.substring(1), false));
            } else if (token.endsWith("]")) {
                ranges.add(new RangeValue(token.substring(0, token.length() - 1), true));
            } else if (token.endsWith(")")) {
                ranges.add(new RangeValue(token.substring(0, token.length() - 1), false));
            } else if (token.isEmpty()) {
                ranges.add(new RangeValue("", false));
            } else {
                throw new NumberFormatException("Invalid Maven version range: " + range);
            }
        }
        if (ranges.size() < 2) {
            ranges.add(new RangeValue(MAX_VERSION_PLACEHOLDER, false));
        }
        return ranges;
    }

    private static class RangeValue {
        private final String value;

        private final boolean closed;

        RangeValue(String value, boolean closed) {
            this.value = value.trim();
            this.closed = closed;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
