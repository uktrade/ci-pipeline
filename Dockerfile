FROM ubuntu:16.04

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    apt-get install -y curl wget git apt-transport-https ca-certificates software-properties-common && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y build-essential python3.5 python-pip ruby rubygems bundler ruby-full && \
    curl -Lfs https://raw.githubusercontent.com/creationix/nvm/v0.33.2/install.sh | NVM_DIR=/usr/local/nvm bash && \
    export NVM_DIR="/usr/local/nvm" && . "$NVM_DIR/nvm.sh" && \
    nvm install stable && nvm install lts/* && \
    curl -Lfs https://raw.githubusercontent.com/pyenv/pyenv-installer/master/bin/pyenv-installer | PYENV_ROOT=/usr/local/pyenv bash && \
    echo -e 'export PATH="/usr/local/pyenv/bin:$PATH"\neval "$(pyenv init -)"\neval "$(pyenv virtualenv-init -)"' > /etc/profile.d/pyenv.sh && \
    rm -rf /var/lib/apt/lists/*

RUN curl -Lfs https://github.com/openshift/origin/releases/download/v1.5.1/openshift-origin-client-tools-v1.5.1-7b451fc-linux-64bit.tar.gz | tar -xzf - -C /usr/local/bin --strip 1 --wildcards */oc && \
    pip install --upgrade awscli && \
    wget -qO- https://cli-assets.heroku.com/install-ubuntu.sh | sh && \
    wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb http://packages.cloudfoundry.org/debian stable main" | tee /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y cf-cli && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile* /tmp/

RUN bundle install --gemfile=/tmp/Gemfile
