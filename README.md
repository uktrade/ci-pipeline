# DIT Digital CD Pipeline

The pipeline is designed to deploy apps to multiple PaaS platforms using a unified process.


## Prerequisite

* Jenkins 2 LTS
* Consul
* Vault
* Docker

## Config Syntax

Pipeline config for projects are stored in another Git repository: https://github.com/uktrade/ci-pipeline-config

```
---
name: sandbox
namespace: shared
scm: git@github.com:uktrade/cf-sample-app-nodejs.git
environments:
  - environment: dev
    type: gds
    # PaaS specific path where app deployed to
    app: dit-staging/sandbox/cf-nodejs
    vars: []
    secrets: true # True to pull secrets stored in Vault
    run: []
  - environment: s3
    type: s3
    app: ci-pipeline-s3-test
    # Deprecated: public available environment variables for app
    vars:
      - S3_WEBSITE_SRC: public
    secrets: true
    # Pre deploy commands to run, eg. build css, download binary, etc.
    run:
      - "ls -al ${WORKSPACE}"
      - "env | sort"
      - "echo hello\ world"
```
Config files are validated using a [json schema](schema.json).

### Build Environment

The pipeline allows users to run commands to before deploying, for some project specific commands to work it has to provide language specific runtime and libraries, following languages are supported with tools,

* __Python__: using `pyenv` with `.python-version` in your project.
* __Nodejs__: using `nvm` with `.nvmrc` in your project.
* __Ruby__: using `rvm` with `.ruby-version` in your project.
* __JAVA__: using `jabba` with `.jabbarc` in your project.


## Supported PaaS

* GOV.UK PaaS (CloudFoundry)
* OpenShift
* AWS S3
* Heroku (Not implemented)

### GOV.UK PaaS

GOV.UK PaaS is CloudFoundry based PaaS.

`app` attribute in app config represents `org/space/app`.

Since CloudFoundry API V3 has removed support for `manifest.yml`, however the pipeline has inherited some functionalities.


#### Regions

GOV.UK PaaS now offers both Ireland (eu-west-1) and London (eu-west-2) regions, to specify your region,

`manifest.yml`:
```
---
name: sandbox
namespace: shared
scm: git@github.com:uktrade/cf-sample-app-nodejs.git
environments:
  - environment: dev
    type: gds
    # PaaS specific path where app deployed to
    app: dit-staging/sandbox/cf-nodejs
    region: eu-west-2
    vars: []
    secrets: true # True to pull secrets stored in Vault
    run: []
```
__Note__: The default region is eu-west-1 if `region` is not specified.

#### Buildpack

App can specify different buildpack other than PaaS natively installed buildpacks.

##### Multiple Buildpacks

`manifest.yml`:
```
applications:
  - buildpack:
    - https://github.com/cloudfoundry/apt-buildpack.git
    - https://github.com/cloudfoundry/python-buildpack.git
```

#### Health Check

CloudFoundry supports multiple types of health checks,
* port
* process
* http

For apps would like to use blue/green deployments require to use `http` health check and provide a URL. CF will wait for new deployment to become healthy before switching routing from old version of app to it.

```
---
applications:
  - health-check-type: http
  - health-check-http-endpoint: /health
```

For more info: https://docs.cloudfoundry.org/devguide/deploy-apps/manifest.html


### OpenShift

OpenShift deployments uses [`oc-pipeline.yml`](oc-pipeline.yml) as template.
For more info: https://docs.openshift.org/latest/dev_guide/templates.html


### AWS S3

`app` attribute in app config represents S3 bucket.

Required envars:
* `AWS_DEFAULT_REGION`
* `AWS_ACCESS_KEY_ID`
* `AWS_SECRET_ACCESS_KEY`

Optional envars:
* `S3_WEBSITE_SRC`: local docroot path
* `S3_WEBSITE_REDIRECT`: S3 redirect rule
