/*
 * Copyright 2017 the original author or authors.
 *
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
 */

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ExternalModuleVariantsIntegrationTest extends AbstractDependencyResolutionTest {
    def "artifacts in a Maven repo have standard attributes defined"() {
        mavenRepo.module("test", "test-jar", "1.2").publish()
        mavenRepo.module("test", "test-aar", "1.2")
            .hasPackaging('aar')
            .hasType('aar')
            .publish()
        mavenRepo.module("test", "test-thing", "1.2")
            .hasPackaging('thing')
            .hasType('thing')
            .publish()
        mavenRepo.module("test", "test", "1.2")
            .artifact(type: 'aar')
            .artifact(type: 'thing')
            .artifact(type: 'aar', classifier: 'util')
            .artifact(type: 'jar', classifier: 'util')
            .publish()
        mavenRepo.module("test", "test-api", "1.2").publish()

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'test:test-jar:1.2'
                compile 'test:test-aar:1.2'
                compile 'test:test-thing:1.2'
                compile 'test:test:1.2'
                compile 'test:test:1.2@aar'
                compile 'test:test:1.2@thing'
                compile 'test:test:1.2:util'
                compile 'test:test:1.2:util@aar'
                compile('test:test-api:1.2') { targetConfiguration = 'compile' }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test-jar-1.2.jar {artifactType=jar}")
        output.contains("test-aar-1.2.aar {artifactType=aar}")
        output.contains("test-thing-1.2.thing {artifactType=thing}")
        output.contains("test-1.2.jar {artifactType=jar}")
        output.contains("test-1.2.aar {artifactType=aar}")
        output.contains("test-1.2.thing {artifactType=thing}")
        output.contains("test-1.2-util.jar {artifactType=jar}")
        output.contains("test-1.2-util.aar {artifactType=aar}")
        output.contains("test-api-1.2.jar {artifactType=jar}")
    }

    def "artifacts in an Ivy repo have standard attributes defined based on their type"() {
        ivyRepo.module("test", "test-jar", "1.2").publish()
        ivyRepo.module("test", "test-aar", "1.2")
            .artifact(ext: 'aar', type: 'aar')
            .publish()
        ivyRepo.module("test", "test-thing", "1.2")
            .artifact(ext: 'thing', type: 'thing')
            .publish()
        ivyRepo.module("test", "test", "1.2")
            .configuration('other')
            .artifact(ext: 'jar')
            .artifact(ext: 'aar', conf: 'other')
            .artifact(ext: 'thing', conf: 'other')
            .artifact(ext: 'aar', classifier: 'util', conf: 'other')
            .artifact(ext: 'jar', classifier: 'util', conf: 'other')
            .publish()
        ivyRepo.module("test", "test-api", "1.2")
            .configuration("custom")
            .configuration("another")
            .artifact(conf: 'custom', ext: 'jar', type: 'custom')
            .artifact(ext: '', type: '', conf: 'another')
            .publish()

        buildFile << """
            repositories {
                ivy { url '${ivyRepo.uri}' }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'test:test-jar:1.2'
                compile 'test:test-aar:1.2'
                compile 'test:test-thing:1.2'
                compile 'test:test:1.2'
                compile 'test:test:1.2@aar'
                compile 'test:test:1.2@thing'
                compile 'test:test:1.2:util'
                compile 'test:test:1.2:util@aar'
                compile('test:test-api:1.2') { targetConfiguration = 'custom' }
                compile('test:test-api:1.2') { targetConfiguration = 'another' }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test-jar-1.2.jar {artifactType=jar}")
        output.contains("test-aar-1.2.aar {artifactType=aar}")
        output.contains("test-thing-1.2.thing {artifactType=thing}")
        output.contains("test-1.2.jar {artifactType=jar}")
        output.contains("test-1.2.aar {artifactType=aar}")
        output.contains("test-1.2.thing {artifactType=thing}")
        output.contains("test-1.2-util.jar {artifactType=jar}")
        output.contains("test-1.2-util.aar {artifactType=aar}")
        output.contains("test-api-1.2.jar {artifactType=custom}")
        output.contains("test-api-1.2 {artifactType=}")
    }

    def "artifacts in a file dependency have standard attributes defined based on their extension"() {
        buildFile << """
            configurations {
                compile
            }
            dependencies {
                compile files('test.jar')
                compile files('test.aar')
                compile files('test.thing')
                compile files('test')
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test.jar {artifactType=jar}")
        output.contains("test.aar {artifactType=aar}")
        output.contains("test.thing {artifactType=thing}")
        output.contains("test {artifactType=}")
    }

    def "artifacts from a Gradle project have standard attributes defined based on their type when none defined for the outgoing variant"() {
        settingsFile << 'include "a", "b", "c"'

        buildFile << """
            project(':a') {
                configurations { create 'default' }
                artifacts { 
                    'default' file('a.custom') 
                }
            }
            project(':b') {
                configurations { create 'default' }
                artifacts { 
                    'default' file('b.jar') 
                }
            }
            project(':c') {
                configurations { create 'default' }
                artifacts {
                    'default'(file('c.jar')) { type = 'other' }
                }
            }

            configurations {
                compile
            }
            dependencies {
                compile project(':a')
                compile project(':b')
                compile project(':c')
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("a.custom {artifactType=custom}")
        output.contains("b.jar {artifactType=jar}")
        output.contains("c.jar {artifactType=other}")
    }

    def "can attach attributes to an artifact in a Maven repo"() {
        mavenRepo.module("test", "test-jar", "1.2").publish()
        mavenRepo.module("test", "test-aar", "1.2")
            .hasPackaging('aar')
            .hasType('aar')
            .publish()
        mavenRepo.module("test", "test-thing", "1.2")
            .hasPackaging('thing')
            .hasType('thing')
            .publish()

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'test:test-jar:1.2'
                compile 'test:test-aar:1.2'
                compile 'test:test-thing:1.2'
                artifactTypes {
                    jar {
                        attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                        attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                    }
                    aar {
                        attributes.attribute(Attribute.of('artifactType', String), 'aar')
                        attributes.attribute(Attribute.of('androidType', String), 'library-archive')
                    }
                    thing {
                        attributes.attribute(Attribute.of('artifactType', String), 'widget')
                        attributes.attribute(Attribute.of('usage', String), 'unknown')
                    }
                }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test-jar-1.2.jar {artifactType=jar, javaVersion=1.8, usage=java-runtime}")
        output.contains("test-aar-1.2.aar {androidType=library-archive, artifactType=aar}")
        output.contains("test-thing-1.2.thing {artifactType=widget, usage=unknown}")
    }

    def "can attach attributes to an artifact in an Ivy repo"() {
        ivyRepo.module("test", "test-jar", "1.2").publish()
        ivyRepo.module("test", "test-aar", "1.2")
            .artifact(type: 'aar', ext: 'aar')
            .publish()
        ivyRepo.module("test", "test-thing", "1.2")
            .artifact(type: 'thing', ext: 'thing')
            .publish()

        buildFile << """
            repositories {
                ivy { url '${ivyRepo.uri}' }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'test:test-jar:1.2'
                compile 'test:test-aar:1.2'
                compile 'test:test-thing:1.2'
                artifactTypes {
                    jar {
                        attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                        attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                    }
                    aar {
                        attributes.attribute(Attribute.of('artifactType', String), 'aar')
                        attributes.attribute(Attribute.of('androidType', String), 'library-archive')
                    }
                    thing {
                        attributes.attribute(Attribute.of('artifactType', String), 'widget')
                        attributes.attribute(Attribute.of('usage', String), 'unknown')
                    }
                }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test-jar-1.2.jar {artifactType=jar, javaVersion=1.8, usage=java-runtime}")
        output.contains("test-aar-1.2.aar {androidType=library-archive, artifactType=aar}")
        output.contains("test-thing-1.2.thing {artifactType=widget, usage=unknown}")
    }

    def "can attach attributes to an artifact provided by a file dependency"() {
        buildFile << """
            configurations {
                compile
            }
            dependencies {
                compile files('test.jar')
                compile files('test.aar')
                compile files('test.thing')
                artifactTypes {
                    jar {
                        attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                        attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                    }
                    aar {
                        attributes.attribute(Attribute.of('artifactType', String), 'aar')
                        attributes.attribute(Attribute.of('androidType', String), 'library-archive')
                    }
                    thing {
                        attributes.attribute(Attribute.of('artifactType', String), 'widget')
                        attributes.attribute(Attribute.of('usage', String), 'unknown')
                    }
                }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("test.jar {artifactType=jar, javaVersion=1.8, usage=java-runtime}")
        output.contains("test.aar {androidType=library-archive, artifactType=aar}")
        output.contains("test.thing {artifactType=widget, usage=unknown}")
    }

    def "can attach attributes to an artifact provided by a Gradle project"() {
        settingsFile << 'include "a", "b", "c"'

        buildFile << """
            project(':a') {
                configurations { create 'default' }
                artifacts { 
                    'default' file('a.jar') 
                }
            }
            project(':b') {
                configurations { create 'default' }
                artifacts { 
                    'default' file('b.aar') 
                }
            }
            project(':c') {
                configurations { create 'default' }
                artifacts {
                    'default'(file('c.thing')) { type = 'ignore-me' }
                }
            }

            configurations {
                compile
            }
            dependencies {
                compile project(':a')
                compile project(':b')
                compile project(':c')
                artifactTypes {
                    jar {
                        attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                        attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                    }
                    aar {
                        attributes.attribute(Attribute.of('artifactType', String), 'aar')
                        attributes.attribute(Attribute.of('androidType', String), 'library-archive')
                    }
                    thing {
                        attributes.attribute(Attribute.of('artifactType', String), 'widget')
                        attributes.attribute(Attribute.of('usage', String), 'unknown')
                    }
                }
            }
            task show {
                def artifacts = configurations.compile.incoming.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println it.file.name + ' ' + it.variant.attributes
                    }
                }
            }
"""

        when:
        run 'show'

        then:
        output.contains("a.jar {artifactType=jar, javaVersion=1.8, usage=java-runtime}")
        output.contains("b.aar {androidType=library-archive, artifactType=aar}")
        output.contains("c.thing {artifactType=widget, usage=unknown}")
    }

    def "each project can define different artifact types"() {
        mavenRepo.module("test", "test-jar", "1.2").publish()
        mavenRepo.module("test", "test-aar", "1.2")
            .hasPackaging('aar')
            .hasType('aar')
            .publish()
        mavenRepo.module("test", "test-thing", "1.2")
            .hasPackaging('thing')
            .hasType('thing')
            .publish()

        settingsFile << "include 'a', 'b'"

        buildFile << """
            allprojects {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                configurations {
                    compile
                    create('default') { extendsFrom configurations.compile }
                }
                task show {
                    def artifacts = configurations.compile.incoming.artifacts
                    inputs.files artifacts.artifactFiles
                    doLast {
                        artifacts.each {
                            println it.file.name + ' ' + it.variant.attributes
                        }
                    }
                }
            }
            project(':a') {
                dependencies {
                    compile 'test:test-jar:1.2'
                    compile 'test:test-aar:1.2'
                    compile 'test:test-thing:1.2'
                    artifacts {
                        compile file("a.jar")
                    }
                    artifactTypes {
                        jar {
                            attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                            attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                        }
                        aar {
                            attributes.attribute(Attribute.of('artifactType', String), 'aar')
                            attributes.attribute(Attribute.of('androidType', String), 'library-archive')
                        }
                        thing {
                            attributes.attribute(Attribute.of('artifactType', String), 'widget')
                            attributes.attribute(Attribute.of('usage', String), 'unknown')
                        }
                    }
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                    artifactTypes {
                        aar {
                            attributes.attribute(Attribute.of('artifactType', String), 'android-lib')
                        }
                        thing {
                            attributes.attribute(Attribute.of('artifactType', String), 'a-thing')
                            attributes.attribute(Attribute.of('usage', String), 'a-thing')
                        }
                    }
                }
            }
"""

        when:
        run ':a:show'

        then:
        output.contains("test-jar-1.2.jar {artifactType=jar, javaVersion=1.8, usage=java-runtime}")
        output.contains("test-aar-1.2.aar {androidType=library-archive, artifactType=aar}")
        output.contains("test-thing-1.2.thing {artifactType=widget, usage=unknown}")

        when:
        run ':b:show'

        then:
        output.contains("a.jar {artifactType=jar}")
        output.contains("test-jar-1.2.jar {artifactType=jar}")
        output.contains("test-aar-1.2.aar {artifactType=android-lib}")
        output.contains("test-thing-1.2.thing {artifactType=a-thing, usage=a-thing}")
    }
}
