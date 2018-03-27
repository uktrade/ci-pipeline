#!/usr/bin/env ruby

require 'yaml'
require 'json'
require 'json-schema'
require 'rest-client'
require 'deep_merge'
require 'base64'

CONFIG_DIR = "#{ENV['WORKSPACE']}/config"
JSON_SCHEMA = "#{ENV['WORKSPACE']}/schema.json"
CONSUL = ENV['CONSUL']
VAULT_API = ENV['VAULT_API']
VAULT_PREFIX = ENV['VAULT_PREFIX']
VAULT_ROLE_ID = ENV['VAULT_ROLE_ID']
VAULT_SERECT_ID = ENV['VAULT_SERECT_ID']
OPTION_FILE = "#{ENV['WORKSPACE']}/.option"
ENV_FILE = "#{ENV['WORKSPACE']}/.env"
CONF_FILE = "#{ENV['WORKSPACE']}/.config"

def validate(schema, data)
  return JSON::Validator.validate!(schema, data, {:validate_schema => true, :strict => true})
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
    return JSON.parse(resp)['data']
  end
end

def save_json(file, data)
  return File.write(file, JSON.dump(data))
end

def main(args)
  team, project, env = args

  if team.nil? || project.nil? || env.nil? || ENV['VAULT_SERECT_ID'].nil?
    puts "Validating files:"

    config_files = Array.new
    Dir.foreach(CONFIG_DIR) do |file|
      if MIME::Types.type_for(file).to_s =~ /(text|application)\/(x-)?yaml/
        puts "+ config/#{file}"
        begin
          config_files += ["#{CONFIG_DIR}/#{file}"] if validate(JSON_SCHEMA, JSON.dump(YAML.load_file("#{CONFIG_DIR}/#{file}")))
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
        consul_add("#{file_data['namespace']}/#{file_data['name']}/#{env['environment']}", JSON.dump(env))
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
      consul_add("#{file_data['namespace']}/#{file_data['name']}/_", JSON.dump(path_data))
      option_data.deep_merge!({ file_data['namespace'] => { file_data['name'] => env_data }})
    }
    save_json(OPTION_FILE, option_data)

  else

    puts "Saving environment variables."

    data = consul_get("#{team}/#{project}/#{env}")
    run = String.new
    data['run'].each_with_index { |cmd, index|
      (index + 1) < data['run'].length ? run += "#{cmd} && " : run += cmd
    } unless data['run'].empty?
    file_content = Hash.new
    data['vars'].each { |var| file_content.deep_merge!(var) } unless data['vars'].empty?
    if data['secrets']
      secrets = vault_get("#{team}/#{project}/#{env}")
      unless secrets.empty?
        file_content.deep_merge!(secrets)
        file_content.each { |key, value| file_content.deep_merge!({key => value.to_s}) unless value.kind_of? String }
      end
    end

    conf_content = {
      'SCM' => consul_get("#{team}/#{project}/_")['scm'],
      'PAAS_TYPE' => data['type'],
      'PAAS_APP' => data['app'],
      'PAAS_ENVIRONMENT' => data['environment'],
      'PAAS_RUN' => data['run'].empty? ? nil : run
    }

    save_json(CONF_FILE, conf_content)
    save_json(ENV_FILE, file_content)
  end

end

main(ARGV)
