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
              checkout([$class: 'GitSCM', url: env.SCM, branches: [[name: env.Version]], recursiveSubmodules: true, credentialsId: env.SCM_CREDENTIAL])
            } else {
              git url: env.SCM, branch: env.Version, credentialsId: env.SCM_CREDENTIAL
            }

            node_ver_exist = fileExists "${env.WORKSPACE}/.nvmrc"
            py_ver_exist = fileExists "${env.WORKSPACE}/.python-version"
            if (node_ver_exist) {
              node_ver = readFile "${env.WORKSPACE}/.nvmrc"
              echo "Detected Nodejs version ${node_ver}"
              ansiColor('xterm') {
                sh "bash -l -c 'nvm install ${node_ver}'"
              }
            }
            if (py_ver_exist) {
              py_ver = readFile "${env.WORKSPACE}/.python-version"
              echo "Detected Python version ${py_ver}"
              ansiColor('xterm') {
                sh "bash -l -c 'pyenv install ${py_ver}'"
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
                      cf push ${gds_app[2]} --no-start
                    """
                    cfignore_exist = fileExists "${env.WORKSPACE}/.cfignore"
                    if (!cfignore_exist) {
                      sh "ln -snf ${env.WORKSPACE}/.gitignore ${env.WORKSPACE}/.cfignore"
                    }
                  }
                  envars.each { key, value ->
                    ansiColor('xterm') {
                      sh "cf set-env ${gds_app[2]} ${input.bash_escape(key)} ${input.bash_escape(value)}"
                    }
                  }
                  ansiColor('xterm') {
                    sh "cf push ${gds_app[2]}"
                  }
                }
                break

              case "s3":
                if (env.S3_WEBSITE_SRC == null) {
                  s3_path = env.WORKSPACE
                } else {
                  s3_path = "${env.WORKSPACE}/${env.S3_WEBSITE_SRC}"
                }
                sh """
                  export AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}
                  export AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY_ID}
                  export AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}
                  aws s3 sync --sse --acl public-read --delete --exclude '.*' ${s3_path} s3://${env.PAAS_APP}
                  if [ -f ${env.WORKSPACE}/${env.S3_WEBSITE_REDIRECT} ]; then
                    aws s3api put-bucket-website --bucket ${env.PAAS_APP} --website-configuration file://${env.WORKSPACE}/${env.S3_WEBSITE_REDIRECT}
                  fi
                """
                break

              case "openshift":
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
