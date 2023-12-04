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
            image: gcr.io/sre-docker-registry/github.com/uktrade/ci-deployer
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
    string(defaultValue: 'rolling', description:'Please choose your deployment strategy, "rolling" or "non-rolling": ', name: 'Strategy')
  }

  stages {

    stage('Init') {
      steps {
        script {
          timestamps {
            log_info = "\033[32mINFO: "
            log_warn = "\033[31mWARNING: "
            log_end = "\033[0m"
            lock = "false"
            error_msg = ""
          }
        }
        container('deployer') {
          script {
            timestamps {
              checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '.ci'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, trackingSubmodules: false, shallow: true], [$class: 'WipeWorkspace'], [$class: 'CloneOption', shallow: true, noTags: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_SCM]]])
              checkout([$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, trackingSubmodules: false, shallow: true], [$class: 'RelativeTargetDirectory', relativeTargetDir: '.ci/config'], [$class: 'CloneOption', shallow: true, noTags: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_CONF_SCM]]])
              sh "${env.WORKSPACE}/.ci/bootstrap.rb list"
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

            if (!params.Strategy) {
              strategy = input(
                id: 'strategy', message: 'Please enter your deployment strategy, rolling or non-rolling: ', parameters: [
                [$class: 'StringParameterDefinition', name: 'Strategy', description: 'Strategy']
              ])
              env.Strategy = strategy
            } else {
              env.Strategy = params.Strategy
            }
            if (env.Strategy != 'rolling' && env.Strategy != 'non-rolling') {
              error 'Invalid Strategy!'
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
                echo "${log_info}Detected Nodejs version ${node_ver.trim()} ${log_end}"
                sh "bash -l -c 'nvm install ${node_ver.trim()} && nvm use ${node_ver.trim()}'"
              }
              if (py_ver_exist) {
                py_ver = readFile "${env.WORKSPACE}/.python-version"
                echo "${log_info}Detected Python version ${py_ver.trim()} ${log_end}"
                sh "bash -l -c 'pyenv install ${py_ver.trim()} && pyenv global ${py_ver.trim()}'"
              }
              if (rb_ver_exist) {
                rb_ver = readFile "${env.WORKSPACE}/.ruby-version"
                echo "${log_info}Detected Ruby version ${rb_ver.trim()} ${log_end}"
                sh "bash -l -c 'rvm install ${rb_ver.trim()} && rvm use ${rb_ver.trim()}'"
              }
              if (java_ver_exist) {
                java_ver = readFile "${env.WORKSPACE}/.java-version"
                echo "${log_info}Detected Java version ${java_ver.trim()} ${log_end}"
                sh "bash -l -c 'jabba install ${java_ver.trim()} && jabba use ${java_ver.trim()}'"
              }
              if (go_ver_exist) {
                go_ver = readFile "${env.WORKSPACE}/.go-version"
                echo "${log_info}Detected Go version ${go_ver.trim()} ${log_end}"
                sh "bash -l -c 'goenv install ${go_ver.trim()} && goenv global ${go_ver.trim()}'"
              }

              echo "${log_info}Running build script ${log_end}"
              if (config.PAAS_RUN) {
                sh """bash -l -c "${config.PAAS_RUN}" """
              }
            }
          }
        }
      }
    }

    stage('Deploy PaaS V3') {
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
              echo "${log_info}Setting PaaS region to ${paas_region.name}. ${log_end}"

              withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                sh "cf api ${paas_region.api}"
                sh 'cf auth $gds_user $gds_pass'
              }

              gds_app = config.PAAS_APP.split("/")
              sh "cf target -o ${gds_app[0]} -s ${gds_app[1]}"
              package_guid = null
              droplet_guid = null
              deploy_guid = null
              cf_log_ts = null

              app_manifest = readJSON text: """{"applications": [{"name": "${gds_app[2]}", "processes": [{"type": "web"}]}]}"""
              app_manifest.applications[0].processes[0].timeout = 60

              cf_manifest_exist = fileExists "${env.WORKSPACE}/manifest.yml"
              buildpack_json = readJSON text:  """{"buildpacks": []}"""
              if (cf_manifest_exist) {
                cf_manifest = readYaml file: "${env.WORKSPACE}/manifest.yml"
                if (cf_manifest.applications.size() == 1 && cf_manifest.applications[0].size() > 0) {
                  echo "${log_warn}CloudFoundry API V3 manifest.yml support is limited. ${log_end}"
                  cf_manifest.applications[0].each { key, value ->
                    switch (key) {
                      case 'buildpacks':
                        echo "${log_info}Setting application ${gds_app[2]} buildpack(s) to ${value} ${log_end}"
                        cf_manifest.applications[0].buildpacks.eachWithIndex { build, index ->
                          buildpack_json.buildpacks[index] = build
                        }
                        writeJSON file: "${env.WORKSPACE}/.ci/buildpacks.json", json: buildpack_json
                        break
                      case 'stack':
                        echo "${log_info}Setting application ${gds_app[2]} base image to ${value} ${log_end}"
                        buildpack_json['stack'] = value
                        writeJSON file: "${env.WORKSPACE}/.ci/buildpacks.json", json: buildpack_json
                        break
                      case 'health-check-type':
                        echo "${log_info}Setting application ${gds_app[2]} health-check-type to ${value} ${log_end}"
                        switch(value) {
                          case "process":
                            app_manifest.applications[0].processes[0]['health-check-type'] = "process"
                            break
                          case "http":
                            if (cf_manifest.applications[0]['health-check-http-endpoint']) {
                              app_manifest.applications[0].processes[0]['health-check-type'] = "http"
                            } else {
                              echo "${log_warn}'health-check-http-endpoint' not configured for 'http' health check. ${log_end}"
                            }
                            break
                          default:
                            app_manifest.applications[0].processes[0]['health-check-type'] = "port"
                            break
                          }
                        break
                      case 'health-check-http-endpoint':
                        echo "${log_info}Setting application ${gds_app[2]} health-check-http-endpoint to ${value} ${log_end}"
                        if (app_manifest.applications[0].processes[0]['health-check-type'] == "http") {
                          app_manifest.applications[0].processes[0]['health-check-http-endpoint'] = value
                        }
                        break
                      case 'timeout':
                        echo "${log_info}Setting application ${gds_app[2]} timeout to ${value} ${log_end}"
                        app_manifest.applications[0].processes[0].timeout = value
                        break
                      case 'docker':
                        echo "${log_info}Detected Docker deployement ${value['image']} ${log_end}"
                        config.DOCKER_DEPLOY_IMAGE = value['image']
                        break
                      case 'memory':
                        echo "${log_info}Setting application ${gds_app[2]} memory to ${value} ${log_end}"
                        app_manifest.applications[0].processes[0].memory = value
                        break
                      case 'disk_quota':
                        echo "${log_info}Setting application ${gds_app[2]} disk to ${value} ${log_end}"
                        app_manifest.applications[0].processes[0].disk_quota = value
                        break
                      default:
                        echo "${log_warn}CloudFoundry API V3 manifest.yml attribute '${key}' is not supported. ${log_end}"
                        break
                    }
                  }
                } else {
                  echo "${log_warn}Invalid CloudFoundry API V3 manifest.yml ignored. ${log_end}"
                }
              }

              echo "${log_info}Creating app ${gds_app[2]} ${log_end}"
              if (config.DOCKER_DEPLOY_IMAGE) {
                sh "cf create-app ${gds_app[2]} --app-type docker || true"
              } else {
                sh "cf create-app ${gds_app[2]} || true"
              }

              app_guid = sh(script: "cf app ${gds_app[2]} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
              app_revision = sh(script:"cf curl '/v3/deployments?app_guids=${app_guid}&status_reasons=DEPLOYED&order_by=-updated_at&per_page=1' | jq -rc '.resources[].revision.guid'", returnStdout: true).trim()

              app_proc_web_json = sh(script: "cf curl '/v3/apps/${app_guid}/processes/web'", returnStdout: true).trim()
              app_proc_web = readJSON text: app_proc_web_json

              echo "${log_info}Configuring app ${gds_app[2]} ${log_end}"
              sh """cf curl '/v3/apps/${app_guid}/features/revisions' -X PATCH -d '{ "enabled": true }' | jq -C '.'"""
              if (buildpack_json.buildpacks.size() > 0) {
                echo "${log_info}Setting buildpack to ${buildpack_json.buildpacks} ${log_end}"
                buildpacks = readFile file: "${env.WORKSPACE}/.ci/buildpacks.json"
                sh """
                  cf curl '/v3/apps/${app_guid}' -X PATCH -d '{"name": "${gds_app[2]}", "lifecycle": {"type": "buildpack", "data": ${buildpacks}}}' | jq -C 'del(.links, .relationships, .metadata)'
                """
              }

              clear_vars = sh(script: """cf curl '/v3/apps/${app_guid}/environment_variables' | jq -rc '.var | map_values(null) | {"var": .}'""", returnStdout: true).trim()
              writeFile file: "${env.WORKSPACE}/.ci/cf_clear_vars.json", text: clear_vars
              sh "cf curl -X PATCH '/v3/apps/${app_guid}/environment_variables' -X PATCH -d @${env.WORKSPACE}/.ci/cf_clear_vars.json | jq -Cc 'del(.links)'"
              sh """jq '{"var": .} * {"var": {"GIT_COMMIT": "${app_git_commit}", "GIT_BRANCH": "${env.Version}"}}' ${env.WORKSPACE}/.ci/env.json > ${env.WORKSPACE}/.ci/cf_envar.json"""
              updated_vars = sh(script: "cf curl '/v3/apps/${app_guid}/environment_variables' -X PATCH -d @${env.WORKSPACE}/.ci/cf_envar.json | jq -r '.var | keys'", returnStdout: true).trim()
              echo "${log_info}Application environment variables updated: ${updated_vars} ${log_end}"

              sh "echo .ci\\*/ >> ${env.WORKSPACE}/.cfignore"
              if (config.DOCKER_DEPLOY_IMAGE) {
                package_guid = sh(script: "cf create-package ${gds_app[2]} --docker-image ${config.DOCKER_DEPLOY_IMAGE.trim()} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
              } else {
                if (config.APP_PATH) {
                  package_guid = sh(script: "cf create-package ${gds_app[2]} -p ${config.APP_PATH} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                } else {
                  package_guid = sh(script: "cf create-package ${gds_app[2]} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
                }
              }

              echo "${log_info}Creating new build for app ${gds_app[2]} ${log_end}"
              cf_log_ts = sh(script: "date +%s%N", returnStdout: true).trim()
              build_json = sh(script: """cf curl '/v3/builds' -X POST -d '{"package": {"guid": "${package_guid}"}}'""", returnStdout: true).trim()
              try {
                build = readJSON text: build_json
                if (build.errors) {
                  error_msg = build.errors[0].detail
                  error error_msg
                }
                build_guid = build.guid
                build_state = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.state'", returnStdout: true).trim()
                while (build_state != "STAGED") {
                  sleep 10
                  build_state = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.state'", returnStdout: true).trim()
                  if (build_state == "FAILED") {
                    error_msg = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.error'", returnStdout: true).trim()
                    error error_msg
                  }
                }
                echo "${log_info}Staging app and tracing logs... ${log_end}"
                sh """
                  cf tail --json -t log --lines 1000 --start-time ${cf_log_ts} ${app_guid} | jq -rC '.batch[] | select(.tags.source_type|(test("(API|CELL|STG)"))).log.payload | @base64d'
                """
              } catch (err) {
                error error_msg
              }

              droplet_guid = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.droplet.guid'", returnStdout: true).trim()

              echo "${log_info}Configuring app ${gds_app[2]} ${log_end}"
              writeYaml file: "manifest.yml", data: app_manifest, overwrite: true
              sh """cf curl '/v3/apps/${app_guid}/actions/apply_manifest' -X POST -d @manifest.yml -H 'Content-type: application/x-yaml' -i"""

              echo "${log_info}Creating new deployement for app ${gds_app[2]} ${log_end}"

              try {
                // Rolling deployments don't have downtime of the web process, but only work if there _is_ a web process
                if (env.Strategy == "rolling") {
                  deploy_json = sh(script: """cf curl '/v3/deployments' -X POST -d '{"droplet": {"guid": "${droplet_guid}"}, "strategy": "rolling", "relationships": {"app": {"data": {"guid": "${app_guid}"}}}}'""", returnStdout: true).trim()
                  deploy = readJSON text: deploy_json
                  if (deploy.errors) {
                    error_msg = deploy.errors[0].detail
                    error error_msg
                  }
                  deploy_guid = deploy.guid
                  timeout(time: app_manifest.applications[0].processes[0].timeout * app_proc_web.instances * 3, unit: 'SECONDS') {
                    error_msg = "App failed to deploy."
                    deploy_state = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.value'", returnStdout: true).trim()
                    while (deploy_state != "FINALIZED") {
                      sleep 10
                      deploy_state = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.value'", returnStdout: true).trim()
                      deploy_status = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.reason'", returnStdout: true).trim()
                      if (deploy_state == "CANCELING" || deploy_status == "CANCELED" || deploy_status == "DEGENERATE") {
                        deploy_err = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.details'", returnStdout: true).trim()
                        error_msg = "${deploy_status}: ${deploy_err}"
                        error error_msg
                      }
                    }
                  }
                // Non-rolling deployments, suitable for apps that don't have a web process
                } else {
                  // Patch application with new droplet
                  sh(script: """cf curl '/v3/apps/${app_guid}/relationships/current_droplet' -X PATCH -d '{"data": {"guid": "${droplet_guid}"}}'""")

                  // Restart the app, so it starts at the new droplet
                  sh(script: """cf curl '/v3/apps/${app_guid}/actions/restart' -X POST""")

                  // Wait until all processes have been created and finished starting
                  timeout(time: 60, unit: 'SECONDS') {
                    while (True) {
                      sleep 5
                      processes_json = sh(script: """cf curl '/v3/apps/${app_guid}/processes' -X GET""", returnStdout: true).trim()
                      processes = readJSON text: processes_json
                      process_states = processes.resources
                        .collect { 
                          process_stats_json = sh(script: """cf curl '/v3/processes/${it.guid}/stats' -X GET""", returnStdout: true).trim()
                          process_stats = readJSON text: process_stats_json
                          process_stats.resources.collect { it.state }
                        }
                        .flatten()

                      // If no processes have yet been created, wait
                      if (process_states.size() == 0) {
                        continue
                      }

                      // If all processes have finished starting...
                      if (process_states.every { it != 'STARTING' }) {
                        // ... but any of them are not running, error
                        if (process_states.any { it.state != 'RUNNING' }) {
                          error "Not all processes running"
                        }
                        // ... and otherwise we're succesful
                        break
                      }
                    }
                  }  
                }
              } catch (err) {
                error error_msg
              }
              echo "${log_info}App ${gds_app[2]} started. ${log_end}"

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
                  sh "cf api ${paas_region.api}"
                  sh 'cf auth $gds_user $gds_pass'
                }
                echo "${log_warn}Rollback app ${log_end}"
                sh "cf target -o ${gds_app[0]} -s ${gds_app[1]}"

                if (deploy_guid != null) {
                  echo "${log_warn}Cancelling application deployment for app ${gds_app[2]} ${log_end}"
                  sh "cf curl '/v3/deployments/${deploy_guid}/actions/cancel' -X POST | jq -C 'del(.links)'"
                  sleep 5
                }
                new_app_revision = sh(script:"cf curl '/v3/apps/${app_guid}/revisions/deployed' | jq -rc '.resources[].guid'", returnStdout: true).trim()
                if (new_app_revision != app_revision && app_revision != '') {
                  echo "${log_warn}Rollback app ${gds_app[2]} to previous revision ${app_revision}. ${log_end}"
                  sh """cf curl '/v3/deployments' -X POST -d '{"revision": {"guid": "${app_revision}"}, "strategy": "rolling", "relationships": {"app": {"data": {"guid": "${app_guid}"}}}}' | jq -C 'del(.links)'"""
                }
                if (droplet_guid != null) {
                  echo "${log_warn}Remove droplet for app ${gds_app[2]} ${log_end}"
                  sh "cf curl '/v3/droplets/${droplet_guid}' -X DELETE"
                }
                if (package_guid != null) {
                  echo "${log_warn}Remove package for app ${gds_app[2]} ${log_end}"
                  sh "cf curl '/v3/packages/${package_guid}' -X DELETE"
                }
                sh "cf logs ${gds_app[2]} --recent | tail -n 200 || true"
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
