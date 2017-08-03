#!/usr/bin/env ruby

require 'yaml'
require 'json'
require 'json-schema'
require 'rest-client'
require 'deep_merge'
require "base64"

CONFIG_DIR = "#{ENV['WORKSPACE']}/config"
JSON_SCHEMA = "#{ENV['WORKSPACE']}/schema.json"
CONSUL = ENV['CONSUL']
VAULT = ENV['VAULT']
VAULT_TOKEN = ENV['VAULT_TOKEN']
OPTION_FILE = "#{ENV['WORKSPACE']}/option.json"
ENV_FILE = "#{ENV['WORKSPACE']}/env"

def init(path)
  option_data = Hash.new
  Dir.foreach(path) do |file|
    if MIME::Types.type_for(file).to_s =~ /(text|application)\/(x-)?yaml/
      file_data = YAML.load_file("#{CONFIG_DIR}/#{file}")
      if JSON::Validator.validate!(JSON_SCHEMA, JSON.dump(file_data), {:validate_schema => true, :strict => true})
        consul_add("#{file_data['namespace']}/#{file_data['name']}/_", file_data['scm'])
        env_data = []
        file_data['environments'].each { |env|
          consul_add("#{file_data['namespace']}/#{file_data['name']}/#{env['environment']}",  JSON.dump(env))
          env_data += [ env['environment'] ]
        }
      end
      option_data.deep_merge!({ file_data['namespace'] => { file_data['name'] => env_data }})
    end
  end
  save_option(option_data)
end

def consul_add(path, data)
  RestClient.put("#{CONSUL}/#{path}", data)
end

def consul_get(path)
  return RestClient.get("#{CONSUL}/#{path}")
end

def vault_get(path)
  return RestClient.get(
    "#{VAULT}/#{path}",
    headers = {'X-Vault-Token': VAULT_TOKEN}
  )
end

def save_option(data)
  File.write(OPTION_FILE, JSON.dump(data))
end

def save_env(team, project, env)
  scm = Base64.decode64(JSON.parse(consul_get("#{team}/#{project}/_")).pop['Value'])
  resp = JSON.parse(consul_get("#{team}/#{project}/#{env}")).pop

  if resp.include?('Value')
    File.open(ENV_FILE, 'w') { |file| file.truncate(0) }
    data = JSON.parse(Base64.decode64(resp['Value']))

    File.open(ENV_FILE, 'a') { |file|
      file.puts "SCM=#{scm}"
      file.puts "PAAS_TYPE=#{data['type']}"
      file.puts "PAAS_APP=#{data['app']}"
      file.puts "PAAS_ENVIRONMENT=#{data['environment']}"
    }

    run = String.new
    data['run'].each_with_index { |cmd, index|
      if (index + 1) < data['run'].length
        run += "#{cmd} && "
      else
        run += cmd
      end
    }
    File.open(ENV_FILE, 'a') { |file|
      file.puts "PAAS_RUN=#{run}"
    }

    data['vars'].each { |var|
      var.each { |k,v|
        File.open(ENV_FILE, 'a') { |file|
          file.puts "#{k}=#{v}"
        }
      }
    }

    if data['secrets']
      secrets = JSON.parse(vault_get("#{team}/#{project}/#{env}"))['data']
      secrets.each { |k,v|
        unless k.eql?("") || v.eql?("")
          File.open(ENV_FILE, 'a') { |file|
            file.puts "#{k}=#{v}"
          }
        end
      }
    end
  else
    println "Error: unable to parse Consul data."
  end

end

def main(args)
  team, project, env = args
  if team.nil? || project.nil? || env.nil? || ENV['VAULT_TOKEN'].nil?
    init(CONFIG_DIR)
  else
    save_env(team, project, env)
  end
end

main(ARGV)
