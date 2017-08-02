FROM ubuntu:16.04

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    apt-get install -y curl wget git apt-transport-https ca-certificates software-properties-common && \
    rm -rf /var/lib/apt/lists/*

RUN curl -sL https://deb.nodesource.com/setup_8.x | bash - && \
    apt-get update && \
    apt-get install -y python3.5 python-pip ruby bundler nodejs && \
    rm -rf /var/lib/apt/lists/*

RUN wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb http://packages.cloudfoundry.org/debian stable main" | tee /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y cf-cli && \
    rm -rf /var/lib/apt/lists/*

ADD Gemfile* /tmp/

RUN cd /tmp && bundle install
