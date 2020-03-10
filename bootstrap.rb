#!/usr/bin/env ruby

require 'yaml'
require 'json'
require 'json-schema'
require 'rest-client'
require 'deep_merge'
require 'base64'

CONFIG_DIR = "#{ENV['WORKSPACE']}/.ci/config"
JSON_SCHEMA = "#{ENV['WORKSPACE']}/.ci/schema.json"
CONSUL = ENV['CONSUL']
VAULT_API = ENV['VAULT_API']
VAULT_PREFIX = ENV['VAULT_PREFIX']
VAULT_ROLE_ID = ENV['VAULT_ROLE_ID']
VAULT_SERECT_ID = ENV['VAULT_SERECT_ID']
OPTION_FILE = "#{ENV['WORKSPACE']}/.ci/.option.json"
ENV_FILE = "#{ENV['WORKSPACE']}/.ci/env.json"
CONF_FILE = "#{ENV['WORKSPACE']}/.ci/config.json"

def validate(schema, data)
  return JSON::Validator.validate!(schema, data, {:validate_schema => true, :strict => false})
end

def consul_add(path, data = nil)
  return RestClient.put("#{CONSUL}/#{path}", data)
end

def consul_delete(path)
  return RestClient.delete("#{CONSUL}/#{path}")
end

def consul_get(path)
  begin
    resp = RestClient.get("#{CONSUL}/#{path}")
  rescue RestClient::ExceptionWithResponse => e
    return e.http_code
  else
    return JSON.parse(Base64.decode64(JSON.parse(resp).pop['Value']))
  end
end

def vault_get(path)
  begin
    login = {'role_id' => VAULT_ROLE_ID, 'secret_id' => VAULT_SERECT_ID}
    token = JSON.parse(RestClient.post("#{VAULT_API}/auth/approle/login", login.to_json))['auth']['client_token']
    resp = RestClient.get("#{VAULT_API}/#{VAULT_PREFIX}/#{path}", headers = {'X-Vault-Token': token})
  rescue RestClient::ExceptionWithResponse => e
    return e.http_code
  else
    return JSON.parse(resp)['data']['data']
  end
end

def save_json(file, data)
  return File.write(file, JSON.pretty_generate(data))
end

def main(args)
  ops, params = args
  case ops

  when "update"
    puts "Validating config files:"
    config_files = Array.new
    Dir.foreach(CONFIG_DIR) do |file|
      if MIME::Types.type_for(file).to_s =~ /(text|application)\/(x-)?yaml/
        puts "  > #{CONFIG_DIR.gsub(/#{ENV['PWD']}/, '')}/#{file}"
        begin
          config_files += ["#{CONFIG_DIR}/#{file}"] if validate(JSON_SCHEMA, JSON.pretty_generate(YAML.load_file("#{CONFIG_DIR}/#{file}")))
        rescue Exception => e
          puts e.to_s
        end
      end
    end
    puts "Updating Consul data."
    option_data = Hash.new
    config_files.each { |file|
      file_data = YAML.load_file(file)
      env_data = Array.new
      file_data['environments'].each { |env|
        existing_env = consul_get("#{file_data['namespace']}/#{file_data['name']}/#{env['environment']}")
        existing_env['lock'].nil? ? env.deep_merge!({'lock' => false}) : env.deep_merge!({'lock' => existing_env['lock']}) if existing_env != 404
        consul_add("#{file_data['namespace']}/#{file_data['name']}/#{env['environment']}", JSON.pretty_generate(env))
        env_data += [ env['environment'] ]
      }

      existing_envs = consul_get("#{file_data['namespace']}/#{file_data['name']}/_")
      existing_envs['environments'].each { |del|
        consul_delete("#{file_data['namespace']}/#{file_data['name']}/#{del}") if not env_data.include?(del)
      } if not existing_envs == 404

      path_data = {
        'name' => file_data['name'],
        'namespace' => file_data['namespace'],
        'scm' => file_data['scm'],
        'environments' => env_data
      }
      consul_add("#{file_data['namespace']}/#{file_data['name']}/_", JSON.pretty_generate(path_data))
      option_data.deep_merge!({ file_data['namespace'] => { file_data['name'] => env_data }})
    }
    consul_add("_", JSON.pretty_generate(option_data))

  when "list"
    puts "Saving project list."
    save_json(OPTION_FILE, consul_get("_"))

  when "get"
    team, project, env = params.split('/')
    puts "Saving environment variables."

    data = consul_get("#{team}/#{project}/#{env}")
    run = String.new
    data['run'].each_with_index { |cmd, index|
      (index + 1) < data['run'].length ? run += "#{cmd} && " : run += cmd
    } unless data['run'].empty?
    file_content = Hash.new
    data['vars'].each { |var| file_content.deep_merge!(var) } unless data['vars'].empty?
    secrets = vault_get("#{team}/data/#{project}/#{env}") if data['secrets']
    file_content.deep_merge!(secrets) unless !defined?(secrets) && secrets.empty?
    file_content.each { |key, value| file_content.deep_merge!({key => value.to_s}) } unless file_content.empty?

    conf_content = {
      'SCM' => consul_get("#{team}/#{project}/_")['scm'],
      'PAAS_TYPE' => data['type'],
      'PAAS_APP' => data['app'],
      'PAAS_ENVIRONMENT' => data['environment']
    }
    conf_content.deep_merge!({'PAAS_REGION' => data['region']}) if data.key?('region')
    conf_content.deep_merge!({'PAAS_RUN' => run}) unless data['run'].empty?
    conf_content.deep_merge!({'USE_NEXUS' => file_content['USE_NEXUS']}) if file_content.key?('USE_NEXUS')
    conf_content.deep_merge!({'NEXUS_PATH' => file_content['NEXUS_PATH']}) if file_content.key?('NEXUS_PATH')
    conf_content.deep_merge!({'APP_PATH' => file_content['APP_PATH']}) if file_content.key?('APP_PATH')
    conf_content.deep_merge!({'JAVA_EXTENSION' => file_content['JAVA_EXTENSION']}) if file_content.key?('JAVA_EXTENSION')

    save_json(CONF_FILE, conf_content)
    save_json(ENV_FILE, file_content)

  when "get-lock"
    puts consul_get(params)['lock']

  when "lock"
    consul_add(params, JSON.pretty_generate(consul_get(params).deep_merge!({'lock' => true})))

  when "unlock"
    consul_add(params, JSON.pretty_generate(consul_get(params).deep_merge!({'lock' => false})))

  else
    abort("Usage: bootstrap.rb [update|list|get|get-lock|lock|unlock] [APP_PATH|Team/Porject/Environment]")
  end

end

main(ARGV)
