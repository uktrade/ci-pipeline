def get_team(json) {
  def namespace = []
  json.each { k,v ->
    namespace += k
  }
  return namespace.join('\n')
}

def get_project(json, team) {
  def projects = []
  json[team].each { k,v ->
    projects += k
  }
  return projects.join('\n')
}

def get_env(json, team, project) {
  envs = []
  json[team][project].each { node ->
    envs += node
  }
  return envs.join('\n')
}

return this;
