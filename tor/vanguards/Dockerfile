FROM alpine:3.20

RUN addgroup -S -g 65532 kerosene && \
    adduser -S -D -H -u 65532 -G kerosene kerosene

COPY entrypoint.sh /entrypoint.sh
RUN chmod 755 /entrypoint.sh

ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
