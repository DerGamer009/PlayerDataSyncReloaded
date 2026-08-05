pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x gradlew'
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