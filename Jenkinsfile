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
            archiveArtifacts artifacts: 'src/main/target/*.war', fingerprint: true, onlyIfSuccessful: true
        }
    }
}