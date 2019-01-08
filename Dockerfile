FROM ubuntu:18.04

ENV CF_CLI_VER 6.41.0
ENV NVM_VER=v0.34.0
ENV JABBA_VER=0.11.2
ENV RVM_VER=1.29.7

RUN groupadd -g 1000 ubuntu && \
    useradd -u 1000 -g 1000 -m -s /bin/bash ubuntu

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y curl wget git apt-transport-https ca-certificates gnupg2 software-properties-common build-essential libpq-dev libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev postgresql-client && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y python3 python3-pip ruby-full rubygems bundler gettext jq && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --upgrade awscli virtualenv && \
    curl -Lfs https://cli-assets.heroku.com/install-ubuntu.sh | bash && \
    curl -Lfs https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb https://packages.cloudfoundry.org/debian stable main" > /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y --allow-unauthenticated cf-cli=$CF_CLI_VER && \
    apt-add-repository -y ppa:rael-gc/rvm && \
    apt-get install -y rvm="$RVM_VER"-\* && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile* /tmp/
RUN gem install bundler && \
    bundle check || bundle install --gemfile=/tmp/Gemfile

USER ubuntu:ubuntu
ENV HOME /home/ubuntu

RUN curl -Lfs https://github.com/shyiko/jabba/raw/$JABBA_VER/install.sh | bash && \
    curl -Lfs https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash && \
    curl -Lfs https://github.com/creationix/nvm/raw/$NVM_VER/install.sh | bash && \
    echo 'export PATH="$HOME/.pyenv/bin:$PATH:/usr/share/rvm/bin"' >> ~/.bashrc && \
    echo 'eval "$(pyenv init -)"\neval "$(pyenv virtualenv-init -)"' >> ~/.bashrc && \
    echo '[[ -s "/usr/share/rvm/scripts/rvm" ]] && source "/usr/share/rvm/scripts/rvm"' >> ~/.bashrc && \
    cf install-plugin -f conduit

ENTRYPOINT ["bash", "-c"]
