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
  return json[team][project].join('\n')
}

return this;
