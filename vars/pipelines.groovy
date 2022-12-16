def pipelines() {
pipeline {
    agent any
    environment {
        DOCKER_CREDS = credentials('docker-credentials')
        }
        stages {
            stage('Build') {
                agent {
                    docker { image 'gradle:7.5.1-jdk11' }
                }
                    steps {
                        sh 'gradle build'
                        archiveArtifacts artifacts: 'build/libs/labgradle-*-SNAPSHOT.jar', fingerprint: true
                    }
            }
            stage('Test') {
                agent {
                    docker { image 'gradle:7.5.1-jdk11' }
                }
                    steps {
                        sh 'gradle test'
                        junit 'build/test-results/test/TEST-*.xml'
                    }
            }
            stage('SonarQube') {
                steps {
                    script{
                        def scannerHome = tool 'scanner-default'
                        withSonarQubeEnv('sonar-server') {
                            sh "${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=labgradle01 \
                            -Dsonar.projectName=labgradle01 \
                            -Dsonar.sources=src/main/kotlin \
                            -Dsonar.java.binaries=build/classes \
                            -Dsonar.tests=src/test/kotlin"
                        }
                    }
                }
            }
            stage('Build Image') {
                steps {
                    copyArtifacts filter: 'build/libs/labgradle-*-SNAPSHOT.jar',
                                    fingerprintArtifacts: true,
                                    projectName: '${JOB_NAME}',
                                    flatten: true,
                                    selector: specific('${BUILD_NUMBER}'),
                                    target: 'build/libs/'
                    sh 'docker --version'
                    sh 'docker-compose --version'
                    sh 'docker-compose build'
                }
            }
            stage('Publish Image') {
                steps {
                    script {
                        sh 'docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}'
                        sh 'docker tag msmicroservice ${DOCKER_CREDS_USR}/msmicroservice:$BUILD_NUMBER'
                        sh 'docker push ${DOCKER_CREDS_USR}/msmicroservice:$BUILD_NUMBER'
                        sh 'docker logout'
                    }
                }
            }
            stage('Run Container') {
                steps {
                    script {
                        sh 'docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}'
                        sh 'docker rm galaxyLab -f'
                        sh 'docker run -d -p 8080:8080 --name galaxyLab ${DOCKER_CREDS_USR}/msmicroservice:$BUILD_NUMBER'
                        //sh 'docker run -d -p 8080:8080 ${DOCKER_CREDS_USR}/msmicroservice:$BUILD_NUMBER'
                        sh 'docker logout'
                    }
                }
            }
            stage('Test Run Container') {
                    steps {
                        script {
                            sh 'docker ps'
                            sh 'curl http://localhost:8080/customers'
                        }
                    }
            }
        }
    }
}

//plugins
//Copy ArtifactVersion
