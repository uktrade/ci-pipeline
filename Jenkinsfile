node('docker.ci.uktrade.io') {
  def options_json
  def envars
  def builder = docker.image('ruby:latest')
  builder.pull()
  builder.inside {
    stage('checkout') {
      git 'https://github.com/uktrade/ci-pipeline.git'
      sh 'bundle install'
    }
    stage('init') {
      sh "${env.WORKSPACE}/bootstrap.rb"
      options_json = readJSON file: "${env.WORKSPACE}/option.json"
    }
  }
  stage('input') {
    script {
      options = load "${env.WORKSPACE}/input.groovy"
      team = input(
        id: 'team', message: 'Please choose your team: ', parameters: [
        [$class: 'ChoiceParameterDefinition', name: 'Team', description: 'Team', choices: options.get_team(options_json)]
        ])
      project = input(
        id: 'project', message: 'Please choose your project: ', parameters: [
        [$class: 'ChoiceParameterDefinition', name: 'Project', description: 'Project', choices: options.get_project(options_json,team)]
      ])
      environment = input(
        id: 'environment', message: 'Please choose your environment: ', parameters: [
        [$class: 'ChoiceParameterDefinition', name: 'Environment', description: 'Environment', choices: options.get_env(options_json, team, project)]
      ])
      git_commit = input(
        id: 'git_commit', message: 'Please enter your git branch/tag/commit: ', parameters: [
        [$class: 'StringParameterDefinition', name: 'Git Commit', description: 'GitCommit', defaultValue: 'master']
      ])
    }
  }
  stage('setup') {
    builder.inside {
      sh "${env.WORKSPACE}/bootstrap.rb ${env.team} ${env.project} ${env.environment}"
      envars = readProperties file: "${env.WORKSPACE}/env"
    }
  }
  stage('load') {
    script {
      envars.each {
        var = it.split("=")
        var.each { k,v ->
          env."${k}" = v
        }
      }
    }
  }
  stage('deploy') {
    def deployer = docker.image('ukti/deployer:latest')
    deployer.pull()
    deployer.inside {

    }
  }
}
