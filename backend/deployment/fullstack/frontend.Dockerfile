FROM nginx:1.28-alpine
COPY --chown=nginx:nginx nginx.conf /etc/nginx/nginx.conf
COPY --chown=nginx:nginx artifact/dist/ /usr/share/nginx/html/
COPY --chown=nginx:nginx deployment.json /usr/share/nginx/html/__deployment.json
USER nginx
EXPOSE 8080
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
