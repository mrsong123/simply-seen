FROM nginx:1.28-alpine
COPY dist /usr/share/nginx/html
RUN chmod -R a=rX /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
