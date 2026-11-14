FROM nginx:1.28-alpine
ENV TRUSTED_PROXY_CIDR=127.0.0.1/32
COPY dist /usr/share/nginx/html
RUN chmod -R a=rX /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
