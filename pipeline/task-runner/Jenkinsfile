pipeline {

  agent {
    kubernetes {
      defaultContainer 'jnlp'
      yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    job: ${env.JOB_NAME}
    job_id: ${env.BUILD_NUMBER}
spec:
  nodeSelector:
    role: worker
  containers:
  - name: deployer
    image: quay.io/uktrade/deployer
    imagePullPolicy: Always
    command:
    - cat
    tty: true
"""
    }
  }

  options {
    timestamps()
    ansiColor('xterm')
    buildDiscarder(logRotator(daysToKeepStr: '180'))
  }

  parameters {
    string(defaultValue: '', description:'Please enter app: ', name: 'cf_app')
    string(defaultValue: 'eu-west-2', description:'Please enter region: ', name: 'cf_region')
    string(defaultValue: '', description:'Please enter task name: ', name: 'task_name')
    string(defaultValue: '', description:'Please enter task command: ', name: 'task_cmd')
    string(defaultValue: '1024M', description:'Please enter task mem: ', name: 'task_mem')
    string(defaultValue: '1G', description:'Please enter task disk: ', name: 'task_disk')
  }

  stages {

    stage('Init') {
      steps {
        script {
          timestamps {
            validateDeclarativePipeline("${env.WORKSPACE}/Jenkinsfile")
          }
        }
      }
    }

    stage('Task') {
      steps {
        container('deployer') {
          script {
            timestamps {
              withCredentials([string(credentialsId: env.GDS_PAAS_CONFIG, variable: 'paas_config_raw')]) {
                paas_config = readJSON text: paas_config_raw
              }
              if (!params.cf_region) {
                cf_region = paas_config.default
              }
              paas_region = paas_config.regions."${cf_region}"
              echo "\u001B[32mINFO: Setting PaaS region to ${paas_region.name}.\u001B[m"

              withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                sh """
                  cf api ${paas_region.api}
                  cf auth ${gds_user} ${gds_pass}
                """
              }

              gds_app = params.cf_app.split("/")
              sh "cf target -o ${gds_app[0]} -s ${gds_app[1]}"
              sh """
                cf run-task ${gds_app[2]} '${params.task_cmd}' --name ${params.task_name} -k ${params.task_disk} -m ${params.task_mem}
              """
            }
          }
        }
      }
    }

  }
}
