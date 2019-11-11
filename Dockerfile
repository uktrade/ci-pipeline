FROM ubuntu:18.04

ENV CF_CLI_VER 6.47.2
ENV NVM_VER 0.35.1
ENV JABBA_VER 0.11.2
ENV RVM_VER 1.29.9
ENV CF_CONDUIT_VER 0.0.8

ENV DEBIAN_FRONTEND noninteractive
RUN groupadd -g 1000 ubuntu && \
    useradd -u 1000 -g 1000 -m -s /bin/bash ubuntu

RUN echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/02apt-speedup && \
    apt-get update && \
    apt-get install -y curl wget git apt-transport-https ca-certificates gnupg2 software-properties-common build-essential libpq-dev libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev llvm libncurses5-dev libncursesw5-dev xz-utils tk-dev postgresql-client && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y python3 python3-pip ruby-full rubygems bundler gettext jq && \
    rm -rf /var/lib/apt/lists/*

RUN curl -Lfs https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add - && \
    echo "deb https://packages.cloudfoundry.org/debian stable main" > /etc/apt/sources.list.d/cloudfoundry-cli.list && \
    apt-get update && \
    apt-get install -y --allow-unauthenticated cf-cli=$CF_CLI_VER && \
    rm -rf /var/lib/apt/lists/*

RUN pip3 install --upgrade awscli virtualenv pip
RUN curl -Lfs https://cli-assets.heroku.com/install-ubuntu.sh | bash

COPY Gemfile* /tmp/
RUN gem install bundler && \
    bundle check || bundle install --gemfile=/tmp/Gemfile

USER ubuntu:ubuntu
ENV HOME /home/ubuntu

RUN cf install-plugin -f https://github.com/alphagov/paas-cf-conduit/releases/download/v$CF_CONDUIT_VER/cf-conduit.linux.amd64
RUN curl -Lfs https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash
RUN curl -Lfs https://github.com/creationix/nvm/raw/v$NVM_VER/install.sh | bash
RUN curl -Lfs https://rvm.io/mpapis.asc | gpg2 --import - && \
    curl -Lfs https://rvm.io/pkuczynski.asc | gpg2 --import - && \
    curl -Lfs https://get.rvm.io | bash -s -- --autolibs=disable --version $RVM_VER
RUN curl -Lfs https://github.com/shyiko/jabba/raw/$JABBA_VER/install.sh | bash
RUN git clone https://github.com/syndbg/goenv.git ~/.goenv

RUN echo 'export PATH="$HOME/.pyenv/bin:$PATH"' >> $HOME/.profile && \
    echo 'eval "$(pyenv init -)"\neval "$(pyenv virtualenv-init -)"' >> $HOME/.profile && \
    echo 'export NVM_DIR="$HOME/.nvm"\n[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"' >> $HOME/.profile && \
    echo 'export GOENV_ROOT="$HOME/.goenv"' >> $HOME/.profile && \
    echo 'export PATH="$GOENV_ROOT/bin:$PATH"' >> $HOME/.profile && \
    echo 'eval "$(goenv init -)"' >> $HOME/.profile && \
    echo 'export PATH="$GOROOT/bin:$PATH"' >> $HOME/.profile && \
    echo 'export PATH="$GOPATH/bin:$PATH"' >> $HOME/.profile

ENTRYPOINT ["bash", "-c"]
