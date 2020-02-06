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
    string(defaultValue: '', description:'Please choose your team: ', name: 'Team')
    string(defaultValue: '', description:'Please choose your project: ', name: 'Project')
    string(defaultValue: '', description:'Please choose your environment: ', name: 'Environment')
    string(defaultValue: '', description:'Please choose your git branch/tag/commit: ', name: 'Version')
  }

  stages {

    stage('Init') {
      steps {
        script {
          timestamps {
            validateDeclarativePipeline("${env.WORKSPACE}/Jenkinsfile")
            log_info = "\033[32mINFO: "
            log_warn = "\033[31mWARNING: "
            lock = "false"
          }
        }
        container('deployer') {
          script {
            timestamps {
              checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '.ci'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, trackingSubmodules: false, shallow: true], [$class: 'WipeWorkspace'], [$class: 'CloneOption', shallow: true, noTags: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_SCM]]])
              checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, trackingSubmodules: false, shallow: true], [$class: 'RelativeTargetDirectory', relativeTargetDir: '.ci/config'], [$class: 'CloneOption', shallow: true, noTags: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_CONF_SCM]]])
              sh "${env.WORKSPACE}/.ci/bootstrap.rb validate"
              options_json = readJSON file: "${env.WORKSPACE}/.ci/.option.json"
            }
          }
        }
      }
    }

    stage('Input') {
      steps {
        script {
          timestamps {
            input = load "${env.WORKSPACE}/.ci/input.groovy"
            if (!env.Team) {
              team = input(
                id: 'team', message: 'Please choose your team: ', parameters: [
                [$class: 'ChoiceParameterDefinition', name: 'Team', description: 'Team', choices: input.get_team(options_json)]
              ])
              env.Team = team
            } else if (!input.validate_team(options_json, env.Team)) {
              error 'Invalid Team!'
            }

            if (!env.Project) {
              project = input(
                id: 'project', message: 'Please choose your project: ', parameters: [
                [$class: 'ChoiceParameterDefinition', name: 'Project', description: 'Project', choices: input.get_project(options_json,team)]
              ])
              env.Project = project
            } else if (!input.validate_project(options_json, env.Team, env.Project)) {
              error 'Invalid Project!'
            }

            if (!env.Environment) {
              environment = input(
                id: 'environment', message: 'Please choose your environment: ', parameters: [
                [$class: 'ChoiceParameterDefinition', name: 'Environment', description: 'Environment', choices: input.get_env(options_json, team, project)]
              ])
              env.Environment = environment
            } else if (!input.validate_env(options_json, env.Team, env.Project, env.Environment)) {
              error 'Invalid Environment!'
            }

            if (!env.Version) {
              git_commit = input(
                id: 'git_commit', message: 'Please enter your git branch/tag/commit: ', parameters: [
                [$class: 'StringParameterDefinition', name: 'Git Commit', description: 'GitCommit']
              ])
              env.Version = git_commit
            }
          }
        }
      }
    }

    stage('Setup') {
      steps {
        container('deployer') {
          script {
            timestamps {
              withCredentials([string(credentialsId: env.VAULT_TOKEN_ID, variable: 'TOKEN')]) {
                env.VAULT_SERECT_ID = TOKEN
                sh "${env.WORKSPACE}/.ci/bootstrap.rb get ${env.Team}/${env.Project}/${env.Environment}"
              }
              envars = readJSON file: "${env.WORKSPACE}/.ci/env.json"
              config = readJSON file: "${env.WORKSPACE}/.ci/config.json"

              lock = sh(script: "${env.WORKSPACE}/.ci/bootstrap.rb get-lock ${env.Team}/${env.Project}/${env.Environment}", returnStdout: true).trim()
              if (lock == 'true') {
                error 'Parallel job of the same project is not allow.'
              } else {
                sh "${env.WORKSPACE}/.ci/bootstrap.rb lock ${env.Team}/${env.Project}/${env.Environment}"
              }
            }
          }
        }
      }
    }

    stage('Build') {
      steps {
        container('deployer') {
          script {
            timestamps {
              checkout([$class: 'GitSCM', branches: [[name: env.Version]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, trackingSubmodules: false], [$class: 'CloneOption', noTags: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: config.SCM]]])

              app_git_commit = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
              node_ver_exist = fileExists "${env.WORKSPACE}/.nvmrc"
              py_ver_exist = fileExists "${env.WORKSPACE}/.python-version"
              rb_ver_exist = fileExists "${env.WORKSPACE}/.ruby-version"
              java_ver_exist = fileExists "${env.WORKSPACE}/.java-version"
              go_ver_exist = fileExists "${env.WORKSPACE}/.go-version"
              if (node_ver_exist) {
                node_ver = readFile "${env.WORKSPACE}/.nvmrc"
                echo "${log_info}Detected Nodejs version ${node_ver.trim()}"
                sh "bash -l -c 'nvm install ${node_ver.trim()} && nvm use ${node_ver.trim()}'"
              }
              if (py_ver_exist) {
                py_ver = readFile "${env.WORKSPACE}/.python-version"
                echo "${log_info}Detected Python version ${py_ver.trim()}"
                sh "bash -l -c 'pyenv install ${py_ver.trim()} && pyenv global ${py_ver.trim()}'"
              }
              if (rb_ver_exist) {
                rb_ver = readFile "${env.WORKSPACE}/.ruby-version"
                echo "${log_info}Detected Ruby version ${rb_ver.trim()}"
                sh "bash -l -c 'rvm install ${rb_ver.trim()} && rvm use ${rb_ver.trim()}'"
              }
              if (java_ver_exist) {
                java_ver = readFile "${env.WORKSPACE}/.java-version"
                echo "${log_info}Detected Java version ${java_ver.trim()}"
                sh "bash -l -c 'jabba install ${java_ver.trim()} && jabba use ${java_ver.trim()}'"
              }
              if (go_ver_exist) {
                go_ver = readFile "${env.WORKSPACE}/.go-version"
                echo "${log_info}Detected Go version ${go_ver.trim()}"
                sh "bash -l -c 'goenv install ${go_ver.trim()} && goenv global ${go_ver.trim()}'"
              }

              if (config.PAAS_RUN) {
                sh "bash -l -c \"${config.PAAS_RUN}\""
              }
            }
          }
        }
      }
    }

    stage('Deploy PaaS V3') {
      when {
        expression {
          config.PAAS_TYPE == 'gds-v3'
        }
      }

      steps {
        container('deployer') {
          script {
            timestamps {
              withCredentials([string(credentialsId: env.GDS_PAAS_CONFIG, variable: 'paas_config_raw')]) {
                paas_config = readJSON text: paas_config_raw
              }
              if (!config.PAAS_REGION) {
                config.PAAS_REGION = paas_config.default
              }
              paas_region = paas_config.regions."${config.PAAS_REGION}"
              echo "${log_info}Setting PaaS region to ${paas_region.name}."

              withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                sh """
                  cf api ${paas_region.api}
                  cf auth ${gds_user} ${gds_pass}
                """
              }

              deploy = load "${env.WORKSPACE}/.ci/CF-v3.groovy"
              deploy.main()

            }
          }
        }
      }

      post {
        failure {
          script {
            container('deployer') {
              timestamps {
                withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  sh """
                    cf api ${paas_region.api}
                    cf auth ${gds_user} ${gds_pass}
                  """
                }
                echo "${log_warn}Rollback app"
                sh """
                  cf target -o ${gds_app[0]} -s ${gds_app[1]}
                  cf logs ${gds_app[2]} --recent || true
                  cf curl '/v3/deployments/${deploy_guid}/actions/cancel' -X POST | jq -C 'del(.links)'
                """
              }
            }
          }
        }
      }

    }

    stage('Deploy PaaS') {
      when {
        expression {
          config.PAAS_TYPE == 'gds'
        }
      }

      steps {
        container('deployer') {
          script {
            timestamps {
              withCredentials([string(credentialsId: env.GDS_PAAS_CONFIG, variable: 'paas_config_raw')]) {
                paas_config = readJSON text: paas_config_raw
              }
              if (!config.PAAS_REGION) {
                config.PAAS_REGION = paas_config.default
              }
              paas_region = paas_config.regions."${config.PAAS_REGION}"
              echo "${log_info}Setting PaaS region to ${paas_region.name}."

              withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                sh """
                  cf api ${paas_region.api}
                  cf auth ${gds_user} ${gds_pass}
                """
              }

              deploy = load "${env.WORKSPACE}/.ci/CF-v2.groovy"
              deploy.main()

            }
          }
        }
      }

      post {
        success {
          script {
            container('deployer') {
              timestamps {
                withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  sh """
                    cf api ${paas_region.api}
                    cf auth ${gds_user} ${gds_pass}
                  """
                }
                echo "${log_info}Cleanup old app"
                sh """
                  cf target -o ${gds_app[0]} -s ${gds_app[1]}
                  cf curl '/v3/apps/${app_guid}' -X PATCH -d '{"name": "${gds_app[2]}-delete"}' | jq -C 'del(.links, .relationships)'
                  cf curl '/v3/apps/${new_app_guid}' -X PATCH -d '{"name": "${gds_app[2]}"}' | jq -C 'del(.links, .relationships)'
                """
                try {
                  timeout(time: 60, unit: 'SECONDS') {
                    old_app_stop = 'false'
                    while (old_app_stop != "STOPPED") {
                      echo "${log_info}Gracefully stopping app ${gds_app[2]}-delete"
                      old_app_stop = sh(script: "cf curl '/v3/apps/${app_guid}/actions/stop' -X POST | jq -r '.state'", returnStdout: true).trim()
                      sleep 5
                    }
                    echo "${log_info}Gracefully stopped app ${gds_app[2]}-delete"
                    sh "cf curl '/v3/apps/${app_guid}' -X DELETE"
                  }
                } catch (err) {
                  echo "${log_warn}Force deleting app ${gds_app[2]}-delete"
                  sh "cf curl '/v3/apps/${app_guid}' -X DELETE"
                }
              }
            }
          }
        }

        failure {
          script {
            container('deployer') {
              timestamps {
                withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  sh """
                    cf api ${paas_region.api}
                    cf auth ${gds_user} ${gds_pass}
                  """
                }
                echo "${log_warn}Rollback app"
                sh """
                  cf target -o ${gds_app[0]} -s ${gds_app[1]}
                  cf logs ${new_app_name} --recent || true
                  cf curl '/v3/apps/${new_app_guid}' -X DELETE || true
                """
              }
            }
          }
        }
      }

    }

    stage('Deploy S3') {
      when {
        expression {
          config.PAAS_TYPE == 's3'
        }
      }

      steps {
        container('deployer') {
          script {
            timestamps {
              if (envars.S3_WEBSITE_SRC == null) {
                s3_path = env.WORKSPACE
              } else {
                s3_path = "${env.WORKSPACE}/${envars.S3_WEBSITE_SRC}"
              }
              sh """
                set +x
                export AWS_DEFAULT_REGION=${envars.AWS_DEFAULT_REGION}
                export AWS_ACCESS_KEY_ID=${envars.AWS_ACCESS_KEY_ID}
                export AWS_SECRET_ACCESS_KEY=${envars.AWS_SECRET_ACCESS_KEY}
                aws s3 sync --sse --acl public-read --delete --exclude '.*' ${s3_path} s3://${config.PAAS_APP}
                if [ -f ${env.WORKSPACE}/${envars.S3_WEBSITE_REDIRECT} ]; then
                  aws s3api put-bucket-website --bucket ${config.PAAS_APP} --website-configuration file://${env.WORKSPACE}/${envars.S3_WEBSITE_REDIRECT}
                fi
              """
            }
          }
        }
      }
    }

  }

  post {
    failure {
      script {
        timestamps {
          emailext body: '${DEFAULT_CONTENT}', recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider'], [$class: 'UpstreamComitterRecipientProvider']], subject: "${currentBuild.result}: ${env.Project} ${env.Environment}", to: '${DEFAULT_RECIPIENTS}'
        }
      }
    }

    always {
      script {
        container('deployer') {
          timestamps {
            if (lock == 'false') {
              sh "${env.WORKSPACE}/.ci/bootstrap.rb unlock ${env.Team}/${env.Project}/${env.Environment}"
            }
          }

          timestamps {
            message_colour_map = readJSON text: '{"SUCCESS": "good", "FAILURE": "danger", "UNSTABLE": "warning"}'
            message_colour = message_colour_map."${currentBuild.currentResult}".toString()
            message_body = """
              [{
                "fallback": "${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.Project} ${env.Environment} (<${env.BUILD_URL}|Open>)",
                "color": "${message_colour}",
                "author_name": "${env.JOB_NAME}",
                "author_link": "${env.JOB_URL}",
                "title": "${currentBuild.currentResult}: Build #${env.BUILD_NUMBER}",
                "title_link": "${env.BUILD_URL}",
                "fields": [{
                  "title": "Team",
                  "value": "${env.Team}",
                  "short": true
                }, {
                  "title": "Project",
                  "value": "${env.Project}",
                  "short": true
                }, {
                  "title": "Environment",
                  "value": "${env.Environment}",
                  "short": true
                }],
                "footer": "<${JENKINS_URL}|Jenkins>",
                "footer_icon": "https://raw.githubusercontent.com/jenkinsci/jenkins/master/war/src/main/webapp/images/jenkins.png",
                "ts": "${currentBuild.timeInMillis/1000}"
              }]
            """
            slackSend attachments: message_body.toString().trim()
            deleteDir()
          }
        }
      }
    }
  }

}
