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

import java.util.Map;

import org.apache.maven.api.model.Activation;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.services.model.ProfileActivationContext;
import org.apache.maven.impl.DefaultVersionParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link MavenVersionProfileActivator}.
 */
class MavenVersionProfileActivatorTest extends AbstractProfileActivatorTest<MavenVersionProfileActivator> {

    @Override
    @BeforeEach
    void setUp() {
        activator = new MavenVersionProfileActivator(new DefaultVersionParser());
    }

    private Profile newProfile(String mavenVersion) {
        Activation activation = Activation.newBuilder().maven(mavenVersion).build();
        return Profile.newBuilder().activation(activation).build();
    }

    private Map<String, String> newProperties(String mavenVersion) {
        return Map.of("maven.version", mavenVersion);
    }

    @Test
    void testNullSafe() {
        Profile profile = Profile.newInstance();

        assertActivation(false, profile, newContext(null, null));

        profile = profile.withActivation(Activation.newInstance());

        assertActivation(false, profile, newContext(null, null));
    }

    @Test
    void testPrefix() {
        Profile profile = newProfile("4.0");

        assertActivation(true, profile, newContext(null, newProperties("4.0.0-rc-5")));
        assertActivation(true, profile, newContext(null, newProperties("4.0.1")));
        assertActivation(false, profile, newContext(null, newProperties("3.9.9")));
    }

    @Test
    void testPrefixNegated() {
        Profile profile = newProfile("!4.0");

        assertActivation(false, profile, newContext(null, newProperties("4.0.0-rc-5")));
        assertActivation(true, profile, newContext(null, newProperties("3.9.9")));
    }

    @Test
    void testVersionRange() {
        Profile profile = newProfile("[4,)");

        assertActivation(true, profile, newContext(null, newProperties("4.0.0-rc-5")));
        assertActivation(true, profile, newContext(null, newProperties("4.0.1")));
        assertActivation(false, profile, newContext(null, newProperties("3.9.9")));
    }

    @Test
    void testInvalidVersionRange() {
        Profile profile = newProfile("[4,");

        assertActivationWithProblems(profile, newContext(null, newProperties("4.0.0")), "invalid Maven version range");
    }

    private void assertActivationWithProblems(
            Profile profile, ProfileActivationContext context, String warningContains) {
        SimpleProblemCollector problems = new SimpleProblemCollector();

        assertFalse(activator.isActive(profile, context, problems));

        assertEquals(0, problems.getErrors().size());
        assertEquals(1, problems.getWarnings().size());
        assertTrue(problems.getWarnings().get(0).contains(warningContains));
    }
}
