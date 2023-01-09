def functions;
def job_parameters;

pipeline {
    agent any  

    options {
        timeout(time:12)
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5'))
    }

    parameters {
        string(name:'GIT_URL',defaultValue:'https://git.com',description:'Project Source code URL')
        string(name:'GIT_BRANCH',defaultValue:'',description:'Branch/CommitValue to deploy from')
        string(name:'ACCOUNT_NAME',defaultValue:'',description:"name of the AWS Account where it needs to deploy an APP")
        string(name:'COPILOT_APP',defaultValue:'',description:'Copilot Application Name')
        string(name:'COPILOT_ENV',defaultValue:'',description:'Copliot Environment Name')
        string(name:'COPILOT_SVC',defaultValue:'',description:'Copilot Service Name')
    }

    stages{

        stage('Init') {
            steps{
                   script {
                                timestamps {
                                        log_info = "\033[32mINFO: "
                                        log_error = "\033[31mERROR: "
                                        log_end = "\033[0m"
                                        error_message = "Failed to deploy service $env.COPILOT_SVC"
                                }
                }   }
        }

        stage("Load Scripts"){

            steps{  script  {
                                def dev_root = new File('/var/groovy/')
                                def prod_root = new File("${env.WORKSPACE}/groovy")
                                def groovy_root = ""

                                if(dev_root.exists()){ groovy_root = dev_root.toString() }

                                else {
                                    if ( ! prod_root.exists() ){
                                        echo "${log_error} Path ${prod_root.toString()} does not exists"
                                        error error_message
                                    }
                                }
                                echo "${log_info}Loading scripts from ${groovy_root}"

                                functions = load("$groovy_root/functions.groovy")   
                }   }
        }

        stage("Process Paramters"){
            steps{  script  {
                                result = functions.parameters_string_to_map("$params")

                                if( ! result.status ){ 
                                    echo "${log_error}Failed to process paramters${log_end}"
                                    error error_message
                                }

                                job_parameters = result.data
                               
                           
                                job_parameters["SOURCE_DIR"] = new File(env.WORKSPACE,"${env.COPILOT_SVC}_${env.GIT_BRANCH}_${env.BUILD_ID}")
                                echo "${log_info}Processed All Parameters${log_end}"
                }   }
        }

        stage("Validate Paramters"){
            steps{  script {
                                result = functions.all_parameters_are_set(job_parameters)

                                if( result.status ){ 
                                    echo "${log_error}${result.data}${log_end}"
                                    error error_message
                                }

                                echo "${log_info}${result.data}${log_end}"
                }   }
        }

        stage("Clone Repository"){
            steps{
                    checkout(changelog: false, poll: false, scm: [ 
                        $class: 'GitSCM', 
                        branches: [[name: "$params.GIT_BRANCH" ]], 
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [ 
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: job_parameters.SOURCE_DIR.toString() ],
                        ], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[ url: "$params.GIT_URL" ]]
                    ])
            }
        }

        stage("Validate Deployment"){
            steps{   script {
                                result = functions.validate_deployment(job_parameters)

                                if( ! result.status ){ 
                                    echo "${log_error}${result.data}${log_end}"
                                    error error_message
                                }

                                echo "${log_info}${result.data}${log_end}"
                }   }
        }

        stage("Deploy"){
            steps{  script {
                                def print_stream = { line -> echo "$line" }

                                result = functions.deploy_app(job_parameters,print_stream)

                                if( ! result.status ){ 
                                    echo "${log_error}${result.data}${log_end}"
                                    error error_message
                                }

                                echo "${log_info}${result.data}${log_end}"
                }   }
        }

 

    }
}