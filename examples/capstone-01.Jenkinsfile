// Example only. Requires Jenkins global library configuration and a reviewed revision.
@Library('twn-devops-lib@<approved-tag>') _
pipeline {
    agent any
    stages {
        stage('Calculate Version') {
            steps { script { echo incrementVersion(version: '1.2.3', type: 'patch') } }
        }
    }
}
