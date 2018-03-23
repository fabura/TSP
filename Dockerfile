FROM registry.itclover.ru/clover/streammachine:core
MAINTAINER Clover DevOps <devops@itclover.ru>

ADD ./ /code
ADD ./docker-app/docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh

WORKDIR /code

RUN env JAVA_OPTS="-Xms2G -Xmx8G" JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF8' sbt "http/compile"

ENTRYPOINT ["/docker-entrypoint.sh"]
