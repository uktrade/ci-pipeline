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
          timestamps {
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
    }

    stage('init') {
      steps {
        script {
          timestamps {
            deployer.inside {
              checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.GIT_URL]]])
              sh 'bundle check || bundle install'
              sh "${env.WORKSPACE}/bootstrap.rb"
              options_json = readJSON file: "${env.WORKSPACE}/.option.json"
            }
          }
        }
      }
    }

    stage('input') {
      steps {
        script {
          timestamps {
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
    }

    stage('setup') {
      steps {
        script {
          timestamps {
            deployer.inside {
              checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.GIT_URL]]])
              sh 'bundle check || bundle install'
              withCredentials([string(credentialsId: env.VAULT_TOKEN_ID, variable: 'TOKEN')]) {
                env.VAULT_SERECT_ID = TOKEN
                sh "${env.WORKSPACE}/bootstrap.rb ${env.Team} ${env.Project} ${env.Environment}"
              }
              envars = readJSON file: "${env.WORKSPACE}/.env"
              stash name: "oc-pipeline", includes: "oc-pipeline.yml"
            }
          }
        }
      }
    }

    stage('load') {
      steps {
        script {
          timestamps {
            envars.each { key, value ->
              env."${key}" = value
            }
          }
        }
      }
    }

    stage('deploy') {
      steps {
        script {
          timestamps {
            ansiColor('xterm') {
              deployer.inside {
                checkout([$class: 'GitSCM', branches: [[name: env.Version]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.SCM]]])

                node_ver_exist = fileExists "${env.WORKSPACE}/.nvmrc"
                py_ver_exist = fileExists "${env.WORKSPACE}/.python-version"
                rb_ver_exist = fileExists "${env.WORKSPACE}/.ruby-version"
                if (node_ver_exist) {
                  node_ver = readFile "${env.WORKSPACE}/.nvmrc"
                  echo "INFO: Detected Nodejs version ${node_ver}"
                  sh "bash -l -c 'nvm install ${node_ver}'"
                }
                if (py_ver_exist) {
                  py_ver = readFile "${env.WORKSPACE}/.python-version"
                  echo "INFO: Detected Python version ${py_ver}"
                  sh "bash -l -c 'pyenv install ${py_ver}'"
                }
                if (rb_ver_exist) {
                  rb_ver = readFile "${env.WORKSPACE}/.ruby-version"
                  echo "INFO: Detected Ruby version ${rb_ver}"
                  sh "bash -l -c 'rvm install ${rb_ver}'"
                }

                if (env.PAAS_RUN) {
                  sh "bash -l -c \"${env.PAAS_RUN}\""
                }

                switch(env.PAAS_TYPE) {
                  case "gds":
                    gds_app = env.PAAS_APP.split("/")
                    withCredentials([usernamePassword(credentialsId: env.GDS_PAAS_CREDENTIAL, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                      sh """
                        cf login -a ${env.GDS_PAAS} -u ${gds_user} -p ${gds_pass} -o ${gds_app[0]} -s ${gds_app[1]}
                        cf target -o ${gds_app[0]} -s ${gds_app[1]}
                      """
                    }

                    cf_manifest_exist = fileExists "${env.WORKSPACE}/manifest.yml"
                    if (cf_manifest_exist) {
                      echo "INFO: Detected CF V2 manifest.yml"
                      cf_manifest = readYaml file: "${env.WORKSPACE}/manifest.yml"
                      if (cf_manifest.applications.size() != 1 || cf_manifest.applications[0].size() > 3) {
                        echo "\u001B[31mWARNING: Only 'buildpack', 'health-check-type' and 'health-check-http-endpoint' attributes are supported in CF V2 manifest.yml.\u001B[m"
                      }
                      if (cf_manifest.applications[0].buildpack) {
                        echo "\u001B[32mINFO: Setting application ${gds_app[2]} buildpack to ${cf_manifest.applications[0].buildpack}\u001B[m"
                        env.PAAS_BUILDPACK = cf_manifest.applications[0].buildpack
                      }
                      if (cf_manifest.applications[0]."health-check-type") {
                        echo "\u001B[32mINFO: Setting application ${gds_app[2]} health-check-type to ${cf_manifest.applications[0].'health-check-type'}\u001B[m"
                        env.PAAS_HEALTHCHECK_TYPE = cf_manifest.applications[0]."health-check-type"
                      }
                      if (cf_manifest.applications[0]."health-check-http-endpoint") {
                        echo "\u001B[32mINFO: Setting application ${gds_app[2]} health-check-http-endpoint to ${cf_manifest.applications[0].'health-check-http-endpoint'}\u001B[m"
                        env.PAAS_HEALTHCHECK_ENDPOINT = cf_manifest.applications[0]."health-check-http-endpoint"
                      }
                    }

                    cfignore_exist = fileExists "${env.WORKSPACE}/.cfignore"
                    if (!cfignore_exist) {
                      sh "ln -snf ${env.WORKSPACE}/.gitignore ${env.WORKSPACE}/.cfignore"
                    }

                    CHECKPOINT = "INIT"
                    sh "cf v3-create-app ${gds_app[2]}"

                    space_guid = sh(script: "cf space ${gds_app[1]}  --guid", returnStdout: true).trim()
                    app_guid = sh(script: "cf v3-app ${gds_app[2]} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                    app_routes_json = sh(script: "cf curl '/v2/apps/${app_guid}/route_mappings' | jq '[.resources[].entity.route_guid]' 2>/dev/null || echo '[]'", returnStdout: true).trim()
                    app_routes = readJSON text: app_routes_json
                    app_svc_json = sh(script: "cf curl '/v2/service_instances' | jq '.resources[] | select(.entity.space_guid==\"${space_guid}\").metadata.guid' | xargs -I{} cf curl /v2/service_instances/{}/service_bindings | jq '.resources[].entity | select(.app_guid==\"${app_guid}\") | [.service_instance_guid]' | jq -s add", returnStdout: true).trim()
                    app_user_svc_json = sh(script: "cf curl '/v2/user_provided_service_instances' | jq '.resources[] | select(.entity.space_guid=\"${space_guid}\").metadata.guid' | xargs -I{} cf curl /v2/user_provided_service_instances/{}/service_bindings | jq '.resources[].entity | select(.app_guid==\"${app_guid}\") | [.service_instance_guid]' | jq -s add", returnStdout: true).trim()
                    app_scale_json = sh(script: "cf curl '/v3/apps/${app_guid}/processes' | jq '.resources | del(.[].links)'", returnStdout: true).trim()
                    app_scale = readJSON text: app_scale_json

                    new_app_name = gds_app[2] + "-" + env.Version
                    echo "\u001B[32mINFO: Creating new app ${new_app_name}\u001B[m"
                    sh "cf v3-create-app ${new_app_name}"
                    new_app_guid = sh(script: "cf v3-app ${new_app_name} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                    CHECKPOINT = "APP_CREATED"

                    echo "\u001B[32mINFO: Configuring new app ${new_app_name}\u001B[m"
                    if (env.PAAS_BUILDPACK) {
                      echo "\u001B[32mINFO: Setting buildpack to ${env.PAAS_BUILDPACK}\u001B[m"
                      sh """
                        cf curl '/v3/apps/${new_app_guid}' -X PATCH -d '{"name": "${new_app_name}","lifecycle": {"type":"buildpack","data": {"buildpacks": ["${env.PAAS_BUILDPACK}"]}}}' | jq -C 'del(.links, .relationships)'
                      """
                    }

                    sh "cf v3-set-env ${new_app_name} GIT_COMMIT ${input.bash_escape(env.GIT_COMMIT)}"
                    envars.each { key, value ->
                      sh """
                        set +x
                        cf v3-set-env ${new_app_name} ${input.bash_escape(key)} ${input.bash_escape(value)}
                      """
                    }
                    if (app_svc_json != 'null') {
                      CHECKPOINT = "APP_SERVICE"
                      app_svc = readJSON text: app_svc_json
                      app_svc.each {
                        svc_name = sh(script: "cf curl '/v2/service_instances/${it}' | jq -r '.entity.name'", returnStdout: true).trim()
                        echo "\u001B[32mINFO: Migrating service ${svc_name} to ${new_app_name}\u001B[m"
                        sh """
                          cf curl /v2/service_bindings -X POST -d '{"service_instance_guid": "${it}", "app_guid": "${new_app_guid}"}' | jq -C 'del(.entity.credentials)'
                        """
                      }
                      CHECKPOINT = "APP_SERVICE_COMPLETE"
                    }
                    if (app_user_svc_json != 'null') {
                      CHECKPOINT = "APP_USER_SERVICE"
                      app_user_svc = readJSON text: app_user_svc_json
                      app_user_svc.each {
                        user_svc_name = sh(script: "cf curl '/v2/user_provided_service_instances/${it}' | jq -r '.entity.name'", returnStdout: true).trim()
                        echo "\u001B[32mINFO: Migrating user provided service ${user_svc_name} to ${new_app_name}\u001B[m"
                        sh """
                          cf curl /v2/service_bindings -X POST -d '{"service_instance_guid": "${it}", "app_guid": "${new_app_guid}"}' | jq -C 'del(.entity.credentials)'
                        """
                      }
                      CHECKPOINT = "APP_USER_SERVICE_COMPLETE"
                    }

                    if (env.USE_NEXUS) {
                      echo "\u001B[32mINFO: Downloading artifact ${env.Project}-${env.Version}.${env.JAVA_EXTENSION.toLowerCase()}\u001B[m"
                      withCredentials([usernamePassword(credentialsId: env.NEXUS_CREDENTIAL, passwordVariable: 'nexus_pass', usernameVariable: 'nexus_user')]) {
                        sh "curl -LOfs 'https://${nexus_user}:${nexus_pass}@${env.NEXUS_URL}/repository/${env.NEXUS_PATH}/${env.Version}/${env.Project}-${env.Version}.${env.JAVA_EXTENSION.toLowerCase()}'"
                        env.APP_PATH = "${env.Project}-${env.Version}.${env.JAVA_EXTENSION.toLowerCase()}"
                      }
                    }

                    CHECKPOINT = "APP_STAGE"
                    if (env.APP_PATH) {
                      package_guid = sh(script: "cf v3-create-package ${new_app_name} -p ${env.APP_PATH} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                    } else {
                      package_guid = sh(script: "cf v3-create-package ${new_app_name} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                    }

                    echo "\u001B[32mINFO: Creating app ${new_app_name} release\u001B[m"
                    sh "cf v3-stage ${new_app_name} --package-guid ${package_guid}"
                    release_guid = sh(script: "cf curl '/v3/apps/${new_app_guid}/droplets' | jq -r '.resources[] | select(.links.package.href | test(\"${package_guid}\")==true) | .guid'", returnStdout: true).trim()

                    sh "cf v3-set-droplet ${new_app_name} --droplet-guid ${release_guid}"
                    if (env.PAAS_HEALTHCHECK_TYPE) {
                      switch(env.PAAS_HEALTHCHECK_TYPE) {
                        case "http":
                          if (env.PAAS_HEALTHCHECK_ENDPOINT) {
                            sh "cf v3-set-health-check ${new_app_name} ${env.PAAS_HEALTHCHECK_TYPE} --endpoint ${env.PAAS_HEALTHCHECK_ENDPOINT}"
                          }
                          break
                      }
                    }

                    echo "\u001B[32mINFO: Scale app ${new_app_name}\u001B[m"
                    procfile_exist = fileExists "${env.WORKSPACE}/Procfile"
                    if (procfile_exist) {
                      procfile = readProperties file: "${env.WORKSPACE}/Procfile"
                      procfile.each { proc, cmd ->
                        app_scale.each {
                          if (proc == it.type) {
                            sh """
                              cf curl '/v3/apps/${new_app_guid}/processes/${it.type}/actions/scale' -X POST -d '{"instances": ${it.instances}, "memory_in_mb": ${it.memory_in_mb}, "disk_in_mb": ${it.disk_in_mb}}' | jq -C 'del(.links)'
                            """
                          }
                        }
                      }
                    } else {
                      app_scale.each {
                        sh """
                          cf curl '/v3/apps/${new_app_guid}/processes/${it.type}/actions/scale' -X POST -d '{"instances": ${it.instances}, "memory_in_mb": ${it.memory_in_mb}, "disk_in_mb": ${it.disk_in_mb}}' | jq -C 'del(.links)'
                        """
                      }
                    }

                    echo "\u001B[32mINFO: Start app ${new_app_name}\u001B[m"
                    sh "cf v3-start ${new_app_name}"
                    CHECKPOINT = "APP_START"

                    app_ready_wait = 0
                    app_ready = false
                    while (app_ready_wait < 120) {
                      app_state_json = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes/web/stats' | jq -r '.resources[] | select(.type=\"web\") | [.state]' | jq -s add", returnStdout: true).trim()
                      app_state = readJSON text: app_state_json
                      app_state.each {
                        if (it == "RUNNING") {
                          echo "\u001B[32mINFO: App ${new_app_name} is ready\u001B[m"
                          app_ready = true
                          app_ready_wait = 120
                        } else {
                          echo "\u001B[32mINFO: App ${new_app_name} not ready, wait for 10 seconds...\u001B[m"
                          app_ready_wait = app_ready_wait + 10
                          sleep 10
                        }
                      }
                    }

                    if (app_ready) {
                      echo "\u001B[32mINFO: Switching app routes\u001B[m"
                      app_routes.each {
                        CHECKPOINT = "APP_ROUTES"
                        sh """
                          cf curl '/v2/routes/${it}/apps/${new_app_guid}' -X PUT | jq -C '.'
                          cf curl '/v2/routes/${it}/apps/${app_guid}' -X DELETE
                        """
                        CHECKPOINT = "APP_ROUTES_COMPLETE"
                      }
                      echo "\u001B[32mINFO: Cleanup old app\u001B[m"
                      sh """
                        cf v3-delete -f ${gds_app[2]}
                        cf rename ${new_app_name} ${gds_app[2]}
                      """
                    } else {
                      CHECKPOINT = "APP_FAIL"
                      error "App failed to start."
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
                    withCredentials([string(credentialsId: env.OC_TOKEN_ID, variable: 'OC_TOKEN')]) {
                      oc_app = env.PAAS_APP.split("/")
                      sh """
                        oc login https://dashboard.${oc_app[0]} --insecure-skip-tls-verify=true --token=${OC_TOKEN}
                        oc project ${oc_app[1]}
                      """

                      withCredentials([sshUserPrivateKey(credentialsId: env.SCM_CREDENTIAL, keyFileVariable: 'GIT_SSH_KEY', passphraseVariable: '', usernameVariable: '')]) {
                        SSH_KEY = readFile GIT_SSH_KEY
                      }

                      unstash "oc-pipeline"
                      SSH_KEY_ENCODED = sh(script: "set +x && echo '${SSH_KEY}' | base64 -w 0", returnStdout: true).trim()
                      sh """
                        set +x
                        oc process -f oc-pipeline.yml \
                          --param APP_ID=${oc_app[2]} \
                          --param NAMESPACE=${oc_app[1]} \
                          --param SCM=${env.SCM} \
                          --param DOMAIN=apps.${oc_app[0]} \
                          --param GIT_SSH_KEY=${SSH_KEY_ENCODED} \
                          | oc apply -f -
                      """
                      sh "oc secrets add serviceaccount/builder secrets/${oc_app[2]}"

                      envars.each { key, value ->
                        sh """
                          set +x
                          oc set env dc/${oc_app[2]} ${input.bash_escape(key)}=${input.bash_escape(value)}
                        """
                      }

                      sh "oc start-build ${oc_app[2]} --commit=${env.Version} --follow"
                    }
                  break

                  case "heroku":
                  break

                  default:
                    error "Not Supported."
                  break
                }
              }
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
          ansiColor('xterm') {
            deployer.inside {
              switch(env.PAAS_TYPE) {
                case "gds":
                  echo "\u001B[31mWARNING: Rollback app\u001B[m"
                  withCredentials([usernamePassword(credentialsId: env.GDS_PAAS_CREDENTIAL, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                    sh """
                      cf login -a ${env.GDS_PAAS} -u ${gds_user} -p ${gds_pass} -o ${gds_app[0]} -s ${gds_app[1]}
                      cf target -o ${gds_app[0]} -s ${gds_app[1]}
                    """
                  }
                  sh "cf logs ${new_app_name} --recent || true"
                  switch(CHECKPOINT) {
                    case "APP_ROUTES":
                      app_routes.each {
                        sh "cf curl '/v2/routes/${it}/apps/${app_guid}' -X PUT | jq -C '.' || true"
                      }
                    case String:
                      sh "cf v3-delete -f ${new_app_name} || true"
                    break
                  }
                break
              }
              emailext body: '${DEFAULT_CONTENT}', recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider'], [$class: 'UpstreamComitterRecipientProvider']], subject: "${currentBuild.result}: ${env.Project} ${env.Environment}", to: '${DEFAULT_RECIPIENTS}'
              slackSend message: "Failure: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.Project} ${env.Environment} (<${env.BUILD_URL}|Open>)", color: 'danger'
            }
          }
        }
      }
    }

    success {
      script {
        timestamps {
          ansiColor('xterm') {
            slackSend message: "Success: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.Project} ${env.Environment} (<${env.BUILD_URL}|Open>)", color: 'good'
          }
        }
      }
    }

    always {
      script {
        timestamps {
          deleteDir()
        }
      }
    }
  }

}
