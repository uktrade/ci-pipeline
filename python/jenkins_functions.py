import json
import os
import shlex, subprocess
import boto3

sample_invalid_parameter_string = "name: 'DEPLOY_ENV', defaultValue: 'staging', description: '', trim:''"
sample_valid_parameter_string = "name: 'DEPLOY_ENV', defaultValue: 'staging', description: 'TestApplication', trim:False"
sample_job_parameters = {
  "ACCOUNT_NAME": "UserA",
  "SOURCE_DIR": "pythonTest",
  "COPILOT_APP": "hello-copilot",
  "COPILOT_ENV": "Dev",
  "COPILOT_SVC": "api",
  "COMMAND":"ls -lrt"
}

# Boto3 session used to get AWS credentials
session = boto3.Session()
credentials = session.get_credentials()


def return_parameters (parameter_string, seperator=',', serialise=True):
    '''
    return_parameters (parameter_string, seperator=',', serialise=True)
    
    Parses a Jenkins Parameter string, extracts and returns as a JSON formatted string.
    
    Optional keyword arguments:
    
    seperator:  String inserted between values, default is a comma ,
    serialise:  When true returns a JSON formatted string, otherwise a raw dictionary of the split values
                is returned 
    '''
    # Extract Key values
    parameters = {keys.strip(): values.strip().lstrip("'").rstrip("'") for keys,values in [elem.split(":") for elem in parameter_string.split(seperator)]}
    
    # Serialise to a JSON formatted string
    if bool(serialise):
        parameters = json.dumps(parameters)
    
    return parameters

def all_parameters_are_set(parameters: dict, show_unset_keys: bool=False):
    '''
    all_parameters_are_set (parameters: dict)
    
    Checks to see if all values in a passed dictionary are not empty.
    If any is found to be empty returns false, otherwise true
    
    Mandatory argument:
    parameters:   Dictionary with Jenkins parameters
    
    Optional keyword arguments:
    showKeys:  will return a message name with the keys that do not have values set
    '''
    
    # Raise an Exception if the variable passed is not a Dictionary
    if (isinstance(parameters, dict) == False):
        raise Exception("Variable is NOT a Dictionary. This method requires a dictionary") 
    
    unset_parameters = []
    message = "All parameters are valid"
    
    is_any_parameter_unset = all(value != '' for value in parameters.values())
    
    if bool(show_unset_keys):
        # Split all key,values        
        for key, value in parameters.items():
            print (key, ':' , value)
            
            if (value == ''):
                unset_parameters.append(key)        
        
        # Change error message based on number of unset keys found
        if (len(unset_parameters) == 1):
            # print("Printing Unset Parameters")
            print(unset_parameters)
            
            prefix = 'Parameter '
            keys = unset_parameters[0]
            article = ' is '
        elif (len(unset_parameters) > 1):
            prefix = 'Parameters '
            article = " are "
            
            keys = ','.join(map(str,unset_parameters))

            message = prefix + keys + article + "not set"
    
    if (bool(show_unset_keys)):
        return is_any_parameter_unset, message
    else:
        return is_any_parameter_unset
    
def exec_command(command: str):
    '''
    exec_command(command: str)
    
    Runs the command str passed as a string using the subprocess method.
    stout and stderr are captured
    If unable to run the command, the error is printed and exception thrown.
    
    arguments:
    command:  command to run in string format
    
    Returns 0 if successful, non-zero is an error
    '''
    
    # Execute command using subprocess
    result = subprocess.run(args = shlex.split(command), capture_output=True)
    
    # Return error if the command does not execute successfully
    # also output result of stderr
    returnCode = result.returncode
    if result.returncode != 0:
        print(errorString)
        raise Exception( f'Invalid result: { result.returncode }' )
    
    return result

def deploy_command(command: str):
    '''
    deploy_command(command: str)
    
    Runs the command str passed as a string using the subprocess method.
    stout and stderr are captured
    If unable to run the command, the error is printed and exception thrown.
    
    arguments:
    command:  command to run in string format
        
    Returns 0 if successful, non-zero is an error
    '''
    timeoutPeriod = 10 * 60 * 1000
    
    # Execute command using subprocess
    # result = subprocess.run(args = shlex.split(command), capture_output=True,timeout=timeoutPeriod)
    result = subprocess.run(args = shlex.split(command), stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeoutPeriod)
    print(result.stdout)
        
    # Return error/stderr if the command does not execute successfully
    returnCode = result.returncode
    if result.returncode != 0:
        raise Exception( f'Invalid result: { result.returncode }' )
    
    return result
    
    
def validate_deployment(parameters: dict ):
    '''
    validate_deployment (parameters: dict)
    
    Validates that the Copilot deployment has been successful
    
    Mandatory argument:
    parameters:   Dictionary with Jenkins parameters   
    '''
    print(parameters)
    
    # Get AWS Credentials
    credentials_string = credentials.access_key + " " + credentials.secret_key + " " + session.region_name
        
    # Execute command
    result = exec_command(parameters["COMMAND"])
    
    # Decode Output stored as Byte strings
    # outputString = result.stdout.decode('UTF-8')
    # errorString = result.stderr.decode('UTF-8')
    
    print ("Result of ", parameters["COMMAND"] , " is ", result.returncode)
    
    copilot_app =  exec_command("copilot app ls")
    
    print ("copilot_app -> ", copilot_app)
    
    copilot_apps = copilot_app.stdout.decode('UTF-8').rstrip("\n").split("\n")
    print ("copilot_apps -> \n",copilot_apps)
    
    copilot_app_env = session.profile_name
    print("Environment:", copilot_app_env)
    
    copilot_services =  exec_command("copilot svc ls --app " + parameters["COPILOT_APP"])
    
    return copilot_services
    
def deploy_app(parameters: dict ):
    '''
    def deploy_app(parameters: dict )
    
    Deploys app using the jenkins parameters.
    It will monitor output during the deployment and display it
    
    Mandatory argument:
    parameters:   Dictionary with Jenkins parameters
    '''
    print("Reached Deployment App")
    
    # Get AWS Credentials
    credentials_string = credentials.access_key + " " + credentials.secret_key + " " + session.region_name
    
    # Execute Deploy command    
    cmd = "copilot deploy --name " + parameters["COPILOT_SVC"] + " --app " + parameters["COPILOT_APP"] + " --env " + parameters["COPILOT_ENV"]+ " --force"
    copilot_deploy = deploy_command(cmd)
    
    return copilot_deploy
        
# Convert Jenkins Parameter string to JSON
parameters = return_parameters(sample_valid_parameter_string)

# Load Json string into dictionary
paramDict = json.loads(parameters)

# Check all parameters are set
value = all_parameters_are_set(paramDict,show_unset_keys=True)
print("\nAre All parameters set value using function: ", value)

value = all_parameters_are_set(paramDict)
print("\nAre All parameters set value using function: ", value)

# Test Deploy App Function
deploy_app(job_parameters)

# Test validate_deployment Function
validate_deployment(job_parameters)
