pipeline {

  agent {
    node {
      label env.CI_SLAVE
    }
  }

  options {
    timestamps()
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
          validateDeclarativePipeline("${env.WORKSPACE}/Jenkinsfile")
          deployer = docker.image("quay.io/uktrade/deployer:${env.GIT_BRANCH.split("/")[1]}")
          deployer.pull()
          deployer.inside {
            checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_SCM]]])
            checkout([$class: 'GitSCM', branches: [[name: env.GIT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'config']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: env.PIPELINE_CONF_SCM]]])
            sh "${env.WORKSPACE}/bootstrap.rb"
            options_json = readJSON file: "${env.WORKSPACE}/.ci/option.json"
          }
        }
      }
    }

    stage('Input') {
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

    stage('Setup') {
      steps {
        script {
          deployer.inside {
            withCredentials([string(credentialsId: env.VAULT_TOKEN_ID, variable: 'TOKEN')]) {
              env.VAULT_SERECT_ID = TOKEN
              sh "${env.WORKSPACE}/bootstrap.rb ${env.Team} ${env.Project} ${env.Environment}"
            }
            envars = readJSON file: "${env.WORKSPACE}/.ci/env.json"
            config = readJSON file: "${env.WORKSPACE}/.ci/config.json"
            sh "mv oc-pipeline.yml ${env.WORKSPACE}/.ci/"
          }
        }
      }
    }

    stage('Build') {
      steps {
        script {
          ansiColor('xterm') {
            deployer.inside {
              checkout([$class: 'GitSCM', branches: [[name: env.Version]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: env.SCM_CREDENTIAL, url: config.SCM]]])

              app_git_commit = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
              node_ver_exist = fileExists "${env.WORKSPACE}/.nvmrc"
              py_ver_exist = fileExists "${env.WORKSPACE}/.python-version"
              rb_ver_exist = fileExists "${env.WORKSPACE}/.ruby-version"
              java_ver_exist = fileExists "${env.WORKSPACE}/.java-version"
              if (node_ver_exist) {
                node_ver = readFile "${env.WORKSPACE}/.nvmrc"
                echo "\u001B[32mINFO: Detected Nodejs version ${node_ver}\u001B[m"
                sh "bash -l -c 'nvm install ${node_ver.trim()}'"
              }
              if (py_ver_exist) {
                py_ver = readFile "${env.WORKSPACE}/.python-version"
                echo "\u001B[32mINFO: Detected Python version ${py_ver}\u001B[m"
                sh "bash -l -c 'pyenv install ${py_ver.trim()}'"
              }
              if (rb_ver_exist) {
                rb_ver = readFile "${env.WORKSPACE}/.ruby-version"
                echo "\u001B[32mINFO: Detected Ruby version ${rb_ver}\u001B[m"
                sh "bash -l -c 'rvm install ${rb_ver.trim()}'"
              }
              if (java_ver_exist) {
                java_ver = readFile "${env.WORKSPACE}/.java-version"
                echo "\u001B[32mINFO: Detected Java version ${java_ver}\u001B[m"
                sh "bash -l -c 'jabba install ${java_ver.trim()}'"
              }

              if (config.PAAS_RUN) {
                sh "bash -l -c \"${config.PAAS_RUN}\""
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
        script {
          ansiColor('xterm') {
            deployer.inside {
              withCredentials([string(credentialsId: env.GDS_PAAS_CONFIG, variable: 'paas_config_raw')]) {
                paas_config = readJSON text: paas_config_raw
              }
              if (!config.PAAS_REGION) {
                config.PAAS_REGION = paas_config.default
              }
              paas_region = paas_config.regions."${config.PAAS_REGION}"
              echo "\u001B[32mINFO: Setting PaaS region to ${paas_region.name}.\u001B[m"

              withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                sh """
                  cf api ${paas_region.api}
                  cf auth ${gds_user} ${gds_pass}
                """
              }

              gds_app = config.PAAS_APP.split("/")
              sh "cf target -o ${gds_app[0]} -s ${gds_app[1]}"
              cf_manifest_exist = fileExists "${env.WORKSPACE}/manifest.yml"
              if (cf_manifest_exist) {
                echo "INFO: Detected CF V2 manifest.yml"
                cf_manifest = readYaml file: "${env.WORKSPACE}/manifest.yml"
                if (cf_manifest.applications.size() != 1 || cf_manifest.applications[0].size() > 4) {
                  echo "\u001B[31mWARNING: Only 'buildpack', 'health-check-type' and 'health-check-http-endpoint' attributes are supported in CF V2 manifest.yml.\u001B[m"
                }
                if (cf_manifest.applications[0].buildpack) {
                  echo "\u001B[32mINFO: Setting application ${gds_app[2]} buildpack to ${cf_manifest.applications[0].buildpack}\u001B[m"
                  if (cf_manifest.applications[0].buildpack[0].size() == 1) {
                    env.PAAS_BUILDPACK = readJSON text: """{"buildpacks": ["${cf_manifest.applications[0].buildpack}"]}"""
                  } else {
                    env.PAAS_BUILDPACK = readJSON text:  """{"buildpacks": []}"""
                    cf_manifest.applications[0].buildpack.eachWithIndex { build, index ->
                      env.PAAS_BUILDPACK.buildpacks[index] = build
                    }
                  }
                }
                if (cf_manifest.applications[0]."health-check-type") {
                  echo "\u001B[32mINFO: Setting application ${gds_app[2]} health-check-type to ${cf_manifest.applications[0].'health-check-type'}\u001B[m"
                  env.PAAS_HEALTHCHECK_TYPE = cf_manifest.applications[0]."health-check-type"
                }
                if (cf_manifest.applications[0]."health-check-http-endpoint") {
                  echo "\u001B[32mINFO: Setting application ${gds_app[2]} health-check-http-endpoint to ${cf_manifest.applications[0].'health-check-http-endpoint'}\u001B[m"
                  env.PAAS_HEALTHCHECK_ENDPOINT = cf_manifest.applications[0]."health-check-http-endpoint"
                }
                if (cf_manifest.applications[0]."timeout") {
                  echo "\u001B[32mINFO: Setting application ${gds_app[2]} timeout to ${cf_manifest.applications[0].'timeout'}\u001B[m"
                  env.PAAS_TIMEOUT = cf_manifest.applications[0]."timeout"
                }
              }

              cfignore_exist = fileExists "${env.WORKSPACE}/.cfignore"
              if (!cfignore_exist) {
                sh "ln -snf ${env.WORKSPACE}/.gitignore ${env.WORKSPACE}/.cfignore"
              }

              sh "cf v3-create-app ${gds_app[2]}"
              space_guid = sh(script: "cf space ${gds_app[1]}  --guid", returnStdout: true).trim()
              app_guid = sh(script: "cf app ${gds_app[2]} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
              app_routes_json = sh(script: "cf curl '/v2/apps/${app_guid}/route_mappings' | jq '[.resources[].entity.route_guid]' 2>/dev/null || echo '[]'", returnStdout: true).trim()
              app_routes = readJSON text: app_routes_json
              app_svc_json = sh(script: "cf curl '/v2/apps/${app_guid}/service_bindings' | jq '.resources[] | [.entity.service_instance_guid]' | jq -s add", returnStdout: true).trim()
              app_scale_json = sh(script: "cf curl '/v3/apps/${app_guid}/processes' | jq '.resources | del(.[].links)'", returnStdout: true).trim()
              app_scale = readJSON text: app_scale_json
              app_network_policy_json = sh(script: "cf curl /networking/v1/external/policies | jq '.policies | select(.[].source.id==\"${app_guid}\") // select(.[].destination.id==\"${app_guid}\")'", returnStdout: true).trim()

              new_app_name = gds_app[2] + "-" + env.Version
              echo "\u001B[32mINFO: Creating new app ${new_app_name}\u001B[m"
              sh "cf v3-create-app ${new_app_name}"
              new_app_guid = sh(script: "cf app ${new_app_name} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()

              echo "\u001B[32mINFO: Configuring new app ${new_app_name}\u001B[m"
              if (env.PAAS_BUILDPACK) {
                echo "\u001B[32mINFO: Setting buildpack to ${env.PAAS_BUILDPACK}\u001B[m"
                sh """
                  cf curl '/v3/apps/${new_app_guid}' -X PATCH -d '{"name": "${new_app_name}","lifecycle": {"type":"buildpack","data": {"buildpacks": ${env.PAAS_BUILDPACK.buildpacks}}}}' | jq -C 'del(.links, .relationships)'
                """
              }

              sh "cf v3-set-env ${new_app_name} GIT_COMMIT ${app_git_commit}"
              vars_check = readFile file: "${env.WORKSPACE}/.ci/env.json"
              if (vars_check.trim() != '{}') {
                sh "jq '{\"var\": .}' ${env.WORKSPACE}/.ci/env.json > ${env.WORKSPACE}/.ci/cf_envar.json"
                updated_vars = sh(script: "cf curl '/v3/apps/${new_app_guid}/environment_variables' -X PATCH -d @${env.WORKSPACE}/.ci/cf_envar.json | jq -r '.var | keys'", returnStdout: true).trim()
                echo "\u001B[32mINFO: Application environment variables updated: ${updated_vars} \u001B[m"
              }

              if (app_svc_json != 'null') {
                app_svc = readJSON text: app_svc_json
                app_svc.each {
                  svc_name = sh(script: "cf curl '/v2/service_instances/${it}' | jq -r '.entity.name'", returnStdout: true).trim()
                  echo "\u001B[32mINFO: Migrating service ${svc_name} to ${new_app_name}\u001B[m"
                  sh """
                    cf curl /v2/service_bindings -X POST -d '{"service_instance_guid": "${it}", "app_guid": "${new_app_guid}"}' | jq -C 'del(.entity.credentials)'
                  """
                }
              }

              if (config.USE_NEXUS) {
                echo "\u001B[32mINFO: Downloading artifact ${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}\u001B[m"
                withCredentials([usernamePassword(credentialsId: env.NEXUS_CREDENTIAL, passwordVariable: 'nexus_pass', usernameVariable: 'nexus_user')]) {
                  sh "curl -LOfs 'https://${nexus_user}:${nexus_pass}@${env.NEXUS_URL}/repository/${config.NEXUS_PATH}/${env.Version}/${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}'"
                }
                config.APP_PATH = "${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}".toString()
              }

              if (config.APP_PATH) {
                package_guid = sh(script: "cf v3-create-package ${new_app_name} -p ${config.APP_PATH} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
              } else {
                package_guid = sh(script: "cf v3-create-package ${new_app_name} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
              }

              echo "\u001B[32mINFO: Creating app ${new_app_name} release\u001B[m"
              sh "cf v3-stage ${new_app_name} --package-guid ${package_guid}"
              release_guid = sh(script: "cf curl '/v3/apps/${new_app_guid}/droplets' | jq -r '.resources[] | select(.links.package.href | test(\"${package_guid}\")==true) | .guid'", returnStdout: true).trim()
              sh "cf v3-set-droplet ${new_app_name} --droplet-guid ${release_guid}"

              echo "\u001B[32mINFO: Configuring health check for app ${new_app_name}\u001B[m"
              if (!env.PAAS_TIMEOUT) {
                env.PAAS_TIMEOUT = 60
              }
              if (!env.PAAS_HEALTHCHECK_TYPE) {
                env.PAAS_HEALTHCHECK_TYPE = "port"
              }
              switch(env.PAAS_HEALTHCHECK_TYPE) {
                case "port":
                  sh """
                    cf curl '/v3/processes/${new_app_guid}' -X PATCH -d '{"health_check": {"type": "port", "data": {"timeout": ${env.PAAS_TIMEOUT}}}}' | jq -C 'del(.links)'
                  """
                  break
                case "process":
                  sh """
                    cf curl '/v3/processes/${new_app_guid}' -X PATCH -d '{"health_check": {"type": "process", "data": {"timeout": ${env.PAAS_TIMEOUT}}}}' | jq -C 'del(.links)'
                  """
                  break
                case "http":
                  if (env.PAAS_HEALTHCHECK_ENDPOINT) {
                    sh """
                      cf curl '/v3/processes/${new_app_guid}' -X PATCH -d '{"health_check": {"type": "http", "data": {"timeout": ${env.PAAS_TIMEOUT}, "endpoint": "${env.PAAS_HEALTHCHECK_ENDPOINT}"}}}' | jq -C 'del(.links)'
                    """
                  } else {
                    echo "\u001B[31mWARNING: 'health-check-http-endpoint' not configured for 'http' health check.\u001B[m"
                  }
                  break
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

              if (app_network_policy_json != '') {
                echo "\u001B[32mINFO: Update network policy for app ${new_app_name}\u001B[m"
                writeFile file: "${env.WORKSPACE}/.ci/network_policy.json", text: app_network_policy_json
                sh "sed -ie 's/${app_guid}/${new_app_guid}/g' ${env.WORKSPACE}/.ci/network_policy.json"
                new_app_network_policy_json = readFile file: "${env.WORKSPACE}/.ci/network_policy.json"
                sh """
                  cf curl '/networking/v1/external/policies' -X POST -d '{"policies": ${new_app_network_policy_json}}'
                """
              }


              echo "\u001B[32mINFO: Start app ${new_app_name}\u001B[m"
              sh "cf v3-start ${new_app_name}"

              try {
                app_wait_timeout = sh(script: "expr ${env.PAAS_TIMEOUT} \\* 3", returnStdout: true).trim()
                timeout(time: app_wait_timeout.toInteger(), unit: 'SECONDS') {
                  app_ready = 'false'
                  app_stopped = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes/web' | jq -r 'contains({\"instances\": 0})'", returnStdout: true).trim()
                  while (app_ready == 'false' && app_stopped == 'false') {
                    app_ready = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes/web/stats' | jq -r '.resources[] | select(.type=\"web\") | [contains({\"state\": \"RUNNING\"})]' | jq -sr 'add | all'", returnStdout: true).trim()
                    echo "\u001B[32mINFO: App ${new_app_name} not ready, wait for 10 seconds...\u001B[m"
                    sleep 10
                  }
                  echo "\u001B[32mINFO: App ${new_app_name} is ready\u001B[m"
                }
              } catch (err) {
                error "App failed to start."
              }

              echo "\u001B[32mINFO: Switching app routes\u001B[m"
              app_routes.each {
                sh """
                  cf curl '/v2/routes/${it}/apps/${new_app_guid}' -X PUT | jq -C '.'
                  cf curl '/v2/routes/${it}/apps/${app_guid}' -X DELETE
                """
              }

            }
          }
        }
      }

      post {
        success {
          script {
            ansiColor('xterm') {
              deployer.inside {
                withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  sh """
                    cf api ${paas_region.api}
                    cf auth ${gds_user} ${gds_pass}
                  """
                }
                echo "\u001B[32mINFO: Cleanup old app\u001B[m"
                sh """
                  cf target -o ${gds_app[0]} -s ${gds_app[1]}
                  cf curl '/v3/apps/${app_guid}' -X PATCH -d '{"name": "${gds_app[2]}-delete"}' | jq -C 'del(.links, .relationships)'
                  cf curl '/v3/apps/${new_app_guid}' -X PATCH -d '{"name": "${gds_app[2]}"}' | jq -C 'del(.links, .relationships)'
                  cf curl '/v3/apps/${app_guid}' -X DELETE
                """
              }
            }
          }
        }

        failure {
          script {
            ansiColor('xterm') {
              deployer.inside {
                withCredentials([usernamePassword(credentialsId: paas_region.credential, passwordVariable: 'gds_pass', usernameVariable: 'gds_user')]) {
                  sh """
                    cf api ${paas_region.api}
                    cf auth ${gds_user} ${gds_pass}
                  """
                }
                echo "\u001B[31mWARNING: Rollback app\u001B[m"
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
        script {
          ansiColor('xterm') {
            deployer.inside {
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

    stage('Deploy OpenShift') {
      when {
        expression {
          config.PAAS_TYPE == 'openshift'
        }
      }
      steps {
        script {
          ansiColor('xterm') {
            deployer.inside {
              withCredentials([string(credentialsId: env.OC_TOKEN_ID, variable: 'OC_TOKEN')]) {
                oc_app = config.PAAS_APP.split("/")
                sh """
                  oc login https://dashboard.${oc_app[0]} --insecure-skip-tls-verify=true --token=${OC_TOKEN}
                  oc project ${oc_app[1]}
                """

                withCredentials([sshUserPrivateKey(credentialsId: env.SCM_CREDENTIAL, keyFileVariable: 'GIT_SSH_KEY', passphraseVariable: '', usernameVariable: '')]) {
                  SSH_KEY = readFile GIT_SSH_KEY
                }

                SSH_KEY_ENCODED = sh(script: "set +x && echo '${SSH_KEY}' | base64 -w 0", returnStdout: true).trim()
                sh """
                  set +x
                  oc process -f ${env.WORKSPACE}/.ci/oc-pipeline.yml \
                    --param APP_ID=${oc_app[2]} \
                    --param NAMESPACE=${oc_app[1]} \
                    --param SCM=${config.SCM} \
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
            }
          }
        }
      }
    }

  }

  post {
    failure {
      script {
        ansiColor('xterm') {
          emailext body: '${DEFAULT_CONTENT}', recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider'], [$class: 'UpstreamComitterRecipientProvider']], subject: "${currentBuild.result}: ${env.Project} ${env.Environment}", to: '${DEFAULT_RECIPIENTS}'
        }
      }
    }

    always {
      script {
        ansiColor('xterm') {
          message_colour_map = readJSON text: '{"SUCCESS": "#36a64f", "FAILURE": "#FF0000", "UNSTABLE": "#FFCC00"}'
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
          slackSend botUser: true, attachments: message_body.toString().trim()
          deleteDir()
        }
      }
    }
  }

}
