pipeline {
    agent { docker { image 'gradle:latest' } }

    stages {
        stage('build') {
            steps {
                sh './gradlew clean assemble'
            }
        }
        stage('test') {
            steps {
                sh './gradlew test'
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: '**/tds*.war', fingerprint: true, onlyIfSuccessful: true
        }
    }
}