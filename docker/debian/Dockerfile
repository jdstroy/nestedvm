FROM debian:jessie
MAINTAINER BCG, bgould@users.noreply.github.com

ENV DEBIAN_FRONTEND noninteractive

# RUN echo 'deb http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
#    echo 'deb-src http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
#    apt-get update

RUN apt-get update && apt-get install -y \
      build-essential gcc-4.8 g++-4.8 libgmp-dev libmpc-dev libmpfr-dev \
      git curl openjdk-7-jdk \
      --no-install-recommends && \
      apt-get clean all

RUN cd /usr/local/src && \
    git clone https://github.com/bgould/nestedvm && \
    cd nestedvm && \
    make install CC=gcc-4.8 CXX=g++-4.8 && \
    cd /usr/local/src && rm -rf nestedvm 

WORKDIR /usr/local/src
