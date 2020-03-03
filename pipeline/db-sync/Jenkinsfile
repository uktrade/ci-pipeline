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
    string(defaultValue: '', description:'Please enter app: [org/space/service]', name: 'cf_src')
    string(defaultValue: '', description:'Please enter app: [org/space/service]', name: 'cf_dest')
    string(defaultValue: 'eu-west-2', description:'Please enter region: ', name: 'cf_region')
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

              src = params.cf_src.split("/")
              dest = params.cf_dest.split("/")
              echo "\u001B[32mINFO: Purge destination database ${dest[2]}.\u001B[m"
              sh "cf target -o ${dest[0]} -s ${dest[1]}"
              sh """
                cf conduit -p \$(shuf -i1024-65535 -n1 -z) ${dest[2]} -- psql < purge.sql
              """
              echo "\u001B[32mINFO: Restoring database from ${src[2]} to ${dest[2]}.\u001B[m"
              sh "cf target -o ${src[0]} -s ${src[1]}"
              sh """
                cf conduit -p \$(shuf -i1024-65535 -n1 -z) ${src[2]} -- pg_dump -O -x | cf conduit -p \$(shuf -i1024-65535 -n1 -z) --org ${dest[0]} --space ${dest[1]} ${dest[2]} -- psql
              """
            }
          }
        }
      }
    }

  }
}
