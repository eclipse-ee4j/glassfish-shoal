/*
 * Copyright (c) 2024, 2026 Contributors to the Eclipse Foundation.
 * Copyright (c) 2019-2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

pipeline {
  agent any
  options {
    buildDiscarder(logRotator(numToKeepStr: '2', artifactNumToKeepStr: '2'))
    // abort pipeline if previous stage is unstable
    skipStagesAfterUnstable()
    // show timestamps in logs
    timestamps()
    // global timeout, abort after 6 hours
    timeout(time: 20, unit: 'MINUTES')
  }
  stages {
    stage('build') {
      agent any
      tools {
        jdk 'temurin-jdk21-latest'
        maven 'apache-maven-latest'
      }
      steps {
        sh '''
          mvn -V -B install -DskipTests=true
          mvn -B verify
        '''
        junit testResults: '**/target/*-reports/*.xml', allowEmptyResults: true, stdioRetention: 'FAILED', skipPublishingChecks: true
      }
    }
  }
}
