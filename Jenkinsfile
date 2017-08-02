node {
  def container = docker.image('ruby:latest')
  container.pull()
  container.inside {
    stage('checkout') {
      git 'https://github.com/uktrade/ci-pipeline.git'
      sh 'bundle install'
    }
  }
}
