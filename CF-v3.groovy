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

  echo "${log_info}Creating app ${gds_app[2]}"
  if (env.DOCKER_DEPLOY_IMAGE) {
    sh "cf v3-create-app ${gds_app[2]} --app-type docker || true"
  } else {
    sh "cf v3-create-app ${gds_app[2]} || true"
  }

  space_guid = sh(script: "cf space ${gds_app[1]}  --guid", returnStdout: true).trim()
  app_guid = sh(script: "cf app ${gds_app[2]} --guid | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()

  echo "${log_info}Configuring app ${gds_app[2]}"
  if (buildpack_json.buildpacks.size() > 0) {
    echo "${log_info}Setting buildpack to ${buildpack_json.buildpacks}"
    env.PAAS_BUILDPACK = readFile file: "${env.WORKSPACE}/.ci/buildpacks.json"
    sh """
      cf curl '/v3/apps/${app_guid}' -X PATCH -d '{"name": "${gds_app[2]}","lifecycle": {"type":"buildpack","data": ${env.PAAS_BUILDPACK}}}' | jq -C 'del(.links, .relationships)'
    """
  }

  prev_vars = sh(script: "cf curl '/v3/apps/${app_guid}/environment_variables' | jq -rc '.var | map_values(null)'", returnStdout: true).trim()
  sh """
    cf curl -X PATCH '/v3/apps/${app_guid}/environment_variables' -X PATCH -d '{"var": ${prev_vars}}' | jq -C 'del(.links)'
  """
  sh "cf v3-set-env ${gds_app[2]} GIT_COMMIT '${app_git_commit}'"
  sh "cf v3-set-env ${gds_app[2]} GIT_BRANCH '${env.Version}'"
  vars_check = readFile file: "${env.WORKSPACE}/.ci/env.json"
  if (vars_check.trim() != '{}') {
    sh "jq '{\"var\": .}' ${env.WORKSPACE}/.ci/env.json > ${env.WORKSPACE}/.ci/cf_envar.json"
    updated_vars = sh(script: "cf curl '/v3/apps/${app_guid}/environment_variables' -X PATCH -d @${env.WORKSPACE}/.ci/cf_envar.json | jq -r '.var | keys'", returnStdout: true).trim()
    echo "${log_info}Application environment variables updated: ${updated_vars} "
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
    package_guid = sh(script: "cf v3-create-package ${gds_app[2]} --docker-image ${env.DOCKER_DEPLOY_IMAGE.trim()} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
  } else {
    if (config.APP_PATH) {
      package_guid = sh(script: "cf v3-create-package ${gds_app[2]} -p ${config.APP_PATH} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
    } else {
      package_guid = sh(script: "cf v3-create-package ${gds_app[2]} | perl -lne 'print \$& if /(\\{{0,1}([0-9a-fA-F]){8}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){4}-([0-9a-fA-F]){12}\\}{0,1})/'", returnStdout: true).trim()
    }
  }

  echo "${log_info}Creating new build for app ${gds_app[2]}"
  build_guid = sh(script: "cf curl '/v3/builds' -X POST -d '{\"package\": {\"guid\": \"${package_guid}\"}}' | jq -rc '.guid'", returnStdout: true).trim()
  build_state = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.state'", returnStdout: true).trim()
  while (build_state != "STAGED") {
    sleep 10
    build_state = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.state'", returnStdout: true).trim()
    if (build_state == "FAILED") {
      build_err = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.error'", returnStdout: true).trim()
      error build_err
    }
  }

  droplet_guid = sh(script: "cf curl '/v3/builds/${build_guid}' | jq -rc '.droplet.guid'", returnStdout: true).trim()

  echo "${log_info}Configuring health check for app ${gds_app[2]}"
  if (!env.PAAS_TIMEOUT) {
    env.PAAS_TIMEOUT = 60
  }
  if (!env.PAAS_HEALTHCHECK_TYPE) {
    env.PAAS_HEALTHCHECK_TYPE = "port"
  }
  switch(env.PAAS_HEALTHCHECK_TYPE) {
    case "port":
      sh """
        cf curl '/v3/apps/${app_guid}/processes/web' -X PATCH -d '{"health_check": {"type": "port", "data": {"timeout": ${env.PAAS_TIMEOUT}}}}' | jq -C 'del(.links)'
      """
      break
    case "process":
      sh """
        cf curl '/v3/apps/${app_guid}/processes/web' -X PATCH -d '{"health_check": {"type": "process", "data": {"timeout": ${env.PAAS_TIMEOUT}}}}' | jq -C 'del(.links)'
      """
      break
    case "http":
      if (env.PAAS_HEALTHCHECK_ENDPOINT) {
        sh """
          cf curl '/v3/apps/${app_guid}/processes/web' -X PATCH -d '{"health_check": {"type": "http", "data": {"timeout": ${env.PAAS_TIMEOUT}, "endpoint": "${env.PAAS_HEALTHCHECK_ENDPOINT}"}}}' | jq -C 'del(.links)'
        """
      } else {
        echo "${log_warn}'health-check-http-endpoint' not configured for 'http' health check."
      }
      break
  }

  echo "${log_info}Creating new deployement for app ${gds_app[2]}"
  deploy_guid = sh(script: "cf curl '/v3/deployments' -X POST -d '{\"droplet\":{\"guid\":\"${droplet_guid}\"},\"strategy\":\"rolling\",\"relationships\":{\"app\":{\"data\":{\"guid\":\"${app_guid}\"}}}}' | jq -rc '.guid'", returnStdout: true).trim()
  app_wait_timeout = sh(script: "expr ${env.PAAS_TIMEOUT} \\* 3", returnStdout: true).trim()
  timeout(time: app_wait_timeout.toInteger(), unit: 'SECONDS') {
    deploy_state = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.value'", returnStdout: true).trim()
    while (deploy_state != "FINALIZED") {
      sleep 10
      deploy_state = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.value'", returnStdout: true).trim()
      deploy_status = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.reason'", returnStdout: true).trim()
      if (deploy_state == "CANCELING" || deploy_status == "CANCELED" || deploy_status == "DEGENERATE") {
        deploy_err = sh(script: "cf curl '/v3/deployments/${deploy_guid}' | jq -rc '.status.details'", returnStdout: true).trim()
        error "${deploy_status}: ${deploy_err}"
      }
    }
  }
}
