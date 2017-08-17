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

def validate_team(json, team) {
  def namespace = []
  json.each { k,v ->
    namespace += k
  }
  return namespace.contains(team)
}

def validate_project(json, team, project) {
  def projects = []
  json[team].each { k,v ->
    projects += k
  }
  return projects.contains(project)
}

def validate_env(json, team, project, env) {
  envs = []
  json[team][project].each { node ->
    envs += node
  }
  return envs.contains(env)
}

def bash_escape(string) {
  return string.replaceAll(/([\!\"\#\$\&\'\(\)\*\;\<\>\?\[\]\^\\\`\{\}\~\ ])/, '\\\\$1')
}

return this;
