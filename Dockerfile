FROM ubuntu:16.04

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    apt-get install -y curl wget git apt-transport-https ca-certificates software-properties-common && \
    rm -rf /var/lib/apt/lists/*

RUN curl -sL https://deb.nodesource.com/setup_6.x | bash - && \
    apt-get update && \
    apt-get install -y build-essential python3.5 python-pip ruby rubygems bundler ruby-full nodejs && \
    rm -rf /var/lib/apt/lists/*

RUN curl -Lfs -o - https://github.com/openshift/origin/releases/download/v1.5.1/openshift-origin-client-tools-v1.5.1-7b451fc-linux-64bit.tar.gz | tar -xzf - -C /usr/local/bin --strip 1 --wildcards */oc && \
    pip install --upgrade awscli && \
    wget -qO- https://cli-assets.heroku.com/install-ubuntu.sh | sh && \
    wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb http://packages.cloudfoundry.org/debian stable main" | tee /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y cf-cli && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile* /tmp/

RUN cd /tmp && bundle install
