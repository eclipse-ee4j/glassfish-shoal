/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates. All rights reserved.
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

env.label = "ci-pod-${UUID.randomUUID().toString()}"
pipeline {
  options {
    // keep at most 50 builds
    buildDiscarder(logRotator(numToKeepStr: '50'))
    // abort pipeline if previous stage is unstable
    skipStagesAfterUnstable()
    // show timestamps in logs
    timestamps()
    // global timeout, abort after 6 hours
    timeout(time: 30, unit: 'MINUTES')
  }
  agent {
    kubernetes {
      label "${env.label}"
      defaultContainer 'jnlp'
      yaml """
apiVersion: v1
kind: Pod
metadata:
spec:
  volumes:
    - name: "jenkins-home"
      emptyDir: {}
    - name: maven-repo-shared-storage
      persistentVolumeClaim:
       claimName: glassfish-maven-repo-storage
    - name: settings-xml
      secret:
        secretName: m2-secret-dir
        items:
        - key: settings.xml
          path: settings.xml
    - name: settings-security-xml
      secret:
        secretName: m2-secret-dir
        items:
        - key: settings-security.xml
          path: settings-security.xml
    - name: maven-repo-local-storage
      emptyDir: {}
  containers:
  - name: jnlp
    image: jenkins/jnlp-slave:alpine
    imagePullPolicy: IfNotPresent
    volumeMounts:
    env:
      - name: JAVA_TOOL_OPTIONS
        value: -Xmx1G
    resources:
      limits:
        memory: "1Gi"
        cpu: "1"
  - name: build-container
    image: ee4jglassfish/ci:tini-jdk-8.181
    args:
    - cat
    tty: true
    imagePullPolicy: Always
    volumeMounts:
      - name: "jenkins-home"
        mountPath: "/home/jenkins"
        readOnly: false
      - name: maven-repo-shared-storage 
        mountPath: "/home/jenkins/.m2/repository"
      - name: settings-xml
        mountPath: "/home/jenkins/.m2/settings.xml"
        subPath: settings.xml
        readOnly: true
      - name: settings-security-xml
        mountPath: "/home/jenkins/.m2/settings-security.xml"
        subPath: settings-security.xml
        readOnly: true
      - name: maven-repo-local-storage
        mountPath: "/home/jenkins/.m2/repository/org/glassfish/shoal"
    resources:
      limits:
        memory: "7Gi"
        cpu: "3"
"""
    }
  }
  stages {
    stage('build') {
      steps {
        container('build-container') {
          timeout(time: 20, unit: 'MINUTES') {
            sh '''
              mvn --batch-mode install -DskipTests=true -Dmaven.javadoc.skip=true -Dgpg.skip=true --show-version -Pstaging
              mvn --batch-mode verify -Dgpg.skip=true -Pstaging
            '''
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
          }
        }
      }
    }
  }
}
