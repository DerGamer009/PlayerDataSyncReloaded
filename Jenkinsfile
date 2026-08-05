pipeline {
    agent any

    environment {
        JAVA_HOME = '/opt/java/jdk-25'
        PATH = "/opt/java/jdk-25/bin:${env.PATH}"
    }

    stages {
        stage('Environment') {
            steps {
                sh 'java -version'
                sh 'uname -m'
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew --version'
                sh './gradlew clean build'
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: '**/build/libs/*.jar',
                fingerprint: true
        }
    }
}
