def main() {
  gds_app = config.PAAS_APP.split("/")
  sh "cf target -o ${gds_app[0]} -s ${gds_app[1]}"
  cf_manifest_exist = fileExists "${env.WORKSPACE}/manifest.yml"
  buildpack_json = readJSON text:  """{"buildpacks": []}"""
  if (cf_manifest_exist) {
    cf_manifest = readYaml file: "${env.WORKSPACE}/manifest.yml"
    if (cf_manifest.applications.size() == 1 && cf_manifest.applications[0].size() > 0) {
      echo "${log_warn}CloudFoundry API V2 manifest.yml support is limited."
      cf_manifest.applications[0].each { key, value ->
        switch (key) {
          case 'buildpacks':
            echo "${log_info}Setting application ${gds_app[2]} buildpack(s) to ${value}"
            if (cf_manifest.applications[0].buildpacks[0].size() == 1) {
              buildpack_json.buildpacks[0] = value
            } else {
              cf_manifest.applications[0].buildpacks.eachWithIndex { build, index ->
                buildpack_json.buildpacks[index] = build
              }
            }
            writeJSON file: "${env.WORKSPACE}/.ci/buildpacks.json", json: buildpack_json
            break
          case 'buildpack':
            echo "${log_info}Setting application ${gds_app[2]} buildpack(s) to ${value}"
            if (cf_manifest.applications[0].buildpack[0].size() == 1) {
              buildpack_json.buildpack[0] = value
            } else {
              cf_manifest.applications[0].buildpack.eachWithIndex { build, index ->
                buildpack_json.buildpacks[index] = build
              }
            }
            writeJSON file: "${env.WORKSPACE}/.ci/buildpacks.json", json: buildpack_json
            break
          case 'stack':
            echo "${log_info}Setting application ${gds_app[2]} base image to ${value}"
            buildpack_json['stack'] = value
            writeJSON file: "${env.WORKSPACE}/.ci/buildpacks.json", json: buildpack_json
            break
          case 'health-check-type':
            echo "${log_info}Setting application ${gds_app[2]} health-check-type to ${value}"
            env.PAAS_HEALTHCHECK_TYPE = value
            break
          case 'health-check-http-endpoint':
            echo "${log_info}Setting application ${gds_app[2]} health-check-http-endpoint to ${value}"
            env.PAAS_HEALTHCHECK_ENDPOINT = value
            break
          case 'timeout':
            echo "${log_info}Setting application ${gds_app[2]} timeout to ${value}"
            env.PAAS_TIMEOUT = value
            break
          case 'docker':
            echo "${log_info}Detected Docker deployement ${value['image']}"
            env.DOCKER_DEPLOY_IMAGE = value['image']
            break
          default:
            echo "${log_warn}CloudFoundry API V2 manifest.yml attribute '${key}' is not supported."
            break
        }
      }
    } else {
      echo "${log_warn}Invalid CloudFoundry API V2 manifest.yml ignored."
    }
  }

  sh "cf v3-create-app ${gds_app[2]} || true"
  space_guid = sh(script: "cf space ${gds_app[1]}  --guid", returnStdout: true).trim()
  app_guid = sh(script: "cf app ${gds_app[2]} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
  app_routes_json = sh(script: "cf curl '/v3/apps/${app_guid}/routes' | jq '[.resources[].guid]'", returnStdout: true).trim()
  app_routes = readJSON text: app_routes_json
  app_svc_json = sh(script: "cf curl '/v2/apps/${app_guid}/service_bindings' | jq '[.resources[].entity.service_instance_guid]'", returnStdout: true).trim()
  app_scale_json = sh(script: "cf curl '/v3/apps/${app_guid}/processes' | jq '.resources | del(.[].links)'", returnStdout: true).trim()
  app_scale = readJSON text: app_scale_json
  app_network_policy_json = sh(script: "cf curl '/networking/v1/external/policies?id=${app_guid}' | jq 'del(.total_policies)'", returnStdout: true).trim()
  app_network_policy = readJSON text: app_network_policy_json

  new_app_name = gds_app[2] + "-" + env.Version
  echo "${log_info}Creating new app ${new_app_name}"
  if (env.DOCKER_DEPLOY_IMAGE) {
    sh "cf v3-create-app ${new_app_name} --app-type docker || true"
  } else {
    sh "cf v3-create-app ${new_app_name} || true"
  }
  new_app_guid = sh(script: "cf app ${new_app_name} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()

  echo "${log_info}Configuring new app ${new_app_name}"
  if (buildpack_json.buildpacks.size() > 0) {
    echo "${log_info}Setting buildpack to ${buildpack_json.buildpacks}"
    env.PAAS_BUILDPACK = readFile file: "${env.WORKSPACE}/.ci/buildpacks.json"
    sh """
      cf curl '/v3/apps/${new_app_guid}' -X PATCH -d '{"name": "${new_app_name}","lifecycle": {"type":"buildpack","data": ${env.PAAS_BUILDPACK}}}' | jq -C 'del(.links, .relationships)'
    """
  }

  sh "cf v3-set-env ${new_app_name} GIT_COMMIT '${app_git_commit}'"
  sh "cf v3-set-env ${new_app_name} GIT_BRANCH '${env.Version}'"
  vars_check = readFile file: "${env.WORKSPACE}/.ci/env.json"
  if (vars_check.trim() != '{}') {
    sh "jq '{\"var\": .}' ${env.WORKSPACE}/.ci/env.json > ${env.WORKSPACE}/.ci/cf_envar.json"
    updated_vars = sh(script: "cf curl '/v3/apps/${new_app_guid}/environment_variables' -X PATCH -d @${env.WORKSPACE}/.ci/cf_envar.json | jq -r '.var | keys'", returnStdout: true).trim()
    echo "${log_info}Application environment variables updated: ${updated_vars} "
  }

  if (app_svc_json != 'null') {
    app_svc = readJSON text: app_svc_json
    app_svc.each {
      svc_name = sh(script: "cf curl '/v2/service_instances/${it}' | jq -r '.entity.name'", returnStdout: true).trim()
      echo "${log_info}Migrating service ${svc_name} to ${new_app_name}"
      sh """
        cf curl /v2/service_bindings -X POST -d '{"service_instance_guid": "${it}", "app_guid": "${new_app_guid}"}' | jq -C 'del(.entity.credentials)'
      """
    }
  }

  echo "${log_info}Pre-scale staging app ${new_app_name}"
  proc_build_json = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes' | jq '.resources | del(.[].links)'", returnStdout: true).trim()
  proc_build = readJSON text: proc_build_json
  proc_build.each { build ->
    app_scale.each {
      if (build.type == it.type) {
        sh """
          cf curl '/v3/apps/${new_app_guid}/processes/${it.type}/actions/scale' -X POST -d '{"memory_in_mb": ${it.memory_in_mb}, "disk_in_mb": ${it.disk_in_mb}}' | jq -C 'del(.links)'
        """
      }
    }
  }

  sh "echo .ci\\*/ >> ${env.WORKSPACE}/.cfignore"
  if (config.USE_NEXUS) {
    echo "${log_info}Downloading artifact ${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}"
    withCredentials([usernamePassword(credentialsId: env.NEXUS_CREDENTIAL, passwordVariable: 'nexus_pass', usernameVariable: 'nexus_user')]) {
      sh "curl -LOfs 'https://${nexus_user}:${nexus_pass}@${env.NEXUS_URL}/repository/${config.NEXUS_PATH}/${env.Version}/${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}'"
    }
    config.APP_PATH = "${env.Project}-${env.Version}.${config.JAVA_EXTENSION.toLowerCase()}".toString()
  }

  if (env.DOCKER_DEPLOY_IMAGE) {
    package_guid = sh(script: "cf v3-create-package ${new_app_name} --docker-image ${env.DOCKER_DEPLOY_IMAGE.trim()} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
  } else {
    if (config.APP_PATH) {
      package_guid = sh(script: "cf v3-create-package ${new_app_name} -p ${config.APP_PATH} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
    } else {
      package_guid = sh(script: "cf v3-create-package ${new_app_name} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
    }
  }

  echo "${log_info}Creating app ${new_app_name} release"
  if (env.DOCKER_DEPLOY_IMAGE) {
    sh """
      cf curl '/v3/builds' -X POST -d '{"package":{"guid":"${package_guid}"}}' | jq -C 'del(.links, .created_by)'
    """
    try {
      timeout(time: 300, unit: 'SECONDS') {
        docker_build = 'false'
        while (docker_build == 'false') {
          docker_build = sh(script: "cf curl '/v3/builds?app_guids=${new_app_guid}' | jq '.resources[] | select(.state == \"STAGED\") and select(.package.guid == \"${package_guid}\")'", returnStdout: true).trim()
          docker_build_fail = sh(script: "cf curl '/v3/builds?app_guids=${new_app_guid}' | jq '.resources[] | select(.state == \"FAILED\") and select(.package.guid == \"${package_guid}\")'", returnStdout: true).trim()
          if (docker_build_fail == 'true') {
            docker_build_err = sh(script: "cf curl '/v3/builds?app_guids=${new_app_guid}' | jq '.resources[] | select(.state == \"FAILED\") | select(.package.guid == \"${package_guid}\").error'", returnStdout: true).trim()
            error docker_build_err
          }
          sleep 10
        }
        release_guid = sh(script: "cf curl '/v3/builds?app_guids=${new_app_guid}' | jq -r '.resources[] | select(.package.guid == \"${package_guid}\") | select(.state == \"STAGED\").droplet.guid'", returnStdout: true).trim()
        sh "cf v3-set-droplet ${new_app_name} --droplet-guid ${release_guid}"
      }
    } catch (err) {
      error "Failed to stage Docker image ${env.DOCKER_DEPLOY_IMAGE}."
    }
  } else {
    sh "cf v3-stage ${new_app_name} --package-guid ${package_guid}"
    release_guid = sh(script: "cf curl '/v3/apps/${new_app_guid}/droplets' | jq -r '.resources[] | select(.links.package.href | test(\"${package_guid}\")==true) | .guid'", returnStdout: true).trim()
    sh "cf v3-set-droplet ${new_app_name} --droplet-guid ${release_guid}"
  }

  echo "${log_info}Configuring health check for app ${new_app_name}"
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
        echo "${log_warn}'health-check-http-endpoint' not configured for 'http' health check."
      }
      break
  }

  echo "${log_info}Scale app ${new_app_name}"
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

  if (app_network_policy.policies.size() > 0) {
    echo "${log_info}Update network policy for app ${new_app_name}"
    writeFile file: "${env.WORKSPACE}/.ci/network_policy.json", text: app_network_policy_json
    sh "sed -ie 's/${app_guid}/${new_app_guid}/g' ${env.WORKSPACE}/.ci/network_policy.json"
    new_app_network_policy_json = readFile file: "${env.WORKSPACE}/.ci/network_policy.json"
    sh """
      cf curl '/networking/v1/external/policies' -X POST -d '${new_app_network_policy_json}' | jq -C '.'
    """
  }

  echo "${log_info}Start app ${new_app_name}"
  sh "cf v3-start ${new_app_name}"

  try {
    app_wait_timeout = sh(script: "expr ${env.PAAS_TIMEOUT} \\* 3", returnStdout: true).trim()
    timeout(time: app_wait_timeout.toInteger(), unit: 'SECONDS') {
      app_ready = 'false'
      app_stopped = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes/web' | jq -r 'contains({\"instances\": 0})'", returnStdout: true).trim()
      while (app_ready == 'false' && app_stopped == 'false') {
        app_ready = sh(script: "cf curl '/v3/apps/${new_app_guid}/processes/web/stats' | jq -r '[.resources[] | select(.type=\"web\") | contains({\"state\": \"RUNNING\"})] | all'", returnStdout: true).trim()
        echo "${log_info}App ${new_app_name} not ready, wait for 10 seconds..."
        sleep 10
      }
      echo "${log_info}App ${new_app_name} is ready"
    }
  } catch (err) {
    error "App failed to start."
  }

  echo "${log_info}Switching app routes"
  app_routes.each { route ->
    destinations_json = sh(script: "cf curl '/v3/routes/${route}/destinations' | jq '[.destinations[] | select(.app.guid=\"${app_guid}\")]'", returnStdout: true).trim()
    destinations = readJSON text: destinations_json
    destinations.each { dest ->
      sh """
        cf curl '/v3/routes/${route}/destinations' -X POST -d '{"destinations": [ {"app": {"guid": "${new_app_guid}", "process": {"type": "${dest.app.process.type}"}}} ]}' | jq -C 'del(.links)'
      """
    }
    sleep 5
    destinations.each { dest ->
      sh """
        cf curl '/v3/routes/${route}/destinations/${dest.guid}' -X DELETE
      """
    }
  }
}
