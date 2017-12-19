pipeline {

  agent {
    node {
      label 'docker.ci.uktrade.io'
    }
  }

  parameters {
    string(defaultValue: '', description:'Please choose your team: ', name: 'Team')
    string(defaultValue: '', description:'Please choose your project: ', name: 'Project')
    string(defaultValue: '', description:'Please choose your environment: ', name: 'Environment')
    string(defaultValue: '', description:'Please choose your git branch/tag/commit: ', name: 'Version')
  }

  stages {
    stage('prep') {
      steps {
        script {
          validateDeclarativePipeline("${env.WORKSPACE}/Jenkinsfile")
          sh """
            git rev-parse HEAD > ${env.WORKSPACE}/.git_branch
            git remote get-url origin > ${env.WORKSPACE}/.git_url
            git branch --remotes --contains `git rev-parse HEAD` | grep -v HEAD | tail -n 1 > ${env.WORKSPACE}/.git_branch_name
          """
          env.APP_GIT_URL = readFile "${env.WORKSPACE}/.git_url"
          env.APP_GIT_BRANCH = readFile "${env.WORKSPACE}/.git_branch"
          branch = readFile "${env.WORKSPACE}/.git_branch_name"
          env.APP_BRANCH_NAME = branch.replaceAll(/\s+origin\//, "").trim()
          deployer = docker.image("ukti/deployer:${env.APP_BRANCH_NAME}")
          deployer.pull()
        }
      }
    }

    stage('init') {
      steps {
        script {
          deployer.inside {
            checkout([$class: 'GitSCM', url: env.GIT_URL, branches: [[name: env.GIT_BRANCH]], recursiveSubmodules: true, credentialsId: env.SCM_CREDENTIAL])
            sh 'bundle check || bundle install'
            sh "${env.WORKSPACE}/bootstrap.rb"
            options_json = readJSON file: "${env.WORKSPACE}/.option.json"
          }
        }
      }
    }

    stage('input') {
      steps {
        script {
          input = load "${env.WORKSPACE}/input.groovy"
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

    stage('setup') {
      steps {
        script {
          deployer.inside {
            checkout([$class: 'GitSCM', url: env.GIT_URL, branches: [[name: env.GIT_BRANCH]], recursiveSubmodules: true, credentialsId: env.SCM_CREDENTIAL])
            sh 'bundle check || bundle install'
            withCredentials([string(credentialsId: env.VAULT_TOKEN_ID, variable: 'TOKEN')]) {
              env.VAULT_SERECT_ID = TOKEN
              sh "${env.WORKSPACE}/bootstrap.rb ${env.Team} ${env.Project} ${env.Environment}"
            }
            envars = readProperties file: "${env.WORKSPACE}/.env"
          }
        }
      }
    }

    stage('load') {
      steps {
        script {
          envars.each { key, value ->
            env."${key}" = value
          }
        }
      }
    }

    stage('deploy') {
      steps {
        script {
          deployer.inside {
            if (env.Version =~ /[a-fA-F0-9]{40}/) {
              checkout([$class: 'GitSCM', branches: [[name: env.Version]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.SCM]]])
            } else {
              git url: env.SCM, branch: env.Version, credentialsId: env.SCM_CREDENTIAL
            }

            node_ver_exist = fileExists "${env.WORKSPACE}/.nvmrc"
            py_ver_exist = fileExists "${env.WORKSPACE}/.python-version"
            rb_ver_exist = fileExists "${env.WORKSPACE}/.ruby-version"
            if (node_ver_exist) {
              node_ver = readFile "${env.WORKSPACE}/.nvmrc"
              echo "INFO: Detected Nodejs version ${node_ver}"
              ansiColor('xterm') {
                sh "bash -l -c 'nvm install ${node_ver}'"
              }
            }
            if (py_ver_exist) {
              py_ver = readFile "${env.WORKSPACE}/.python-version"
              echo "INFO: Detected Python version ${py_ver}"
              ansiColor('xterm') {
                sh "bash -l -c 'pyenv install ${py_ver}'"
              }
            }
            if (rb_ver_exist) {
              rb_ver = readFile "${env.WORKSPACE}/.ruby-version"
              echo "INFO: Detected Ruby version ${rb_ver}"
              ansiColor('xterm') {
                sh "bash -l -c 'rvm install ${rb_ver}'"
              }
            }

            ansiColor('xterm') {
              sh "bash -l -c \"${env.PAAS_RUN}\""
            }

            switch(env.PAAS_TYPE) {
              case "gds":
                withCredentials([usernamePassword(credentialsId: env.GDS_PAAS_CREDENTIAL, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  gds_app = env.PAAS_APP.split("/")
                  ansiColor('xterm') {
                    sh """
                      cf login -a ${env.GDS_PAAS} -u ${gds_user} -p ${gds_pass} -o ${gds_app[0]} -s ${gds_app[1]}
                      cf target -o ${gds_app[0]} -s ${gds_app[1]}
                      cf v3-create-app ${gds_app[2]}
                    """

                    cf_manifest_exist = fileExists "${env.WORKSPACE}/manifest.yml"
                    if (cf_manifest_exist) {
                      echo "INFO: Detected CF V2 manifest.yml"
                      cf_manifest = readYaml file: "${env.WORKSPACE}/manifest.yml"
                      if (cf_manifest.applications.size() != 1) {
                        echo "\u001B[31mWARNING: CF V2 manifest.yml contains more than 1 application defined! Only 'buildpack' attribute is accepted.\u001B[m"
                      }
                      if (cf_manifest.applications[0].size() != 1) {
                        echo "\u001B[31mWARNING: CF V2 manifest.yml contains more than 1 attribute for application defined! Only 'buildpack' attribute is accepted.\u001B[m"
                      }
                      if (cf_manifest.applications[0].buildpack) {
                        echo "\u001B[32mINFO: Setting application ${gds_app[2]} buildpack to ${cf_manifest.applications[0].buildpack}\u001B[m"
                        env.PAAS_BUILDPACK = cf_manifest.applications[0].buildpack
                      }
                    }

                    cfignore_exist = fileExists "${env.WORKSPACE}/.cfignore"
                    if (!cfignore_exist) {
                      sh "ln -snf ${env.WORKSPACE}/.gitignore ${env.WORKSPACE}/.cfignore"
                    }
                  }

                  envars.each { key, value ->
                    ansiColor('xterm') {
                      sh "cf v3-set-env ${gds_app[2]} ${input.bash_escape(key)} ${input.bash_escape(value)}"
                    }
                  }

                  ansiColor('xterm') {
                    if (env.PAAS_BUILDPACK) {
                      sh "cf v3-push ${gds_app[2]} -b ${env.PAAS_BUILDPACK}"
                    } else {
                      sh "cf v3-push ${gds_app[2]}"
                    }
                  }
                }
                break

              case "s3":
                if (env.S3_WEBSITE_SRC == null) {
                  s3_path = env.WORKSPACE
                } else {
                  s3_path = "${env.WORKSPACE}/${env.S3_WEBSITE_SRC}"
                }
                ansiColor('xterm') {
                  sh """
                    export AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}
                    export AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY_ID}
                    export AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}
                    aws s3 sync --sse --acl public-read --delete --exclude '.*' ${s3_path} s3://${env.PAAS_APP}
                    if [ -f ${env.WORKSPACE}/${env.S3_WEBSITE_REDIRECT} ]; then
                      aws s3api put-bucket-website --bucket ${env.PAAS_APP} --website-configuration file://${env.WORKSPACE}/${env.S3_WEBSITE_REDIRECT}
                    fi
                  """
                }
                break

              case "openshift":
                oc_app = env.PAAS_APP.split("/")
                ansiColor('xterm') {
                  sh """
                    oc login https://dashboard.${oc_app[0]} --token=${env.OC_TOKEN}
                    oc project ${oc_app[1]}
                    export OC_BUILD_ID=$(expr `oc get bc/${oc_app[2]} -o json | jq -rc '.status.lastVersion'` + 1)
                  """

                  sh """
                    oc process -f oc-pipeline.yml \
                      -v APP_ID=${oc_app[2]} \
                      -v NAMESPACE=${oc_app[1]} \
                      -v SCM=${env.SCM} \
                      -v SCM_COMMIT=${env.Version} \
                      -v PORT=${env.PORT} \
                      -v DOMAIN=apps.${env.oc_app[0]} \
                      | oc apply -f -
                  """

                  envars.each { key, value ->
                    sh "oc set env dc/${oc_app[2]} ${input.bash_escape(key)}=${input.bash_escape(value)}"
                  }

                  sh """
                    while [ $(oc get bc/${oc_app[2]} -o json | jq -rc '.status.lastVersion') -ne ${env.OC_BUILD_ID} ]; do
                      sleep 10
                    done
                    oc logs -f --version=${env.OC_BUILD_ID} bc/${oc_app[2]}
                    oc logs -f dc/${oc_app[2]}
                  """
                }
                break

              case "heroku":
                break
            }
          }
        }
      }
    }

  }

  post {
    failure {
      emailext subject: "${currentBuild.result}: ${env.Project} ${env.Environmen}", body: "${PROJECT_DEFAULT_CONTENT}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']], attachLog: true
    }

    always {
      script {
        deleteDir()
      }
    }
  }

}
