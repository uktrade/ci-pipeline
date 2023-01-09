//For fetching creds
import jenkins.*
import jenkins.model.* 
import hudson.*
import hudson.model.*
import groovy.json.JsonSlurper

def parameters_string_to_map(String parameter_string){

/*   
 *  This function converts jenkins paramter string into map 
 *  - jenkins paramter string in form [ key: value ]
 *  - it returns map of form [ key : value ]
 *  Paramters:
 *  -  parameter_string
 *  Todo:
 *  - validate parameter_string to be of the form [ key: value ]
 */

    def params_map = parameter_string[1..-2].split(',').collectEntries{ entry ->
            def pair = entry.split(':',limit=2)
            def key_name = pair.first().trim()
            if(pair.size() == 1 ){ [  (key_name) : "" ] }
            else { [ (key_name) : (pair.last().trim()) ] }
        }

    return [ status: true, data: params_map ]
}


def all_parameters_are_set(Map parameters){
/*
    This function simply checkes if we have one or more unset paramter.
    if it finds one, returns status as false with appropriate error message
    if it does not find any emptry variables, it returns status as true and appropriate success message
*/
    def is_any_parameter_unset = false
    def unset_paramters = []
    def message = "All Paramters are valid"

    parameters.each { key , value ->  if( ! value ) {  unset_paramters.add(key) } }

    if( unset_paramters.size() > 0 ){

        is_any_parameter_unset = true

        def prefix  =   'Parameter'
        def keys    =   unset_paramters[0]
        def artical =   'is'
    
        if ( unset_paramters.size() > 1 ){ 
            prefix  =   'Parameters'
            keys    =   unset_paramters.join(",")
            artical =   'are'
        }

        keys = unset_paramters.join(",")

        message = "$prefix $keys $artical not set"
    }

      
    return [ status: is_any_parameter_unset , data: message ]
}


def get_credentials(String account_name){
/*
    this function tries to find the credentials from jekins based on its name
    if it finds them, it sets AWS credentials, if not it send appropriate error message
    
    ToDo: this need to be updated to secreat string instead of plain text
*/
    def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
    );

    def credentials_string 

    for (creds in jenkinsCredentials) {
        if(creds.id == account_name){ 
            credentials_string =  "env AWS_ACCESS_KEY_ID=${creds.accessKey} env AWS_SECRET_ACCESS_KEY=${creds.secretKey} env AWS_DEFAULT_REGION=eu-west-2"   
        }
    }

    if( ! credentials_string ){
        return [ status: false , data: "Missing credentials for $account_name"]
    }
    
    return [ status: true , data: credentials_string ]
}

def validate_deployment(Map parameters){
/*
    This function validates things such as 
    - APP defined in paramter actually exists and has copilot manifest
    - Environment defined in paramter exists and has copilot manifest
    - Service defined in paramter exists and has copilot manifest

    it returns false and appropriate error message if it fails to validate
    it returns true and appropriate success message if it is all valid

    More can be done here , that is validate APP , Environment and , Service in repository as well
*/
    /* get credentials string tp be used with command */

    def credentials_string = get_credentials(parameters.ACCOUNT_NAME)

    if ( ! credentials_string.status ){ return credentials_string }

    /* closure to execute command */
    def exec_command = { 
        command -> 
        def output  = new StringBuilder()
        def error   = new StringBuilder()

        def proc = command.execute(null,parameters.SOURCE_DIR)
        proc.waitForProcessOutput(output,error)

        def exit_status = proc.exitValue()

        if(! error.toString().equals("")) { return [ status: false , data: "[${exit_status.toString()}]: ${error.toString()}" ] }
        return [ status: true , data: output.toString() ]
    }


    def slurper = new JsonSlurper()

    /* 
        get all APPS in account and check if app we have defined in parameters as COPILOT_APP is in here 
    */

    def copilot_apps =  exec_command("${credentials_string.data} copilot app ls")
 

    if(! copilot_apps.status) { return copilot_apps }
    
    copilot_apps = copilot_apps.data.split('\n').collect{it.toLowerCase().trim()}

    if( ! copilot_apps.contains(parameters.COPILOT_APP.toLowerCase())){
        return [ status: false, data: "${parameters.COPILOT_APP} not found in ${parameters.ACCOUNT_NAME} account" ]
    }
    // else { 
    //      return [ status: true , data: "app ${parameters.COPILOT_APP} found in  ${parameters.ACCOUNT_NAME} account" ]
    // }

    /* 
        get all ENVIRONMENTS for in COPILOT_APP account and check if COPILOT_ENV is in here 
    */

    def copilot_app_envs = exec_command("${credentials_string.data} copilot env ls --app ${parameters.COPILOT_APP}  --json")
        
    if ( ! copilot_app_envs.status ) { return copilot_app_envs }

    copilot_app_envs = slurper.parseText(copilot_app_envs.data).environments.name

    if( ! copilot_app_envs.contains(parameters.COPILOT_ENV.toLowerCase())){
        return [ status: false, data: "${parameters.COPILOT_ENV} not found in ${parameters.COPILOT_APP} account" ]
    }
    // else { 
    //      return [ status: true , data: "app ${parameters.COPILOT_ENV} found in  ${parameters.COPILOT_APP} account" ]
    // }

    /* 
        get all Services for in COPILOT_APP in given (COPILOT_ENV) environment  and,check if COPILOT_SVC is in here 
    */

    def copilot_services = exec_command("${credentials_string.data} copilot svc ls --app ${parameters.COPILOT_APP} --json")
        
    if ( ! copilot_services.status ) { return copilot_services }

    copilot_services = slurper.parseText(copilot_services.data).services.name

    if( ! copilot_services.contains(parameters.COPILOT_SVC.toLowerCase())){
        return [ status: false, data: "service ${parameters.COPILOT_SVC} not found in env ${parameters.COPILOT_ENV} for ${parameters.COPILOT_APP}" ]
    }
    // else { 
    //      return [ status: true , data: "app ${parameters.COPILOT_ENV} found in  ${parameters.COPILOT_APP} account" ]
    // }

    return [ status: true, data: "Found ${parameters.COPILOT_APP}/${parameters.COPILOT_ENV}/${parameters.COPILOT_SVC}" ]
}


def deploy_app(Map parameters,print_stream){

    /* get credentials string tp be used with command */

    def credentials_string = get_credentials(parameters.ACCOUNT_NAME)

    if ( ! credentials_string.status ){ return credentials_string }

    /* closure to execute command */
    def exec_command = { 
        command -> 
        def output  = new StringBuilder()
        def error   = new StringBuilder()

        def proc = command.execute(null,parameters.SOURCE_DIR)
        proc.consumeProcessOutput(output,error)
        proc.waitForOrKill( 10 * 60 * 1000)

        def exit_status = proc.exitValue()

        if(! error.toString().equals("")) { return [ status: false , data: "[${exit_status.toString()}]: ${error.toString()}" ] }
        return [ status: true , data: output.toString() ]
    }

    def copilot_deploy = exec_command("${credentials_string.data} copilot deploy --name ${parameters.COPILOT_SVC} --app ${parameters.COPILOT_APP} --env ${parameters.COPILOT_ENV} --force")

    return copilot_deploy
}

this