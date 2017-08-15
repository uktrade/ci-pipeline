FROM ubuntu:16.04

ENV NVM_VER v0.33.2

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    apt-get install -y curl wget git apt-transport-https ca-certificates software-properties-common && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y build-essential python3 python3-pip ruby-full rubygems bundler gettext && \
    rm -rf /var/lib/apt/lists/*

RUN curl -Lfs https://github.com/openshift/origin/releases/download/v1.5.1/openshift-origin-client-tools-v1.5.1-7b451fc-linux-64bit.tar.gz | tar -xzf - -C /usr/local/bin --strip 1 --wildcards */oc && \
    pip3 install --upgrade awscli virtualenv && \
    wget -qO- https://cli-assets.heroku.com/install-ubuntu.sh | sh && \
    wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb http://packages.cloudfoundry.org/debian stable main" | tee /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y cf-cli && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile* /tmp/
RUN bundle install --gemfile=/tmp/Gemfile

RUN groupadd -g 1000 ubuntu && \
    useradd -u 1000 -g 1000 -m -s /bin/bash ubuntu

USER ubuntu:ubuntu

RUN curl -Lfs https://raw.githubusercontent.com/pyenv/pyenv-installer/master/bin/pyenv-installer | bash && \
    curl -Lfs https://raw.githubusercontent.com/creationix/nvm/$NVM_VER/install.sh | bash

RUN echo 'export PATH="$HOME/.pyenv/bin:$PATH"\neval "$(pyenv init -)"\neval "$(pyenv virtualenv-init -)"' >> ~/.bashrc && \
    echo 'export NVM_DIR="$HOME/.nvm"\n[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"' >> ~/.bashrc
