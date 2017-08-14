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
    string(defaultValue: '', description:'Please choose your git branch/tag/commit: ', name: 'Git_Commit')
  }

  stages {
    stage('prep') {
      steps {
        script {
          validateDeclarativePipeline("${env.WORKSPACE}/Jenkinsfile")
          sh "git branch --remotes --contains `git rev-parse HEAD` | grep -v HEAD | sed -e s/origin\\\\///g > ${env.WORKSPACE}/.git_branch"
          env.BRANCH_NAME = readFile "${env.WORKSPACE}/.git_branch"
          deployer = docker.image('ukti/deployer:latest')
          deployer.pull()
        }
      }
    }

    stage('init') {
      steps {
        script {
          deployer.inside {
            git url: 'git@github.com:uktrade/ci-pipeline.git', branch: env.BRANCH_NAME, credentialsId: env.SCM_CREDENTIAL
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
          options = load "${env.WORKSPACE}/input.groovy"
          if (!env.Team) {
            team = input(
              id: 'team', message: 'Please choose your team: ', parameters: [
              [$class: 'ChoiceParameterDefinition', name: 'Team', description: 'Team', choices: options.get_team(options_json)]
            ])
            env.Team = team
          } else if (!options.validate_team(options_json, env.Team)) {
            error 'Invalid Team!'
          }

          if (!env.Project) {
            project = input(
              id: 'project', message: 'Please choose your project: ', parameters: [
              [$class: 'ChoiceParameterDefinition', name: 'Project', description: 'Project', choices: options.get_project(options_json,team)]
            ])
            env.Project = project
          } else if (!options.validate_project(options_json, env.Team, env.Project)) {
            error 'Invalid Project!'
          }

          if (!env.Environment) {
            environment = input(
              id: 'environment', message: 'Please choose your environment: ', parameters: [
              [$class: 'ChoiceParameterDefinition', name: 'Environment', description: 'Environment', choices: options.get_env(options_json, team, project)]
            ])
            env.Environment = environment
          } else if (!options.validate_env(options_json, env.Team, env.Project, env.Environment)) {
            error 'Invalid Environment!'
          }

          if (!env.Git_Commit) {
            git_commit = input(
              id: 'git_commit', message: 'Please enter your git branch/tag/commit: ', parameters: [
              [$class: 'StringParameterDefinition', name: 'Git Commit', description: 'GitCommit', defaultValue: 'master']
            ])
            env.Git_Commit = git_commit
          }
        }
      }
    }

    stage('setup') {
      steps {
        script {
          deployer.inside {
            git url: 'git@github.com:uktrade/ci-pipeline.git', branch: env.BRANCH_NAME, credentialsId: env.SCM_CREDENTIAL
            sh 'bundle check || bundle install'
            withCredentials([string(credentialsId: env.VAULT_TOKEN_ID, variable: 'TOKEN')]) {
              env.VAULT_TOKEN = TOKEN
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
          envars.each {
            var = it.toString().split("=", 2)
            env."${var[0]}" = var[1]
          }
        }
      }
    }

    stage('deploy') {
      steps {
        script {
          env.HOME = '/tmp'
          deployer.inside {
            git url: env.SCM, branch: env.Git_Commit, credentialsId: env.SCM_CREDENTIAL
            ansiColor('xterm') {
              sh "bash -c \"${env.PAAS_RUN}\""
            }

            switch(env.PAAS_TYPE) {
              case "gds":
                withCredentials([usernamePassword(credentialsId: env.GDS_PAAS_CREDENTIAL, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  gds_app = env.PAAS_APP.split("/")
                  ansiColor('xterm') {
                    sh """
                      cf login -a ${env.GDS_PAAS} -u ${gds_user} -p ${gds_pass} -s ${gds_app[0]}
                      cf target -s ${gds_app[0]}
                      ln -snf ${env.WORKSPACE}/.gitignore ${env.WORKSPACE}/.cfignore
                    """
                  }
                  envars.each {
                    var = it.toString().split("=", 2)
                    ansiColor('xterm') {
                      sh "cf set-env ${gds_app[1]} ${var[0]} \"${var[1]}\""
                    }
                  }
                  ansiColor('xterm') {
                    sh "cf push ${gds_app[1]}"
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
    always {
      script {
        deleteDir()
      }
    }
  }

}
